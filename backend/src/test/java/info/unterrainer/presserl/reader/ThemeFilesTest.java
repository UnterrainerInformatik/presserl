package info.unterrainer.presserl.reader;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ThemeFilesTest {

    @TempDir
    Path temp;

    private Path theme;
    private ThemeFiles files;

    @BeforeEach
    void themeDirectory() throws IOException {
        theme = Files.createDirectory(temp.resolve("theme"));
        Files.writeString(temp.resolve("secret.css"), "outside");
        Files.writeString(theme.resolve("custom.css"), ":root {}");
        Files.createDirectories(theme.resolve("fonts"));
        Files.write(theme.resolve("fonts/comic.woff2"), new byte[] { 1, 2, 3 });
        Files.writeString(theme.resolve("notes.txt"), "not served");
        Files.createDirectory(theme.resolve("folder.css"));
        files = new ThemeFiles(theme.toString());
    }

    @Test
    void servesAllowedFiles() throws IOException {
        assertThat(files.resolve("custom.css")).contains(theme.toRealPath().resolve("custom.css"));
        assertThat(files.resolve("fonts/comic.woff2")).isPresent();
        assertThat(files.customCssPresent()).isTrue();
    }

    @Test
    void customCssPresenceFollowsTheFile() throws IOException {
        Files.delete(theme.resolve("custom.css"));

        assertThat(files.customCssPresent()).isFalse();
    }

    @Test
    void refusesEscapes() {
        assertThat(files.resolve("../secret.css")).isEmpty();
        assertThat(files.resolve("fonts/../../secret.css")).isEmpty();
        assertThat(files.resolve(temp.resolve("secret.css").toString())).isEmpty();
        assertThat(files.resolve("fonts\\..\\..\\secret.css")).isEmpty();
        assertThat(files.resolve("custom.css\0.png")).isEmpty();
    }

    @Test
    void refusesSymlinksPointingOutside() throws IOException {
        Files.createSymbolicLink(theme.resolve("link.css"), temp.resolve("secret.css"));
        Files.createSymbolicLink(theme.resolve("outside"), temp);

        assertThat(files.resolve("link.css")).isEmpty();
        assertThat(files.resolve("outside/secret.css")).isEmpty();
    }

    @Test
    void followsSymlinksInsideTheDirectory() throws IOException {
        Files.createSymbolicLink(theme.resolve("alias.css"), theme.resolve("custom.css"));

        assertThat(files.resolve("alias.css")).isPresent();
    }

    @Test
    void refusesOtherTypesDirectoriesAndMissingFiles() {
        assertThat(files.resolve("notes.txt")).isEmpty();
        assertThat(files.resolve("folder.css")).isEmpty();
        assertThat(files.resolve("missing.css")).isEmpty();
        assertThat(files.resolve("")).isEmpty();
        assertThat(files.resolve("fonts")).isEmpty();
    }

    @Test
    void missingDirectoryIsEmpty() {
        ThemeFiles missing = new ThemeFiles(temp.resolve("nowhere").toString());

        assertThat(missing.resolve("custom.css")).isEmpty();
        assertThat(missing.customCssPresent()).isFalse();
    }

    @Test
    void contentTypesFollowTheExtension() {
        assertThat(ThemeFiles.type("custom.css")).contains("text/css;charset=UTF-8");
        assertThat(ThemeFiles.type("fonts/a.WOFF2")).contains("font/woff2");
        assertThat(ThemeFiles.type("logo.svg")).contains("image/svg+xml");
        assertThat(ThemeFiles.type("photo.jpeg")).contains("image/jpeg");
        assertThat(ThemeFiles.type("dir.css/file")).isEmpty();
        assertThat(ThemeFiles.type("script.js")).isEmpty();
        assertThat(ThemeFiles.type("page.html")).isEmpty();
    }
}
