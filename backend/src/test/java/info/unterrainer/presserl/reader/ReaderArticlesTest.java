package info.unterrainer.presserl.reader;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import info.unterrainer.presserl.TestSupport;


import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;

/**
 * Front page and article page with published articles, in German (no {@code Accept-Language}).
 */
@QuarkusTest
class ReaderArticlesTest {

    private static final Instant SEPT_20 = Instant.parse("2026-09-20T12:00:00Z");
    private static final Instant SEPT_25 = Instant.parse("2026-09-25T12:00:00Z");
    private static final String ALL_BLOCKS = """
            {"version": 1, "blocks": [
              {"type": "paragraph", "content": [{"text": "It started "}, {"text": "in May", "bold": true}, {"text": "."}]},
              {"type": "subhead", "text": "Watering"},
              {"type": "quote", "content": [{"text": "Every day!"}]},
              {"type": "list", "items": [[{"text": "Water"}], [{"text": "Sun"}]]}]}""";

    @Inject
    DataSource dataSource;

    @AfterEach
    void resetIssues() {
        TestSupport.resetIssues(dataSource);
    }

    private ReaderFixtures fixtures;

    @BeforeEach
    void emptyNewspaper() {
        fixtures = new ReaderFixtures(dataSource);
        fixtures.deleteAllArticles();
    }

    private static String page(String path, int status) {
        return given().get(path).then().statusCode(status).extract().asString();
    }

    // --- front page

    @Test
    void newestPublicationLeads() {
        long older = fixtures.published("Older story", SEPT_20);
        long newer = fixtures.published("Newer story", SEPT_25);

        String html = page("/", 200);

        assertThat(html).contains("<article class=\"lead-article\">");
        assertThat(html.indexOf("Newer story")).isLessThan(html.indexOf("Older story"));
        assertThat(html.indexOf("lead-article")).isLessThan(html.indexOf("Newer story"));
        assertThat(html).contains("href=\"/articles/" + newer + "\"", "href=\"/articles/" + older + "\"");
    }

    @Test
    void cardShowsKickerHeadlineLeadBylineAndDate() {
        long id = fixtures.article("PUBLISHED", "anna", "Anna Bauer", 1, SEPT_20);
        fixtures.revision(id, 1, "Garden", "Tomatoes", "A good year", "Harvest is early.", ReaderFixtures.EMPTY_BODY,
                SEPT_20);

        assertThat(page("/", 200)).contains(
                "<p class=\"kicker\">Garden</p>",
                "<a href=\"/articles/" + id + "\">Tomatoes</a>",
                "<p class=\"lead\">Harvest is early.</p>",
                "Von Anna Bauer",
                "<time datetime=\"2026-09-20\">20. September 2026</time>");
    }

    @Test
    void sameTimeOrdersByHigherId() {
        long first = fixtures.published("First id", SEPT_20);
        long second = fixtures.published("Second id", SEPT_20);

        String html = page("/", 200);

        assertThat(second).isGreaterThan(first);
        assertThat(html.indexOf("Second id")).isLessThan(html.indexOf("First id"));
    }

    @Test
    void draftAndOfflineArticlesAreHidden() {
        fixtures.published("Visible", SEPT_20);
        long offline = fixtures.article("OFFLINE", "anna", "Anna", 1, SEPT_20);
        fixtures.revision(offline, 1, "", "Gone offline", "", "", ReaderFixtures.EMPTY_BODY, SEPT_20);
        long draft = fixtures.article("DRAFT", "anna", "Anna", null, null);
        fixtures.revision(draft, 1, "", "Still a draft", "", "", ReaderFixtures.EMPTY_BODY, null);

        assertThat(page("/", 200)).contains("Visible").doesNotContain("Gone offline", "Still a draft");
    }

    @Test
    void unpublishedChangesDoNotLeakToTheFrontPage() {
        long id = fixtures.published("Live headline", SEPT_20);
        fixtures.revision(id, 2, "", "Unpublished headline", "", "", ReaderFixtures.EMPTY_BODY, null);

        assertThat(page("/", 200)).contains(">Live headline</a>").doesNotContain("Unpublished headline");
    }

    @Test
    void emptyNewspaperShowsNote() {
        assertThat(page("/", 200))
                .contains("<h1 class=\"masthead__name\">My Newspaper</h1>")
                .contains("Noch keine Artikel veröffentlicht.")
                .doesNotContain("lead-article", "article-card");
    }

