package info.unterrainer.presserl.reader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Points {@code presserl.theme.dir} at a directory under {@code target/} that tests fill and empty.
 */
public class ThemeProfile implements QuarkusTestProfile {

    static final Path DIR = Path.of("target", "test-theme").toAbsolutePath();

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("presserl.theme.dir", DIR.toString());
    }

    static void write(String relative, String content) {
        try {
            Path file = DIR.resolve(relative);
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void clear() {
        try {
            if (Files.exists(DIR)) {
                try (Stream<Path> paths = Files.walk(DIR)) {
                    paths.sorted(Comparator.reverseOrder()).filter(p -> !p.equals(DIR)).forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                }
            }
            Files.createDirectories(DIR);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
