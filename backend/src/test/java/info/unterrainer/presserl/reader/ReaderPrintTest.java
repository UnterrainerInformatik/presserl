package info.unterrainer.presserl.reader;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import info.unterrainer.presserl.TestSupport;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;

/**
 * Print views of an article and an issue on a public newspaper, the links to them and their
 * stylesheet rules. Afterwards only issue 1 exists again.
 */
@QuarkusTest
class ReaderPrintTest {

    private static final Instant MONDAY = Instant.parse("2026-09-21T09:00:00Z");
    private static final String BODY = """
            {"version": 1, "blocks": [{"type": "paragraph", "content": [{"text": "Body text of the story."}]}]}""";

    @Inject
    DataSource dataSource;

    private ReaderFixtures fixtures;

    @BeforeEach
    void emptyNewspaper() {
        TestSupport.awaitReady();
        TestSupport.deleteSections(dataSource);
        fixtures = new ReaderFixtures(dataSource);
        fixtures.deleteAllArticlesAndMedia();
        fixtures.deleteAllIssues();
    }

    @AfterEach
    void cleanUp() {
        fixtures.deleteAllArticlesAndMedia();
        TestSupport.deleteSections(dataSource);
        TestSupport.resetIssues(dataSource);
    }

    private static Response get(String path) {
        return given().redirects().follow(false).header("Accept-Language", "de").get(path);
    }

    /**
     * A published article in {@code section} with kicker, subheadline, lead and body.
     */
    private long story(long section, String headline) {
        long id = fixtures.publishedIn(section, headline + " (pending)", MONDAY);
        execute("DELETE FROM article_revision WHERE article_id = " + id);
        fixtures.revision(id, 1, "Kicker " + headline, headline, "Sub " + headline, "Lead " + headline, BODY, MONDAY);
        return id;
    }

    private void execute(String sql) {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    // --- article print view

    @Test
    void printPublishedArticleWithLeadImage() {
        long sport = fixtures.section("Sport", "red", 0);
        long article = story(sport, "Match");
        long cat = ReaderMedia.upload(3200, 2400);
        fixtures.leadImage(article, 1, cat, "The winner");

        Response response = get("/print/article/" + article);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.header("Content-Security-Policy")).startsWith("default-src 'self';");
        String html = response.asString();
        assertThat(html).contains("<main data-view=\"print-article\">",
                "<p class=\"masthead__name\">My Newspaper</p>",
                "<p class=\"print-section-header\" data-section-color=\"red\">Sport</p>",
                "Kicker Match", "<h1 class=\"headline\">Match</h1>", "Sub Match", "Lead Match",
                "Body text of the story.", "Von Anna", "21. September 2026",
                "<img src=\"/media/%d/print\" width=\"3000\" height=\"2250\" alt=\"\">".formatted(cat),
                "The winner",
                "<button type=\"button\" data-print hidden>Drucken</button>",
                "<a href=\"/articles/%d\">Zurück zum Artikel</a>".formatted(article),
                "<script src=\"/reader/print.js\" defer></script>")
                .doesNotContain("masthead__tools", "text-size-switch", "section-bar", "srcset", "<script>");
    }

    @Test
    void unpublishedChangesDoNotLeak() {
        long article = fixtures.published("Live headline", MONDAY);
        fixtures.revision(article, 2, "", "Working headline", "", "", ReaderFixtures.EMPTY_BODY, null);

        assertThat(get("/print/article/" + article).asString()).contains("Live headline")
                .doesNotContain("Working headline");
    }

    @Test
    void draftUnknownAndMalformedHaveNoPrintView() {
        long draft = fixtures.article("DRAFT", "anna", "Anna", null, null);
        fixtures.revision(draft, 1, "", "Draft", "", "", ReaderFixtures.EMPTY_BODY, null);

        assertThat(get("/print/article/" + draft).statusCode()).isEqualTo(404);
        assertThat(get("/print/article/999999").statusCode()).isEqualTo(404);
        assertThat(get("/print/article/abc").statusCode()).isEqualTo(404);
    }

