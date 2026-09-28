package info.unterrainer.presserl.media;

import static info.unterrainer.presserl.media.MediaFixtures.bytes;
import static info.unterrainer.presserl.media.MediaFixtures.directories;
import static info.unterrainer.presserl.media.MediaFixtures.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import info.unterrainer.presserl.media.MediaProcessor.Rendition;

import jakarta.ws.rs.core.Response.Status;

class MediaProcessorTest {

    private static final String[] FORBIDDEN_DIRECTORIES = { "ExifIFD0Directory", "ExifSubIFDDirectory",
            "ExifThumbnailDirectory", "GpsDirectory", "XmpDirectory", "IptcDirectory", "IccDirectory",
            "JpegCommentDirectory", "PhotoshopDirectory" };

    @TempDir
    Path temp;

    private final MediaProcessor processor = new MediaProcessor();

    private MediaProcessor.Processed process(byte[] content) throws IOException {
        // the name is deliberately misleading: only the bytes count
        Path file = Files.write(temp.resolve("upload.jpg"), content);
        return processor.process(file);
    }

    private void assertRefused(byte[] content, Status status) {
        assertThatThrownBy(() -> process(content))
                .isInstanceOfSatisfying(MediaException.class, e -> {
                    assertThat(e.status()).isEqualTo(status);
                    assertThat(e.errors()).singleElement().satisfies(error -> assertThat(error.field()).isEqualTo("file"));
                });
    }

    @Test
    void jpegIsReencodedAsJpeg() throws IOException {
        MediaProcessor.Processed result = process(bytes("photo-gps.jpg"));

        assertThat(result.contentType()).isEqualTo("image/jpeg");
        assertThat(result.extension()).isEqualTo("jpg");
        assertThat(result.width()).isEqualTo(640);
        assertThat(result.height()).isEqualTo(480);
        assertThat(read(result.bytes()).getWidth()).isEqualTo(640);
    }

    @Test
    void pngWithMisleadingNameIsProcessedAsPng() throws IOException {
        MediaProcessor.Processed result = process(bytes("opaque.png"));

        assertThat(result.contentType()).isEqualTo("image/jpeg");
        assertThat(result.width()).isEqualTo(1200);
        assertThat(result.height()).isEqualTo(800);
    }

