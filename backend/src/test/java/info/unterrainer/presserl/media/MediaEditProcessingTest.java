package info.unterrainer.presserl.media;

import static info.unterrainer.presserl.media.MediaFixtures.bytes;
import static info.unterrainer.presserl.media.MediaFixtures.directories;
import static info.unterrainer.presserl.media.MediaFixtures.read;
import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import info.unterrainer.presserl.media.MediaProcessor.Crop;
import info.unterrainer.presserl.media.MediaProcessor.Ellipse;
import info.unterrainer.presserl.media.MediaProcessor.Processed;

/**
 * Pixelation and cropping of stored images ({@link MediaProcessor#edit}).
 */
class MediaEditProcessingTest {

    private final MediaProcessor processor = new MediaProcessor();

    /**
     * An opaque image where every pixel has a different colour, so any averaging shows.
     */
    private static BufferedImage noise(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, (x * 7 % 256) << 16 | (y * 13 % 256) << 8 | ((x + y) * 5 % 256));
            }
        }
        return image;
    }

    private static boolean inside(Ellipse e, int x, int y) {
        double dx = (x + 0.5 - e.cx()) / e.rx();
        double dy = (y + 0.5 - e.cy()) / e.ry();
        return dx * dx + dy * dy <= 1;
    }

    private static byte[] png(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    @Test
    void blockSideIsAnEighthOfTheSmallerDiameterButAtLeastTwelve() {
        assertThat(MediaProcessor.blockSize(new Ellipse(400, 300, 100, 60))).isEqualTo(15);
        assertThat(MediaProcessor.blockSize(new Ellipse(400, 300, 20, 20))).isEqualTo(12);
        assertThat(MediaProcessor.blockSize(new Ellipse(400, 300, 4, 400))).isEqualTo(12);
        assertThat(MediaProcessor.blockSize(new Ellipse(800, 800, 400, 800))).isEqualTo(100);
    }

    @Test
    void pixelsInsideTheEllipseTakeTheirBlockAverageAndOthersStay() {
        BufferedImage original = noise(800, 600);
        BufferedImage image = noise(800, 600);
        Ellipse ellipse = new Ellipse(400, 300, 100, 60);

        MediaProcessor.pixelate(image, ellipse);

        int b = 15;
        for (int y = 0; y < 600; y++) {
            for (int x = 0; x < 800; x++) {
                if (!inside(ellipse, x, y)) {
                    assertThat(image.getRGB(x, y)).as("%d,%d", x, y).isEqualTo(original.getRGB(x, y));
                    continue;
                }
                int bx = 300 + (x - 300) / b * b;
                int by = 240 + (y - 240) / b * b;
                long r = 0;
                long g = 0;
                long bl = 0;
                for (int yy = by; yy < by + b; yy++) {
                    for (int xx = bx; xx < bx + b; xx++) {
                        int rgb = original.getRGB(xx, yy);
                        r += (rgb >> 16) & 0xFF;
                        g += (rgb >> 8) & 0xFF;
                        bl += rgb & 0xFF;
                    }
                }
                int n = b * b;
                int expected = 0xFF000000 | (int) ((r + n / 2) / n) << 16 | (int) ((g + n / 2) / n) << 8
                        | (int) ((bl + n / 2) / n);
                assertThat(image.getRGB(x, y)).as("%d,%d", x, y).isEqualTo(expected);
            }
        }
    }

    @Test
    void smallEllipseUsesTwelvePixelBlocks() {
        BufferedImage image = noise(200, 200);
        Ellipse ellipse = new Ellipse(100, 100, 20, 20);

        MediaProcessor.pixelate(image, ellipse);

        // block from (80,80) to (92,92) lies inside the ellipse: one colour
        int colour = image.getRGB(90, 90);
        for (int y = 86; y < 92; y++) {
            for (int x = 86; x < 92; x++) {
                assertThat(image.getRGB(x, y)).isEqualTo(colour);
            }
        }
        assertThat(image.getRGB(92, 90)).isNotEqualTo(colour);
    }

    @Test
    void ellipseReachingOverTheEdgeIsClipped() {
        BufferedImage image = noise(100, 100);

        MediaProcessor.pixelate(image, new Ellipse(0, 0, 40, 40));

        // the block at (0,0) is clipped to 8 x 8 inside the image (it starts at -40 + 3 * 12 = -4)
        int colour = image.getRGB(0, 0);
        assertThat(image.getRGB(7, 7)).isEqualTo(colour);
        assertThat(image.getRGB(8, 0)).isNotEqualTo(colour);
    }

    @Test
    void transparentPixelsAverageTheirAlpha() {
        BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 24; x++) {
                image.setRGB(x, y, x < 6 ? 0x00000000 : 0xFFFF0000);
            }
        }

        MediaProcessor.pixelate(image, new Ellipse(6, 6, 6, 6));

        // one 12 x 12 block from (0,0), half of it transparent: alpha 127.5, rounded
        assertThat(image.getRGB(6, 6) >>> 24).isEqualTo(128);
    }

    @Test
    void cropAndPixelateAJpeg() {
        Processed result = processor.edit(bytes("photo-gps.jpg"), new Crop(100, 50, 400, 300),
                List.of(new Ellipse(300, 200, 50, 50)));

        assertThat(result.contentType()).isEqualTo("image/jpeg");
        assertThat(result.width()).isEqualTo(400);
        assertThat(result.height()).isEqualTo(300);
        assertThat(read(result.bytes()).getWidth()).isEqualTo(400);
        assertThat(result.renditions()).extracting(r -> r.kind().value()).containsExactly("print", "web",
                "thumbnail");
        assertThat(result.renditions()).allSatisfy(r -> assertThat(r.contentType()).isEqualTo("image/jpeg"));
        assertThat(result.renditions().getLast().width()).isEqualTo(400);
        assertThat(directories(result.bytes())).doesNotContain("ExifIFD0Directory", "GpsDirectory",
                "XmpDirectory", "IccDirectory");
    }

    @Test
    void largeCropGetsSmallerRenditions() {
        Processed result = processor.edit(MediaFixtures.jpeg(4000, 3000), new Crop(0, 0, 3600, 2400), List.of());

        assertThat(result.width()).isEqualTo(3600);
        assertThat(result.renditions()).extracting(MediaProcessor.Rendition::width).containsExactly(3000, 1600,
                480);
        assertThat(result.renditions().get(1).height()).isEqualTo(1067);
    }

    @Test
    void croppedTransparentPngStaysPng() {
        BufferedImage stored = read(png(read(bytes("transparent.png"))));
        int w = stored.getWidth();
        int h = stored.getHeight();

        Processed result = processor.edit(png(stored), new Crop(0, 0, w - 10, h - 10), List.of());

        assertThat(result.contentType()).isEqualTo("image/png");
        BufferedImage image = read(result.bytes());
        assertThat(image.getColorModel().hasAlpha()).isTrue();
        assertThat(MediaProcessor.hasTransparency(image)).isTrue();
        assertThat(result.renditions()).allSatisfy(r -> {
            assertThat(r.contentType()).isEqualTo("image/png");
            assertThat(MediaProcessor.hasTransparency(read(r.bytes()))).isTrue();
        });
    }

    @Test
    void pixelatedOnlyKeepsTheSize() {
        Processed result = processor.edit(MediaFixtures.jpeg(1600, 1067), null,
                List.of(new Ellipse(600, 400, 80, 110)));

        assertThat(result.width()).isEqualTo(1600);
        assertThat(result.height()).isEqualTo(1067);
    }
}
