package info.unterrainer.presserl.reader;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import info.unterrainer.presserl.TestSupport;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;

/**
 * Default theme, styling API, section bar and text-size switch of the reader, without a fork theme
 * directory.
 */
@QuarkusTest
class ReaderThemeTest {

    private static final Instant SEPT_20 = Instant.parse("2026-09-20T12:00:00Z");
    private static final Instant SEPT_25 = Instant.parse("2026-09-25T12:00:00Z");
    private static final List<String> PUBLIC_TOKENS = List.of("--presserl-font-body", "--presserl-font-headline",
            "--presserl-color-paper", "--presserl-color-ink", "--presserl-color-muted", "--presserl-color-accent",
            "--presserl-color-rule", "--presserl-text-size", "--presserl-letter-spacing", "--presserl-measure",
            "--presserl-grid-columns", "--presserl-section-red", "--presserl-section-orange",
            "--presserl-section-yellow", "--presserl-section-green", "--presserl-section-teal",
            "--presserl-section-blue", "--presserl-section-purple", "--presserl-section-pink");

    @Inject
    DataSource dataSource;

    private ReaderFixtures fixtures;

    @BeforeEach
    void emptyNewspaper() {
        fixtures = new ReaderFixtures(dataSource);
        fixtures.deleteAllArticles();
    }

    @AfterEach
    void clearOverrides() {
        execute("UPDATE newspaper SET settings = '{}' WHERE id = 1");
    }

    // --- effective text size

    @Test
    void newspaperDefaultWithoutCookie() {
        execute("UPDATE newspaper SET settings = '{\"reader.text-size\": \"l\"}' WHERE id = 1");

        assertThat(given().get("/").asString()).contains("<html lang=\"de\" data-text-size=\"l\">");
    }

    @Test
    void readersChoiceWinsOnEveryPage() {
        execute("UPDATE newspaper SET settings = '{\"reader.text-size\": \"l\"}' WHERE id = 1");
        long id = fixtures.published("Sized", SEPT_20);

        for (String path : List.of("/", "/articles/" + id, "/articles/999999")) {
            assertThat(given().cookie(TextSizeResource.COOKIE, "s").get(path).asString()).as(path)
                    .contains("data-text-size=\"s\"");
        }
    }

    @Test
    void tamperedCookieFallsBackToTheNewspaperDefault() {
        execute("UPDATE newspaper SET settings = '{\"reader.text-size\": \"l\"}' WHERE id = 1");

        assertThat(given().cookie(TextSizeResource.COOKIE, "huge").get("/").asString())
                .contains("data-text-size=\"l\"");
    }

    @Test
    void switchMarksTheCurrentSize() {
        String html = given().get("/").asString();

        assertThat(html).contains(
                "<form class=\"text-size-switch\" method=\"post\" action=\"/text-size\"",
                "<input type=\"hidden\" name=\"next\" value=\"/\">",
                "value=\"s\" aria-pressed=\"false\" title=\"klein\">S</button>",
                "value=\"m\" aria-pressed=\"true\" title=\"mittel\">M</button>",
                "value=\"l\" aria-pressed=\"false\" title=\"groß\">L</button>",
                "value=\"xl\" aria-pressed=\"false\" title=\"sehr groß\">XL</button>",
                ">Schriftgröße</span>");
        assertThat(html.split("aria-pressed=\"true\"").length - 1).isEqualTo(1);
    }

    @Test
    void switchReturnsToThePageWithItsQuery() {
        long id = fixtures.published("Return", SEPT_20);

        assertThat(given().get("/articles/" + id + "?from=x&y=1").asString())
                .contains("name=\"next\" value=\"/articles/" + id + "?from=x&amp;y=1\"");
    }

    @Test
    void englishSwitch() {
        assertThat(given().header("Accept-Language", "en").get("/").asString())
                .contains(">Text size</span>", "title=\"extra large\">XL</button>");
    }

    // --- POST /text-size

