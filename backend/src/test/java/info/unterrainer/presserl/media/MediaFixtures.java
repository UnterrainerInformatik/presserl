package info.unterrainer.presserl.media;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.StreamSupport;

import javax.imageio.ImageIO;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;

/**
 * Fixtures from {@code src/test/resources/media/} (see {@code generate.py}) and generated images.
 */
public final class MediaFixtures {

    private MediaFixtures() {
    }

    public static byte[] bytes(String name) {
        try (InputStream in = MediaFixtures.class.getResourceAsStream("/media/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("no fixture " + name);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * A JPEG of the given size: left half red, right half blue.
     */
    public static byte[] jpeg(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, width / 2, height);
        g.setColor(Color.BLUE);
        g.fillRect(width / 2, 0, width - width / 2, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "jpeg", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    public static BufferedImage read(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The simple class names of every metadata directory metadata-extractor finds in the image.
     */
    public static List<String> directories(byte[] bytes) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes));
            return StreamSupport.stream(metadata.getDirectories().spliterator(), false)
                    .map(Directory::getClass)
                    .map(Class::getSimpleName)
                    .toList();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
