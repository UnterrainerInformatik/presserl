package info.unterrainer.presserl.reader;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import info.unterrainer.presserl.TestSupport;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;

/**
 * Visibility follows the issue, the weighted front-page order and the section filter, on a public
 * newspaper. Articles, media and issues are written straight into the database; afterwards only issue 1
 * (not live) exists again.
 */
@QuarkusTest
class ReaderFrontPageTest {

    private static final Instant MONDAY = Instant.parse("2026-09-21T09:00:00Z");

    @Inject
    DataSource dataSource;

    private ReaderFixtures fixtures;
    private long live;

    @BeforeEach
    void emptyNewspaper() {
        TestSupport.awaitReady();
        TestSupport.deleteSections(dataSource);
        fixtures = ReaderFixtures.withoutIssue(dataSource);
        fixtures.deleteAllArticlesAndMedia();
        fixtures.deleteAllIssues();
        live = fixtures.issue(1, true, null);
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

    private long published(String headline, Instant at, long issueId) {
        long id = fixtures.published(headline, at);
        fixtures.appendTo(issueId, id);
        return id;
    }

    private long publishedIn(long sectionId, String headline, Instant at, long issueId) {
        long id = fixtures.publishedIn(sectionId, headline, at);
        fixtures.appendTo(issueId, id);
        return id;
    }

    private static String lead(String html) {
        String lead = html.substring(html.indexOf("<article class=\"lead-article\">"));
        return lead.substring(0, lead.indexOf("</article>"));
    }

    private static List<String> order(String html, String... headlines) {
        return Arrays.stream(headlines).filter(html::contains)
                .sorted(Comparator.comparingInt(html::indexOf)).toList();
    }

    // --- visibility follows the issue

    @Test
    void plannedIssueHidesItsArticlesEverywhereUntilItGoesLive() {
        long planned = fixtures.issue(2, false, null);
        long cat = ReaderMedia.upload(1600, 1200);
        long id = published("Waiting story", MONDAY, planned);
        fixtures.leadImage(id, 1, cat, "");
        String[] paths = { "/articles/" + id, "/print/article/" + id };

        assertThat(get("/").asString()).doesNotContain("Waiting story");
        for (String path : paths) {
            assertThat(get(path).statusCode()).as(path).isEqualTo(404);
            assertThat(get(path).asString()).as(path).doesNotContain("Waiting story");
        }
        assertThat(get("/media/%d/web".formatted(cat)).statusCode()).isEqualTo(404);

        fixtures.publishIssue(planned, true);

        assertThat(get("/").asString()).contains("Waiting story");
        for (String path : paths) {
            assertThat(get(path).statusCode()).as(path).isEqualTo(200);
            assertThat(get(path).asString()).as(path).contains("Waiting story");
        }
        assertThat(get("/media/%d/web".formatted(cat)).statusCode()).isEqualTo(200);

        fixtures.publishIssue(planned, false);

        assertThat(get("/").asString()).doesNotContain("Waiting story");
        assertThat(get("/articles/" + id).statusCode()).isEqualTo(404);
        assertThat(get("/media/%d/web".formatted(cat)).statusCode()).isEqualTo(404);
    }

    @Test
    void articleWithoutIssueIsHidden() {
        long id = fixtures.published("Lost story", MONDAY);

        assertThat(get("/").asString()).doesNotContain("Lost story");
        assertThat(get("/articles/" + id).statusCode()).isEqualTo(404);
    }

    @Test
    void blogModeShowsANewArticleAtOnce() {
        String publisher = TestSupport.token("publisher", "publisher");
        long id = given().auth().oauth2(publisher).contentType(ContentType.JSON).body("{\"headline\": \"Blog entry\"}")
                .post("/api/articles").then().statusCode(201).extract().jsonPath().getLong("id");

        given().auth().oauth2(publisher).post("/api/articles/%d/publish".formatted(id)).then().statusCode(200);

        assertThat(get("/").asString()).contains("Blog entry");
        assertThat(get("/articles/" + id).statusCode()).isEqualTo(200);
    }

    // --- weighted order

    @Test
    void weightedArticlesLeadLowestWeightFirst() {
        long a = published("Alpha", MONDAY, live);
        long b = published("Bravo", MONDAY.plusSeconds(60), live);
        published("Charlie", MONDAY.plusSeconds(120), live);
        fixtures.weight(a, 1);
        fixtures.weight(b, 2);

        String html = get("/").asString();

        assertThat(lead(html)).contains("Alpha");
        assertThat(order(html, "Alpha", "Bravo", "Charlie")).containsExactly("Alpha", "Bravo", "Charlie");
    }

    @Test
    void equalWeightsFallBackToNewestFirst() {
        long a = published("Alpha", MONDAY, live);
        long b = published("Bravo", MONDAY.plusSeconds(60), live);
        fixtures.weight(a, 1);
        fixtures.weight(b, 1);

        assertThat(order(get("/").asString(), "Alpha", "Bravo")).containsExactly("Bravo", "Alpha");
    }

    @Test
    void weightedArticleBeyondTheNewest30IsListedFirst() {
        long oldest = published("Story 00", MONDAY, live);
        for (int i = 1; i <= 30; i++) {
            published("Story %02d".formatted(i), MONDAY.plus(i, ChronoUnit.HOURS), live);
        }
        fixtures.weight(oldest, 5);

        String html = get("/").asString();

        assertThat(lead(html)).contains("Story 00");
        assertThat(html).contains("Story 30", "Story 02").doesNotContain("Story 01");
    }

    @Test
    void weightOfOfflineOrWaitingArticleHasNoEffect() {
        long planned = fixtures.issue(2, false, null);
        long offline = published("Offline story", MONDAY, live);
        fixtures.status(offline, "OFFLINE");
        fixtures.weight(offline, 1);
        long waiting = published("Waiting story", MONDAY, planned);
        fixtures.weight(waiting, 1);
        published("Older", MONDAY, live);
        published("Newer", MONDAY.plusSeconds(60), live);

        String html = get("/").asString();

        assertThat(html).doesNotContain("Offline story", "Waiting story");
        assertThat(lead(html)).contains("Newer");
        assertThat(order(html, "Older", "Newer")).containsExactly("Newer", "Older");
    }

    // --- section filter

    @Test
    void sectionFilterListsOnlyThatSectionWithItsWeights() {
        long sport = fixtures.section("Sport", "blue", 0);
        long kultur = fixtures.section("Kultur", "red", 1);
        long a = publishedIn(sport, "Sport A", MONDAY.plusSeconds(60), live);
        long b = publishedIn(sport, "Sport B", MONDAY, live);
        publishedIn(kultur, "Kultur C", MONDAY.plusSeconds(120), live);
        fixtures.weight(b, 3);

        Response response = get("/?section=" + sport);

        assertThat(response.statusCode()).isEqualTo(200);
        String html = response.asString();
        assertThat(html).contains("Sport A", "Sport B").doesNotContain("Kultur C");
        assertThat(lead(html)).contains("Sport B").doesNotContain("Sport A");
        assertThat(html).contains("href=\"/articles/%d\"".formatted(a));
    }

    @Test
    void activeSectionTagsLinkBackAndOthersToTheirFilter() {
        long sport = fixtures.section("Sport", "blue", 0);
        long kultur = fixtures.section("Kultur", "red", 1);
        publishedIn(sport, "Sport A", MONDAY, live);

        String html = get("/?section=" + sport).asString();

        String active = "<a class=\"section-tag\" href=\"/\" aria-current=\"page\" data-section-color=\"blue\">Sport</a>";
        String bar = html.substring(html.indexOf("<nav class=\"section-bar\""), html.indexOf("</nav>"));
        assertThat(bar).contains(active, "<a class=\"section-tag\" href=\"/?section=%d\" data-section-color=\"red\">Kultur</a>"
                .formatted(kultur));
        assertThat(lead(html)).contains(active);
        assertThat(get("/").asString()).doesNotContain("aria-current=\"page\" data-section-color");
    }

    @Test
    void waitingArticlesStayHiddenInTheFilter() {
        long sport = fixtures.section("Sport", "blue", 0);
        long planned = fixtures.issue(2, false, null);
        publishedIn(sport, "Sport waiting", MONDAY, planned);

        String html = get("/?section=" + sport).asString();

        assertThat(html).doesNotContain("Sport waiting").contains("In „Sport“ gibt es noch keine Artikel.");
    }

    @Test
    void emptySectionShowsItsNote() {
        fixtures.section("Sport", "blue", 0);
        long kultur = fixtures.section("Kultur", "red", 1);

        Response german = get("/?section=" + kultur);
        Response english = given().header("Accept-Language", "en").get("/?section=" + kultur);

        assertThat(german.statusCode()).isEqualTo(200);
        assertThat(german.asString()).contains("In „Kultur“ gibt es noch keine Artikel.");
        assertThat(english.asString()).contains("There are no articles in “Kultur” yet.");
    }

    @Test
    void unknownOrMalformedSectionIsNotFound() {
        fixtures.section("Sport", "blue", 0);

        for (String query : List.of("999999", "abc", "", "-1", "1e3")) {
            Response response = get("/?section=" + query);
            assertThat(response.statusCode()).as(query).isEqualTo(404);
            assertThat(response.asString()).as(query).contains("<main data-view=\"not-found\">");
        }
    }

    @Test
    void otherQueryParametersAreIgnored() {
        published("Alpha", MONDAY, live);

        Response response = get("/?utm_source=x");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asString()).contains("Alpha");
    }
}
