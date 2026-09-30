package info.unterrainer.presserl.reader;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import info.unterrainer.presserl.TestSupport;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import jakarta.inject.Inject;

/**
 * The legal notice from the theme's {@code legal-notice.txt} on a public newspaper: its page and the
 * footer link.
 */
@QuarkusTest
@TestProfile(ThemeProfile.class)
class ReaderLegalNoticeTest {

    static final String NOTICE = """
            Offenlegung gemäß § 25 Mediengesetz

            Medieninhaber: Gerald Unterrainer, Enns
            Österreich
            """;

    @Inject
    DataSource dataSource;

    private long article;

    @BeforeEach
    void theme() {
        TestSupport.awaitReady();
        ThemeProfile.clear();
        ReaderFixtures fixtures = new ReaderFixtures(dataSource);
        fixtures.deleteAllArticles();
        article = fixtures.published("Notice check", Instant.parse("2026-09-20T12:00:00Z"));
    }

    @AfterEach
    void cleanUp() {
        ThemeProfile.clear();
        TestSupport.resetIssues(dataSource);
    }

    private static Response get(String path, String language) {
        return given().redirects().follow(false).header("Accept-Language", language).get(path);
    }

    @Test
    void noticeIsRenderedAsParagraphsWithLineBreaks() {
        ThemeProfile.write("legal-notice.txt", NOTICE);

        Response response = get("/legal-notice", "de");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.header("Cache-Control")).isNull();
        assertThat(response.asString()).contains("<main data-view=\"legal-notice\">",
                "<p class=\"masthead__name\"><a href=\"/\">", "<h1 class=\"legal-notice__title\">Impressum</h1>",
                "<p>Offenlegung gemäß § 25 Mediengesetz</p>",
                "<p>Medieninhaber: Gerald Unterrainer, Enns<br>Österreich</p>", "<nav class=\"section-bar\"");
        assertThat(get("/legal-notice", "en").asString())
                .contains("<h1 class=\"legal-notice__title\">Legal notice</h1>", ">Legal notice</a>");
    }

    @Test
    void windowsLineEndingsAndSeveralBlankLinesAreOneParagraphBreak() {
        ThemeProfile.write("legal-notice.txt", "One\r\n\r\n  \r\n\r\nTwo  \r\nThree\r\n");

        assertThat(get("/legal-notice", "de").asString()).contains("<p>One</p>", "<p>Two<br>Three</p>");
    }

    @Test
    void markupIsShownAsText() {
        ThemeProfile.write("legal-notice.txt", "<script>alert(1)</script> & <b>bold</b>");

        String html = get("/legal-notice", "de").asString();

        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt; &amp; &lt;b&gt;bold&lt;/b&gt;")
                .doesNotContain("<script>alert", "<b>bold");
    }

    @Test
    void aChangeShowsOnTheNextLoad() {
        ThemeProfile.write("legal-notice.txt", "First text");
        assertThat(get("/legal-notice", "de").asString()).contains("First text");

        ThemeProfile.write("legal-notice.txt", "Second text");

        assertThat(get("/legal-notice", "de").asString()).contains("Second text").doesNotContain("First text");
    }

    @Test
    void noNoticeWithoutFileOrWithWhitespaceOnly() {
        Response missing = get("/legal-notice", "de");
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(missing.asString()).contains("<main data-view=\"not-found\">");

        ThemeProfile.write("legal-notice.txt", "  \n\t\n\n");

        assertThat(get("/legal-notice", "de").statusCode()).isEqualTo(404);
        assertThat(get("/", "de").asString()).doesNotContain("/legal-notice");
    }

    @Test
    void theFileIsNotServedAsThemeFile() {
        ThemeProfile.write("legal-notice.txt", NOTICE);

        assertThat(get("/theme/legal-notice.txt", "de").statusCode()).isEqualTo(404);
    }

    @Test
    void readerPagesEndWithTheFooterLink() {
        ThemeProfile.write("legal-notice.txt", NOTICE);
        String footer = "<footer class=\"presserl-footer\">\n  <a href=\"/legal-notice\">Impressum</a>\n</footer>";

        for (String path : List.of("/", "/articles/" + article, "/issues", "/articles/999999", "/legal-notice")) {
            String html = get(path, "de").asString();
            assertThat(html).as(path).contains(footer);
            assertThat(html.indexOf(footer)).as(path).isGreaterThan(html.indexOf("</main>"));
        }
        assertThat(get("/", "en").asString()).contains("<a href=\"/legal-notice\">Legal notice</a>");
    }

    @Test
    void printViewsHaveNoFooterLink() {
        ThemeProfile.write("legal-notice.txt", NOTICE);

        String html = get("/print/article/" + article, "de").asString();

        assertThat(html).contains("<main data-view=\"print-article\">").doesNotContain("/legal-notice",
                "presserl-footer");
    }

    @Test
    void noFooterLinkWithoutNotice() {
        for (String path : List.of("/", "/articles/" + article, "/issues", "/articles/999999")) {
            assertThat(get(path, "de").asString()).as(path).doesNotContain("/legal-notice", "presserl-footer");
        }
    }
}
