package info.unterrainer.presserl.reader;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

    @Test
    void legalNoticeIsSplitIntoParagraphsOfLines() throws IOException {
        Files.writeString(theme.resolve("legal-notice.txt"), "\uFEFFFirst line\nsecond line   \n\n\n  \nNext\r\n");

        assertThat(files.legalNotice()).contains(List.of(List.of("First line", "second line"),
                List.of("Next")));
    }

    @Test
    void legalNoticeIsAbsentWhenMissingBlankTooLargeOrNotUtf8() throws IOException {
        assertThat(files.legalNotice()).isEmpty();

        Files.writeString(theme.resolve("legal-notice.txt"), " \n\t\n");
        assertThat(files.legalNotice()).isEmpty();

        Files.writeString(theme.resolve("legal-notice.txt"), "x".repeat(64 * 1024 + 1));
        assertThat(files.legalNotice()).isEmpty();

        Files.write(theme.resolve("legal-notice.txt"), new byte[] { 'o', 'k', (byte) 0xC3, (byte) 0x28 });
        assertThat(files.legalNotice()).isEmpty();

        Files.writeString(theme.resolve("legal-notice.txt"), "x".repeat(64 * 1024));
        assertThat(files.legalNotice()).isPresent();
    }

    @Test
    void iconsAreTheBundledDefaultsWithoutThemeFiles() {
        assertThat(files.icons()).isEqualTo(new ReaderPage.Icons("/reader/icons/favicon.svg",
                "/reader/icons/favicon.ico", "/reader/icons/apple-touch-icon.png"));
    }

    @Test
    void eachThemeIconReplacesOnlyItsOwnKind() throws IOException {
        Files.writeString(theme.resolve("favicon.svg"), "<svg/>");

        assertThat(files.icons()).isEqualTo(new ReaderPage.Icons("/theme/favicon.svg", "/reader/icons/favicon.ico",
                "/reader/icons/apple-touch-icon.png"));

        Files.write(theme.resolve("favicon.ico"), new byte[] { 0, 0, 1, 0 });
        Files.write(theme.resolve("apple-touch-icon.png"), new byte[] { 1 });
        Files.delete(theme.resolve("favicon.svg"));

        assertThat(files.icons()).isEqualTo(new ReaderPage.Icons("/reader/icons/favicon.svg", "/theme/favicon.ico",
                "/theme/apple-touch-icon.png"));
    }

    @Test
    void iconDirectoriesAreNoIcons() throws IOException {
        Files.createDirectory(theme.resolve("favicon.svg"));

        assertThat(files.icons().svg()).isEqualTo("/reader/icons/favicon.svg");
    }

    @Test
    void legalNoticeIsNeverAServableThemeFile() throws IOException {
        Files.writeString(theme.resolve("legal-notice.txt"), "Notice");

        assertThat(files.resolve("legal-notice.txt")).isEmpty();
    }
}