    @Test
    void frontPageListsThe30MostRecentlyPublished() {
        for (int i = 1; i <= 31; i++) {
            fixtures.published("Story %02d".formatted(i), SEPT_20.plus(i, ChronoUnit.HOURS));
        }

        String html = page("/", 200);

        assertThat(html.split("<article class=\"(lead-article|article-card)\">").length - 1).isEqualTo(30);
        assertThat(html).contains(">Story 31<", ">Story 02<").doesNotContain(">Story 01<");
    }

    // --- article page

    @Test
    void articlePageShowsTheLiveRevision() {
        long id = fixtures.article("PUBLISHED", "anna", "Anna", 1, SEPT_20);
        fixtures.revision(id, 1, "Garden", "Hello", "A subheadline", "The lead.", ALL_BLOCKS, SEPT_20);

        Response response = given().get("/articles/" + id);

        response.then().statusCode(200)
                .header("Content-Type", "text/html;charset=UTF-8")
                .header("Vary", "Accept-Language, Cookie");
        assertThat(response.asString()).contains(
                "<main data-view=\"article\">",
                "<title>Hello – My Newspaper</title>",
                "<p class=\"masthead__name\"><a href=\"/\">My Newspaper</a></p>",
                "<p class=\"kicker\">Garden</p>",
                "<h1 class=\"headline\">Hello</h1>",
                "<p class=\"subheadline\">A subheadline</p>",
                "<p class=\"lead\">The lead.</p>",
                "Von Anna",
                "Veröffentlicht am 20. September 2026")
                .doesNotContain("Aktualisiert");
        assertThat(response.asString().split("<h1").length - 1).isEqualTo(1);
    }

    @Test
    void bylineFallsBackToUsername() {
        long id = fixtures.article("PUBLISHED", "anna", "", 1, SEPT_20);
        fixtures.revision(id, 1, "", "Hello", "", "", ReaderFixtures.EMPTY_BODY, SEPT_20);

        assertThat(page("/articles/" + id, 200)).contains("Von anna");
        assertThat(page("/", 200)).contains("Von anna");
    }

    @Test
    void republishedArticleShowsNewContentAndUpdateDate() {
        long id = fixtures.article("PUBLISHED", "anna", "Anna", 2, SEPT_20);
        fixtures.revision(id, 1, "", "First version", "", "", ReaderFixtures.EMPTY_BODY, SEPT_20);
        fixtures.revision(id, 2, "", "Second version", "", "", ReaderFixtures.EMPTY_BODY, SEPT_25);

        assertThat(page("/articles/" + id, 200))
                .contains("Second version",
                        "<time datetime=\"2026-09-20\">Veröffentlicht am 20. September 2026</time>",
                        "<time datetime=\"2026-09-25\">Aktualisiert am 25. September 2026</time>")
                .doesNotContain("First version");
    }

    @Test
    void sameDayRepublicationShowsNoUpdate() {
        long id = fixtures.article("PUBLISHED", "anna", "Anna", 2, SEPT_20);
        fixtures.revision(id, 1, "", "First version", "", "", ReaderFixtures.EMPTY_BODY, SEPT_20);
        fixtures.revision(id, 2, "", "Second version", "", "", ReaderFixtures.EMPTY_BODY,
                SEPT_20.plus(2, ChronoUnit.HOURS));

        assertThat(page("/articles/" + id, 200)).contains("Second version").doesNotContain("Aktualisiert");
    }

    @Test
    void unpublishedChangesStayHiddenOnTheArticlePage() {
        long id = fixtures.published("Live headline", SEPT_20);
        fixtures.revision(id, 2, "", "Unpublished headline", "", "", ALL_BLOCKS, null);

        assertThat(page("/articles/" + id, 200)).contains("Live headline")
                .doesNotContain("Unpublished headline", "Watering");
    }

    // --- body rendering and escaping