    @Test
    void opaquePngWithAlphaChannelBecomesJpeg() throws IOException {
        assertThat(process(bytes("opaque-alpha.png")).contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void transparentPngStaysPngWithAlpha() throws IOException {
        MediaProcessor.Processed result = process(bytes("transparent.png"));

        assertThat(result.contentType()).isEqualTo("image/png");
        BufferedImage image = read(result.bytes());
        assertThat(image.getColorModel().hasAlpha()).isTrue();
        assertThat(image.getRGB(250, 100) >>> 24).isZero();
        assertThat(image.getRGB(50, 100) >>> 24).isEqualTo(0xFF);
    }

    @Test
    void lossyWebpBecomesJpeg() throws IOException {
        MediaProcessor.Processed result = process(bytes("photo.webp"));

        assertThat(result.contentType()).isEqualTo("image/jpeg");
        assertThat(result.width()).isEqualTo(320);
        assertThat(result.height()).isEqualTo(240);
    }

    @Test
    void transparentWebpBecomesPngWithAlpha() throws IOException {
        MediaProcessor.Processed result = process(bytes("transparent.webp"));

        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(result.extension()).isEqualTo("png");
        BufferedImage image = read(result.bytes());
        assertThat(image.getWidth()).isEqualTo(200);
        assertThat(image.getRGB(150, 50) >>> 24).isZero();
        assertThat(new Color(image.getRGB(50, 50), true).getRed()).isGreaterThan(150);
    }

    @Test
    void htmlDisguisedAsJpegIsUnsupported() {
        assertRefused(bytes("cat.jpg"), Status.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void gifIsUnsupported() {
        assertRefused(bytes("image.gif"), Status.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void heicIsUnsupported() {
        assertRefused(bytes("image.heic"), Status.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void animatedWebpIsUnsupported() {
        assertRefused(bytes("animated.webp"), Status.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void emptyFileIsUnsupported() {
        assertRefused(new byte[0], Status.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void decompressionBombIsRefusedBeforeDecoding() {
        assertThatThrownBy(() -> process(bytes("bomb.png")))
                .isInstanceOfSatisfying(MediaException.class, e -> {
                    assertThat(e.status()).isEqualTo(Status.BAD_REQUEST);
                    assertThat(e.errors().getFirst().message()).contains("30000 x 30000");
                });
    }

    @Test
    void dimensionLimits() {
        MediaProcessor.checkDimensions(20_000, 2_500);
        MediaProcessor.checkDimensions(10_000, 5_000);
        assertThatThrownBy(() -> MediaProcessor.checkDimensions(20_001, 10)).isInstanceOf(MediaException.class);
        assertThatThrownBy(() -> MediaProcessor.checkDimensions(10, 20_001)).isInstanceOf(MediaException.class);
        assertThatThrownBy(() -> MediaProcessor.checkDimensions(10_000, 5_001)).isInstanceOf(MediaException.class);
    }

    @Test
    void truncatedJpegIsInvalid() {
        assertThatThrownBy(() -> process(Arrays.copyOf(bytes("photo-gps.jpg"), 3000)))
                .hasMessageContaining("damaged");
        byte[] jpeg = MediaFixtures.jpeg(800, 600);
        assertRefused(Arrays.copyOf(jpeg, jpeg.length / 2), Status.BAD_REQUEST);
    }

    @Test
    void noMetadataSurvives() throws IOException {
        byte[] original = bytes("photo-gps.jpg");
        assertThat(directories(original)).contains("ExifIFD0Directory", "GpsDirectory", "XmpDirectory",
                "JpegCommentDirectory");

        byte[] stored = process(original).bytes();

        assertThat(directories(stored)).doesNotContain(FORBIDDEN_DIRECTORIES);
        String raw = new String(stored, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertThat(raw).doesNotContain("Exif", "PresserlCam", "Spy 3000", "2026:09:27", "Secret Photographer",
                "taken at home", "http://ns.adobe.com/xap");
    }

    @Test
    void orientationSixIsRotatedClockwise() throws IOException {
        MediaProcessor.Processed result = process(bytes("rotated-6.jpg"));

        assertThat(result.width()).isEqualTo(300);
        assertThat(result.height()).isEqualTo(400);
        BufferedImage image = read(result.bytes());
        assertThat(image.getWidth()).isEqualTo(300);
        assertThat(image.getHeight()).isEqualTo(400);
        // the red left half of the stored pixels is on top after rotating 90 degrees clockwise
        assertThat(new Color(image.getRGB(150, 50)).getRed()).isGreaterThan(200);
        assertThat(new Color(image.getRGB(150, 350)).getBlue()).isGreaterThan(200);
        assertThat(directories(result.bytes())).doesNotContain(FORBIDDEN_DIRECTORIES);
    }

    @Test
    void largeImageIsScaledToTheLongSide() throws IOException {
        MediaProcessor.Processed result = process(MediaFixtures.jpeg(6000, 4000));

        assertThat(result.contentType()).isEqualTo("image/jpeg");
        assertThat(result.width()).isEqualTo(4096);
        assertThat(result.height()).isEqualTo(2731);
        BufferedImage image = read(result.bytes());
        assertThat(image.getWidth()).isEqualTo(4096);
        assertThat(image.getHeight()).isEqualTo(2731);
    }

    @Test
    void portraitImageIsScaledToTheLongSide() throws IOException {
        MediaProcessor.Processed result = process(MediaFixtures.jpeg(3000, 5000));

        assertThat(result.width()).isEqualTo(2458);
        assertThat(result.height()).isEqualTo(4096);
    }

    @Test
    void smallImagesAreNotEnlarged() throws IOException {
        MediaProcessor.Processed result = process(MediaFixtures.jpeg(100, 50));

        assertThat(result.width()).isEqualTo(100);
        assertThat(result.height()).isEqualTo(50);
    }

    @Test
    void largePhotoGetsThreeRenditions() throws IOException {
        List<Rendition> renditions = process(MediaFixtures.jpeg(6000, 4000)).renditions();

        assertThat(renditions).extracting(Rendition::kind)
                .containsExactly(RenditionKind.PRINT, RenditionKind.WEB, RenditionKind.THUMBNAIL);
        assertThat(renditions).extracting(r -> r.width() + "x" + r.height())
                .containsExactly("3000x2000", "1600x1067", "480x320");
        for (Rendition rendition : renditions) {
            assertThat(rendition.contentType()).isEqualTo("image/jpeg");
            assertThat(rendition.extension()).isEqualTo("jpg");
            BufferedImage image = read(rendition.bytes());
            assertThat(image.getWidth()).isEqualTo(rendition.width());
            assertThat(image.getHeight()).isEqualTo(rendition.height());
        }
    }

    @Test
    void smallOpaquePngGetsRenditionsOfItsOwnSizeAsJpeg() throws IOException {
        BufferedImage small = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(small, "png", png);

        List<Rendition> renditions = process(png.toByteArray()).renditions();

        assertThat(renditions).hasSize(3).allSatisfy(rendition -> {
            assertThat(rendition.contentType()).isEqualTo("image/jpeg");
            assertThat(rendition.width()).isEqualTo(400);
            assertThat(rendition.height()).isEqualTo(300);
            assertThat(read(rendition.bytes()).getWidth()).isEqualTo(400);
        });
    }

    @Test
    void transparentPngGetsPngRenditionsWithAlpha() throws IOException {
        List<Rendition> renditions = process(bytes("transparent.png")).renditions();

        assertThat(renditions).hasSize(3).allSatisfy(rendition -> {
            assertThat(rendition.contentType()).isEqualTo("image/png");
            assertThat(rendition.extension()).isEqualTo("png");
            BufferedImage image = read(rendition.bytes());
            assertThat(image.getColorModel().hasAlpha()).isTrue();
            assertThat(MediaProcessor.hasTransparency(image)).isTrue();
        });
    }

    @Test
    void portraitRenditionsKeepTheOrientationOfTheSides() throws IOException {
        List<Rendition> renditions = process(MediaFixtures.jpeg(3000, 5000)).renditions();

        assertThat(renditions).extracting(r -> r.width() + "x" + r.height())
                .containsExactly("1800x3000", "960x1600", "288x480");
    }

    @Test
    void renditionsCarryNoMetadata() throws IOException {
        for (Rendition rendition : process(bytes("photo-gps.jpg")).renditions()) {
            assertThat(directories(rendition.bytes())).as(rendition.kind().value())
                    .doesNotContain(FORBIDDEN_DIRECTORIES);
        }
    }

    @Test
    void renditionsAreDerivedFromAStoredImage() throws IOException {
        MediaProcessor.Processed stored = process(MediaFixtures.jpeg(6000, 4000));

        List<Rendition> derived = processor.deriveRenditions(stored.bytes());

        assertThat(derived).extracting(r -> r.kind().value() + " " + r.width() + "x" + r.height() + " " + r.contentType())
                .containsExactly("print 3000x2000 image/jpeg", "web 1600x1067 image/jpeg",
                        "thumbnail 480x320 image/jpeg");
    }

    @Test
    void renditionsOfAStoredTransparentImageStayPng() throws IOException {
        byte[] stored = process(bytes("transparent.png")).bytes();

        assertThat(processor.deriveRenditions(stored)).allSatisfy(rendition -> {
            assertThat(rendition.contentType()).isEqualTo("image/png");
            assertThat(MediaProcessor.hasTransparency(read(rendition.bytes()))).isTrue();
        });
    }

    @Test
    void derivingFromDamagedBytesIsRefused() {
        assertThatThrownBy(() -> processor.deriveRenditions(bytes("cat.jpg"))).isInstanceOf(MediaException.class);
    }

    @Test
    void everyOrientationEndsUpright() {
        // a 4 x 2 image whose top-left pixel is marked; displayed upright it is the top-left pixel
        for (int orientation = 1; orientation <= 8; orientation++) {
            BufferedImage stored = storedFor(orientation);
            BufferedImage upright = MediaProcessor.orient(stored, orientation);
            assertThat(upright.getWidth()).as("width for %d", orientation).isEqualTo(4);
            assertThat(upright.getHeight()).as("height for %d", orientation).isEqualTo(2);
            assertThat(upright.getRGB(0, 0) & 0xFFFFFF).as("marker for %d", orientation).isEqualTo(0xFF0000);
            assertThat(upright.getRGB(3, 1) & 0xFFFFFF).as("opposite for %d", orientation).isEqualTo(0x0000FF);
        }
    }

    /**
     * How a camera stores an upright 4 x 2 image (red top-left, blue bottom-right) for each EXIF
     * orientation: the inverse of the transform the orientation asks for.
     */
    private static BufferedImage storedFor(int orientation) {
        boolean swap = orientation >= 5;
        BufferedImage image = new BufferedImage(swap ? 2 : 4, swap ? 4 : 2, BufferedImage.TYPE_INT_RGB);
        int[][] redBlue = switch (orientation) {
            case 1 -> new int[][] { { 0, 0 }, { 3, 1 } };
            case 2 -> new int[][] { { 3, 0 }, { 0, 1 } };
            case 3 -> new int[][] { { 3, 1 }, { 0, 0 } };
            case 4 -> new int[][] { { 0, 1 }, { 3, 0 } };
            case 5 -> new int[][] { { 0, 0 }, { 1, 3 } };
            case 6 -> new int[][] { { 0, 3 }, { 1, 0 } };
            case 7 -> new int[][] { { 1, 3 }, { 0, 0 } };
            case 8 -> new int[][] { { 1, 0 }, { 0, 3 } };
            default -> throw new IllegalArgumentException();
        };
        image.setRGB(redBlue[0][0], redBlue[0][1], 0xFF0000);
        image.setRGB(redBlue[1][0], redBlue[1][1], 0x0000FF);
        return image;
    }
}