    @Test
    void articlePageLinksItsPrintView() {
        long article = fixtures.published("Wall", MONDAY);

        assertThat(get("/articles/" + article).asString())
                .contains("<p class=\"print-link\"><a href=\"/print/article/%d\">Drucken</a></p>".formatted(article));
        assertThat(given().header("Accept-Language", "en").get("/articles/" + article).asString())
                .contains("<a href=\"/print/article/%d\">Print</a>".formatted(article));
    }

    // --- issue print view

    @Test
    void wholeIssueInOrderWithSectionHeaders() {
        long sport = fixtures.section("Sport", "red", 0);
        long kultur = fixtures.section("Kultur", "blue", 1);
        long b = story(sport, "Bravo");
        long a = story(kultur, "Alpha");
        long c = story(sport, "Charlie");
        long two = fixtures.issue(2, true, LocalDate.of(2026, 10, 12));
        fixtures.inIssue(two, b, a, c);

        Response response = get("/print/issue/" + two);

        assertThat(response.statusCode()).isEqualTo(200);
        String html = response.asString();
        assertThat(html).contains("<main data-view=\"print-issue\">", "<h1 class=\"masthead__name\">My Newspaper</h1>",
                "Ausgabe 2 · <time datetime=\"2026-10-12\">12. Oktober 2026</time>",
                "<a href=\"/issues/%d\">Zurück zur Ausgabe</a>".formatted(two));
        String front = html.substring(html.indexOf("class=\"print-issue__front\""), html.indexOf("class=\"print-columns\""));
        assertThat(front).contains("<h2 class=\"headline\">Bravo</h2>", "Body text of the story.")
                .doesNotContain("Alpha", "Charlie");
        String columns = html.substring(html.indexOf("class=\"print-columns\""));
        int kulturHeader = columns.indexOf("data-section-color=\"blue\">Kultur</p>");
        int sportHeader = columns.indexOf("data-section-color=\"red\">Sport</p>");
        assertThat(kulturHeader).isNotNegative().isLessThan(columns.indexOf("Alpha"));
        assertThat(columns.indexOf("Alpha")).isLessThan(sportHeader);
        assertThat(sportHeader).isLessThan(columns.indexOf("Charlie"));
    }

    @Test
    void unpublishedArticlesAreLeftOutOfTheIssue() {
        long sport = fixtures.section("Sport", "red", 0);
        long x = story(sport, "Xray");
        fixtures.status(x, "OFFLINE");
        long y = story(sport, "Yankee");
        long two = fixtures.issue(2, true, null);
        fixtures.inIssue(two, x, y);

        String html = get("/print/issue/" + two).asString();

        assertThat(html).contains("Yankee").doesNotContain("Xray", "print-columns");
    }

    @Test
    void issueWithoutPublishedArticlesShowsMastheadAndNote() {
        long one = fixtures.issue(1, true, null);

        assertThat(get("/print/issue/" + one).asString())
                .contains("Ausgabe 1", "Diese Ausgabe enthält noch keine Artikel.")
                .doesNotContain("print-article", "print-columns");
    }

    @Test
    void issueNotLiveHasNoPrintView() {
        long four = fixtures.issue(4, false, null);

        assertThat(get("/print/issue/" + four).statusCode()).isEqualTo(404);
        assertThat(get("/print/issue/999999").statusCode()).isEqualTo(404);
        assertThat(get("/print/issue/x").statusCode()).isEqualTo(404);
    }

    // --- styles and script

    @Test
    void stylesheetHasTheA4PageAndPrintRules() {
        String css = get("/reader/reader.css").asString();

        assertThat(css).contains("@page", "size: A4 portrait", "@bottom-center", "content: counter(page)",
                "@media print", "break-after: page", "break-inside: avoid",
                "[data-view=\"print-issue\"] {\n  --presserl-grid-columns: 2;",
                "column-count: var(--presserl-grid-columns)");
    }

    @Test
    void printScriptIsServedFromTheReader() {
        Response response = get("/reader/print.js");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.header("Content-Security-Policy")).startsWith("default-src 'self';");
        assertThat(response.asString()).contains("[data-print]", "window.print()");
    }
}