    @Test
    void allBlockTypesMapToFixedHtml() {
        long id = fixtures.article("PUBLISHED", "anna", "Anna", 1, SEPT_20);
        fixtures.revision(id, 1, "", "Blocks", "", "", ALL_BLOCKS, SEPT_20);

        String html = page("/articles/" + id, 200);

        int paragraph = html.indexOf("<p>It started <strong>in May</strong>.</p>");
        int subhead = html.indexOf("<h2>Watering</h2>");
        int quote = html.indexOf("<blockquote><p>Every day!</p></blockquote>");
        int list = html.indexOf("<ul>", html.indexOf("class=\"article__body\""));
        assertThat(paragraph).isPositive();
        assertThat(subhead).isGreaterThan(paragraph);
        assertThat(quote).isGreaterThan(subhead);
        assertThat(list).isGreaterThan(quote);
        assertThat(html.substring(list, html.indexOf("</ul>", list)))
                .contains("<li>Water</li>", "<li>Sun</li>")
                .satisfies(ul -> assertThat(ul.split("<li>").length - 1).isEqualTo(2));
    }

    @Test
    void lineFeedBecomesLineBreak() {
        long id = fixtures.article("PUBLISHED", "anna", "Anna", 1, SEPT_20);
        fixtures.revision(id, 1, "", "Lines", "", "",
                "{\"version\": 1, \"blocks\": [{\"type\": \"paragraph\", \"content\": [{\"text\": \"one\\ntwo\"}]}]}",
                SEPT_20);

        assertThat(page("/articles/" + id, 200)).contains("<p>one<br>two</p>");
    }

    @Test
    void markupIsShownAsText() {
        long id = fixtures.article("PUBLISHED", "anna", "<i>Anna</i>", 1, SEPT_20);
        fixtures.revision(id, 1, "<em>k</em>", "<b>Hi</b>", "<u>s</u>", "<a href=x>l</a>",
                "{\"version\": 1, \"blocks\": [{\"type\": \"paragraph\", \"content\": "
                        + "[{\"text\": \"<script>alert(1)</script>\"}]}, {\"type\": \"subhead\", \"text\": \"<h1>x</h1>\"}]}",
                SEPT_20);

        String article = page("/articles/" + id, 200);
        String front = page("/", 200);

        assertThat(article).contains("&lt;b&gt;Hi&lt;/b&gt;", "&lt;script&gt;alert(1)&lt;/script&gt;",
                "&lt;em&gt;k&lt;/em&gt;", "&lt;u&gt;s&lt;/u&gt;", "&lt;a href=x&gt;l&lt;/a&gt;",
                "&lt;i&gt;Anna&lt;/i&gt;", "<h2>&lt;h1&gt;x&lt;/h1&gt;</h2>")
                .doesNotContain("<script", "<b>Hi", "<em>k", "<i>Anna");
        assertThat(front).contains("&lt;b&gt;Hi&lt;/b&gt;").doesNotContain("<b>Hi", "<script");
    }

    // --- not found

    @Test
    void unpublishedUnknownAndMalformedIdsGetTheSame404() {
        long draft = fixtures.article("DRAFT", "anna", "Anna", null, null);
        fixtures.revision(draft, 1, "", "Secret draft", "", "", ReaderFixtures.EMPTY_BODY, null);
        long submitted = fixtures.article("SUBMITTED", "anna", "Anna", null, null);
        fixtures.revision(submitted, 1, "", "Secret submission", "", "", ReaderFixtures.EMPTY_BODY, null);
        long offline = fixtures.article("OFFLINE", "anna", "Anna", 1, SEPT_20);
        fixtures.revision(offline, 1, "", "Secret offline", "", "", ALL_BLOCKS, SEPT_20);

        String draftPage = page("/articles/" + draft, 404);

        assertThat(draftPage).contains("<main data-view=\"not-found\">", "Seite nicht gefunden",
                "<a href=\"/\">Zur Titelseite</a>", "My Newspaper").doesNotContain("Secret");
        for (String path : new String[] { "/articles/" + submitted, "/articles/" + offline, "/articles/999999",
                "/articles/abc", "/articles/-1", "/articles/99999999999999999999" }) {
            given().get(path).then().statusCode(404)
                    .header("Content-Type", "text/html;charset=UTF-8")
                    .body(not(containsString("Secret")))
                    .body(not(containsString("Watering")));
            // only the text-size switch's return path differs
            assertThat(withoutReturnPath(page(path, 404))).as(path).isEqualTo(withoutReturnPath(draftPage));
        }
    }

    private static String withoutReturnPath(String html) {
        return html.replaceAll("name=\"next\" value=\"[^\"]*\"", "name=\"next\" value=\"\"");
    }
}
