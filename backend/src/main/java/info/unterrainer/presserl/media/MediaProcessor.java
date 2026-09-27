package info.unterrainer.presserl.media;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;

/**
 * The one pipeline every uploaded image passes: sniff the type from the bytes, check the dimensions
 * before decoding, apply the EXIF orientation, convert to sRGB, scale down and re-encode from the
 * pixels. Nothing of the original file (EXIF, GPS, XMP, IPTC, ICC, comments, thumbnails) survives.
 * Declared content type and file name are never looked at.
 */
public class MediaProcessor {

    public static final int MAX_LONG_SIDE = 4096;
    public static final long MAX_PIXELS = 50_000_000L;
    public static final int MAX_SIDE = 20_000;
    static final float JPEG_QUALITY = 0.85f;

    private static final byte[] JPEG_MAGIC = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
    private static final byte[] PNG_MAGIC = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };
    private static final int WEBP_ANIMATION_FLAG = 0x02;
    private static final int HEAD_LENGTH = 21;

    /**
     * Accepted input types with their ImageIO format name.
     */
    public enum InputType {
        JPEG("jpeg"), PNG("png"), WEBP("webp");

        final String format;

        InputType(String format) {
            this.format = format;
        }
    }

    /**
     * The re-encoded image.
     */
    public record Processed(byte[] bytes, String contentType, String extension, int width, int height) {
    }

    /**
     * Processes the uploaded file.
     *
     * @throws MediaException {@code 415} for unsupported types, {@code 400} for oversized or undecodable images
     */
    public Processed process(Path file) {
        InputType type = sniff(head(file));
        BufferedImage decoded = decode(file, type);
        BufferedImage oriented = orient(decoded, orientation(file));
        boolean alpha = hasTransparency(oriented);
        BufferedImage scaled = scale(toSrgb(oriented, alpha), MAX_LONG_SIDE);
        return alpha
                ? new Processed(encode(scaled, "png", null), "image/png", "png", scaled.getWidth(), scaled.getHeight())
                : new Processed(encodeJpeg(scaled), "image/jpeg", "jpg", scaled.getWidth(), scaled.getHeight());
    }

    /**
     * The type from the magic bytes: JPEG, PNG or a still WebP ({@code VP8 }, {@code VP8L}, or
     * {@code VP8X} without the animation flag).
     */
    static InputType sniff(byte[] head) {
        if (startsWith(head, JPEG_MAGIC)) {
            return InputType.JPEG;
        }
        if (startsWith(head, PNG_MAGIC)) {
            return InputType.PNG;
        }
        if (head.length >= 16 && ascii(head, 0, 4).equals("RIFF") && ascii(head, 8, 4).equals("WEBP")) {
            String chunk = ascii(head, 12, 4);
            if (chunk.equals("VP8 ") || chunk.equals("VP8L")) {
                return InputType.WEBP;
            }
            if (chunk.equals("VP8X") && head.length >= HEAD_LENGTH) {
                if ((head[20] & WEBP_ANIMATION_FLAG) != 0) {
                    throw MediaException.unsupported("animated WebP is not supported");
                }
                return InputType.WEBP;
            }
        }
        throw MediaException.unsupported("only JPEG, PNG and WebP images are accepted");
    }

    private static BufferedImage decode(Path file, InputType type) {
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            ImageReader reader = reader(type);
            try {
                List<String> warnings = new ArrayList<>();
                reader.addIIOReadWarningListener((source, warning) -> warnings.add(warning));
                reader.setInput(in, true, true);
                checkDimensions(reader.getWidth(0), reader.getHeight(0));
                BufferedImage image = reader.read(0);
                // readers fill missing data and only warn, e.g. for a truncated JPEG
                if (!warnings.isEmpty()) {
                    throw MediaException.invalid("the image is damaged: " + warnings.getFirst());
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (MediaException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw MediaException.invalid("the image cannot be decoded", e);
        }
    }

    static void checkDimensions(int width, int height) {
        if (width <= 0 || height <= 0 || width > MAX_SIDE || height > MAX_SIDE
                || (long) width * height > MAX_PIXELS) {
            throw MediaException.invalid("image of " + width + " x " + height + " pixels is too large; at most "
                    + MAX_PIXELS / 1_000_000 + " megapixels and " + MAX_SIDE + " pixels per side");
        }
    }

    private static ImageReader reader(InputType type) {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(type.format);
        if (!readers.hasNext()) {
            throw new IllegalStateException("no ImageIO reader for " + type.format);
        }
        return readers.next();
    }

    /**
     * The EXIF orientation 1 to 8; 1 when missing or unreadable.
     */
    static int orientation(Path file) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file.toFile());
            for (ExifIFD0Directory directory : metadata.getDirectoriesOfType(ExifIFD0Directory.class)) {
                if (directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                    int value = directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
                    return value >= 1 && value <= 8 ? value : 1;
                }
            }
        } catch (Exception e) {
            // no readable orientation: the pixels are used as stored
        }
        return 1;
    }

    /**
     * Rotates and flips the pixels so that the image shows as the EXIF orientation describes.
     */
    static BufferedImage orient(BufferedImage image, int orientation) {
        if (orientation == 1) {
            return image;
        }
        int w = image.getWidth();
        int h = image.getHeight();
        boolean swap = orientation >= 5;
        AffineTransform t = new AffineTransform();
        switch (orientation) {
            case 2 -> { t.translate(w, 0); t.scale(-1, 1); }
            case 3 -> { t.translate(w, h); t.rotate(Math.PI); }
            case 4 -> { t.translate(0, h); t.scale(1, -1); }
            case 5 -> { t.rotate(-Math.PI / 2); t.scale(-1, 1); }
            case 6 -> { t.translate(h, 0); t.rotate(Math.PI / 2); }
            case 7 -> { t.scale(-1, 1); t.translate(-h, w); t.rotate(-Math.PI / 2); }
            case 8 -> { t.translate(0, w); t.rotate(-Math.PI / 2); }
            default -> throw new IllegalArgumentException("orientation " + orientation);
        }
        BufferedImage target = new BufferedImage(swap ? h : w, swap ? w : h,
                image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.drawImage(image, t, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    /**
     * Whether the image has an alpha channel with at least one pixel that is not fully opaque.
     */
    static boolean hasTransparency(BufferedImage image) {
        if (!image.getColorModel().hasAlpha()) {
            return false;
        }
        int w = image.getWidth();
        int[] row = new int[w];
        for (int y = 0; y < image.getHeight(); y++) {
            image.getRGB(0, y, w, 1, row, 0, w);
            for (int argb : row) {
                if ((argb >>> 24) != 0xFF) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A copy in sRGB, {@code TYPE_INT_ARGB} with alpha or {@code TYPE_INT_RGB} without; drawing
     * converts from the source color space.
     */
    static BufferedImage toSrgb(BufferedImage image, boolean alpha) {
        int type = alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        if (image.getType() == type) {
            return image;
        }
        BufferedImage target = new BufferedImage(image.getWidth(), image.getHeight(), type);
        Graphics2D g = target.createGraphics();
        try {
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    /**
     * Scales the longer side down to {@code maxLongSide} (never up): halving steps while the image
     * is at least twice the target, then one bilinear step to the exact size.
     */
    static BufferedImage scale(BufferedImage image, int maxLongSide) {
        int w = image.getWidth();
        int h = image.getHeight();
        int longSide = Math.max(w, h);
        if (longSide <= maxLongSide) {
            return image;
        }
        double factor = (double) maxLongSide / longSide;
        int targetW = w >= h ? maxLongSide : Math.max(1, (int) Math.round(w * factor));
        int targetH = w >= h ? Math.max(1, (int) Math.round(h * factor)) : maxLongSide;
        BufferedImage current = image;
        while (current.getWidth() / 2 >= targetW && current.getHeight() / 2 >= targetH) {
            current = resize(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        return resize(current, targetW, targetH);
    }

    private static BufferedImage resize(BufferedImage image, int width, int height) {
        BufferedImage target = new BufferedImage(width, height, image.getType());
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(image, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private static byte[] encodeJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(JPEG_QUALITY);
        param.setProgressiveMode(ImageWriteParam.MODE_DISABLED);
        return write(writer, image, param);
    }

    private static byte[] encode(BufferedImage image, String format, ImageWriteParam param) {
        return write(ImageIO.getImageWritersByFormatName(format).next(), image, param);
    }

    /**
     * Writes only the pixels: no metadata is handed to the writer.
     */
    private static byte[] write(ImageWriter writer, BufferedImage image, ImageWriteParam param) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private static byte[] head(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return in.readNBytes(HEAD_LENGTH);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        return bytes.length >= prefix.length && Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
    }

    private static String ascii(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, StandardCharsets.US_ASCII);
    }
}