    @Test
    void choosingALargerSize() {
        long id = fixtures.published("Choose", SEPT_20);

        Response response = postTextSize("xl", "/articles/" + id);

        assertThat(response.statusCode()).isEqualTo(303);
        assertThat(response.header("Location")).endsWith("/articles/" + id);
        assertThat(response.header("Cache-Control")).isEqualTo("no-store");
        String cookie = response.header("Set-Cookie");
        assertThat(cookie).contains(TextSizeResource.COOKIE + "=xl", "Path=/", "Max-Age=31536000", "HttpOnly")
                .containsIgnoringCase("SameSite=Lax")
                // Secure only in production (presserl.reader.cookie-secure)
                .doesNotContainIgnoringCase("secure;").doesNotEndWith("Secure");
        assertThat(given().cookie(TextSizeResource.COOKIE, response.cookie(TextSizeResource.COOKIE))
                .get("/articles/" + id).asString()).contains("data-text-size=\"xl\"");
    }

    @Test
    void openRedirectIsRefused() {
        Response response = postTextSize("l", "https://evil.example/");

        assertThat(response.statusCode()).isEqualTo(303);
        assertThat(response.header("Location")).doesNotContain("evil").endsWith("/");
        assertThat(response.cookie(TextSizeResource.COOKIE)).isEqualTo("l");
    }

    @Test
    void protocolRelativeTargetIsRefused() {
        assertThat(postTextSize("l", "//evil.example/").header("Location")).doesNotContain("evil");
    }

    @Test
    void unknownSizeSetsNoCookie() {
        Response response = postTextSize("huge", "/");

        assertThat(response.statusCode()).isEqualTo(303);
        assertThat(response.header("Location")).endsWith("/");
        assertThat(response.header("Set-Cookie")).isNull();
    }

    @Test
    void missingFieldsStillRedirectHome() {
        Response response = given().redirects().follow(false).contentType("application/x-www-form-urlencoded")
                .post("/text-size");

        assertThat(response.statusCode()).isEqualTo(303);
        assertThat(response.header("Set-Cookie")).isNull();
    }

    // --- section bar and section tags

    @Test
    void sectionBarListsSectionsInOrderWithTheirColours() {
        TestSupport.deleteSections(dataSource);
        fixtures.section("General", "red", 0);
        long sport = fixtures.section("Sport", "blue", 1);
        fixtures.publishedIn(sport, "Match won", SEPT_20);

        String html = given().get("/").asString();

        String bar = html.substring(html.indexOf("<nav class=\"section-bar\""), html.indexOf("</nav>"));
        assertThat(bar).contains("aria-label=\"Ressorts\"");
        assertThat(bar.indexOf("<span class=\"section-tag\" data-section-color=\"red\">General</span>"))
                .isPositive()
                .isLessThan(bar.indexOf("<span class=\"section-tag\" data-section-color=\"blue\">Sport</span>"));
        assertThat(html.indexOf("</header>")).isLessThan(html.indexOf("<nav class=\"section-bar\""));
        assertThat(bar).doesNotContain("<a ");
    }

    @Test
    void noSectionsNoSectionBar() {
        TestSupport.deleteSections(dataSource);

        assertThat(given().get("/").asString()).doesNotContain("section-bar", "section-tag");
    }

    @Test
    void storyAndArticleShowTheirSection() {
        TestSupport.deleteSections(dataSource);
        fixtures.section("General", "red", 0);
        long sport = fixtures.section("Sport", "blue", 1);
        long id = fixtures.publishedIn(sport, "Match won", SEPT_20);

        String front = given().get("/").asString();
        String article = given().get("/articles/" + id).asString();

        String story = front.substring(front.indexOf("<article class=\"lead-article\">"));
        story = story.substring(0, story.indexOf("</article>"));
        assertThat(story.indexOf("<span class=\"section-tag\" data-section-color=\"blue\">Sport</span>"))
                .isPositive()
                .isLessThan(story.indexOf("<h2 class=\"headline\">"));
        String page = article.substring(article.indexOf("<article class=\"article\">"));
        assertThat(page.indexOf("<span class=\"section-tag\" data-section-color=\"blue\">Sport</span>"))
                .isPositive()
                .isLessThan(page.indexOf("<h1 class=\"headline\">Match won</h1>"));
    }

    // --- styling API

    @Test
    void frontPageCarriesTheDocumentedHooks() {
        long first = fixtures.article("PUBLISHED", "anna", "Anna", 1, SEPT_20);
        fixtures.revision(first, 1, "Garden", "Older", "", "A lead.", ReaderFixtures.EMPTY_BODY, SEPT_20);
        fixtures.published("Newer", SEPT_25);

        String html = given().get("/").asString();

        assertThat(html).contains("<main data-view=\"frontpage\">", "<header class=\"masthead\">",
                "class=\"masthead__name\"", "<nav class=\"section-bar\"", "class=\"text-size-switch\"",
                "<div class=\"stories\">", "<article class=\"lead-article\">", "<article class=\"article-card\">",
                "<p class=\"kicker\">Garden</p>", "<h2 class=\"headline\">", "<p class=\"lead\">A lead.</p>",
                "<p class=\"byline\">", "data-section-color=\"");
        assertThat(html.indexOf("lead-article")).isLessThan(html.indexOf("article-card"));
    }

    @Test
    void mastheadSitsInsideTheViewSoViewSpecificRulesReachIt() {
        long id = fixtures.published("Inside", SEPT_20);

        for (String path : List.of("/", "/articles/" + id, "/articles/999999")) {
            String html = given().get(path).asString();
            assertThat(html.indexOf("<main data-view=")).as(path).isPositive()
                    .isLessThan(html.indexOf("<header class=\"masthead\">"));
        }
    }

    @Test
    void articlePageCarriesTheDocumentedHooks() {
        long id = fixtures.article("PUBLISHED", "anna", "Anna", 1, SEPT_20);
        fixtures.revision(id, 1, "Garden", "Hello", "Sub", "The lead.", ReaderFixtures.EMPTY_BODY, SEPT_20);

        assertThat(given().get("/articles/" + id).asString()).contains("<main data-view=\"article\">",
                "<article class=\"article\">", "<p class=\"kicker\">Garden</p>", "<h1 class=\"headline\">Hello</h1>",
                "<p class=\"subheadline\">Sub</p>", "<p class=\"lead\">The lead.</p>", "<p class=\"byline\">",
                "<div class=\"article__body\">", "<span class=\"section-tag\" data-section-color=\"");
        assertThat(given().get("/articles/999999").asString()).contains("<main data-view=\"not-found\">");
    }

    @Test
    void customCssIsNotLinkedWithoutThemeDirectory() {
        assertThat(given().get("/").asString())
                .contains("<link rel=\"stylesheet\" href=\"/reader/reader.css\">")
                .doesNotContain("/theme/custom.css");
    }

    // --- stylesheet and fonts

    @Test
    void stylesheetDefinesEveryPublicTokenOnRoot() {
        String css = given().get("/reader/reader.css").then().statusCode(200).extract().asString();

        String root = css.substring(css.indexOf(":root {"), css.indexOf("}", css.indexOf(":root {")));
        for (String token : PUBLIC_TOKENS) {
            assertThat(root).as(token).containsPattern(Pattern.quote(token) + ":\\s*[^;]+;");
        }
        assertThat(css).contains("@media (prefers-color-scheme: dark)", "[data-text-size=\"s\"]",
                "[data-text-size=\"xl\"]", "var(--presserl-text-size)");
    }

    @Test
    void stylesheetReferencesNoForeignOrigin() {
        String css = given().get("/reader/reader.css").asString();

        assertThat(css).doesNotContainPattern("url\\(\\s*[\"']?(https?:)?//").doesNotContain("@import");
    }

    @Test
    void stylesheetUsesNoItalicsOrJustification() {
        String css = given().get("/reader/reader.css").asString();

        assertThat(css).doesNotContain("font-style: italic", "text-align: justify", "hyphens: auto");
    }

    @Test
    void everyFontOfTheStylesheetIsServedAsWoff2WithItsLicence() {
        String css = given().get("/reader/reader.css").asString();
        List<String> fonts = Pattern.compile("url\\(\"(fonts/[^\"]+\\.woff2)\"\\)").matcher(css).results()
                .map(m -> m.group(1)).distinct().toList();

        assertThat(fonts).hasSize(10).contains("fonts/atkinson-hyperlegible-latin-400-normal.woff2");
        for (String font : fonts) {
            given().get("/reader/" + font).then().statusCode(200).contentType("font/woff2");
        }
        for (String licence : List.of("OFL-AtkinsonHyperlegible.txt", "OFL-PlayfairDisplay.txt", "OFL-Andika.txt")) {
            assertThat(given().get("/reader/fonts/" + licence).then().statusCode(200).extract().asString())
                    .contains("SIL OPEN FONT LICENSE Version 1.1");
        }
    }

    private static Response postTextSize(String size, String next) {
        return given().redirects().follow(false).formParam("size", size).formParam("next", next).post("/text-size");
    }

    private void execute(String sql) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
