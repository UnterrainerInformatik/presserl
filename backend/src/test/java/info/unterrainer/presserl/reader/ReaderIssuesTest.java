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
 * Issue page, issue archive and the issue line of the front page masthead on a public newspaper.
 * Articles and issues are written straight into the database; afterwards only issue 1 exists again.
 */
@QuarkusTest
class ReaderIssuesTest {

    private static final Instant MONDAY = Instant.parse("2026-09-21T09:00:00Z");

    @Inject
    DataSource dataSource;

    private ReaderFixtures fixtures;

    @BeforeEach
    void emptyNewspaper() {
        TestSupport.awaitReady();
        TestSupport.deleteSections(dataSource);
        fixtures = ReaderFixtures.withoutIssue(dataSource);
        fixtures.deleteAllIssues();
    }

    @AfterEach
    void cleanUp() {
        TestSupport.deleteSections(dataSource);
        TestSupport.resetIssues(dataSource);
    }

    private static Response get(String path) {
        return given().redirects().follow(false).header("Accept-Language", "de").get(path);
    }

    private long published(String headline) {
        return fixtures.published(headline, MONDAY);
    }

    // --- issue page

    @Test
    void issueShowsItsArticlesInIssueOrder() {
        long b = published("Bravo");
        long a = published("Alpha");
        long c = published("Charlie");
        long two = fixtures.issue(2, true, null);
        fixtures.inIssue(two, b, a, c);

        Response response = get("/issues/" + two);

        assertThat(response.statusCode()).isEqualTo(200);
        String html = response.asString();
        assertThat(html).contains("<main data-view=\"issue\">", "<p class=\"masthead__name\"><a href=\"/\">",
                "<p class=\"masthead__issue\">Ausgabe 2</p>", "<a href=\"/print/issue/%d\">Drucken</a>".formatted(two));
        String lead = html.substring(html.indexOf("class=\"lead-article\""), html.indexOf("class=\"article-card\""));
        assertThat(lead).contains("Bravo");
        assertThat(html.indexOf("Alpha")).isGreaterThan(html.indexOf("class=\"article-card\""))
                .isLessThan(html.indexOf("Charlie"));
        assertThat(response.header("Cache-Control")).isNull();
    }

    @Test
    void unpublishedArticlesAreLeftOut() {
        long x = published("Xray");
        fixtures.status(x, "OFFLINE");
        long y = published("Yankee");
        long two = fixtures.issue(2, true, null);
        fixtures.inIssue(two, x, y);

        String html = get("/issues/" + two).asString();

        assertThat(html.substring(html.indexOf("class=\"lead-article\""))).contains("Yankee");
        assertThat(html).doesNotContain("Xray", "article-card");
    }

    @Test
    void issueWithoutPublishedArticlesShowsANote() {
        long one = fixtures.issue(1, true, null);

        assertThat(get("/issues/" + one).asString()).contains("Diese Ausgabe enthält noch keine Artikel.")
                .doesNotContain("lead-article");
    }

    @Test
    void issueNotLiveIsNotFound() {
        long article = published("Hidden issue");
        long four = fixtures.issue(4, false, null);
        fixtures.inIssue(four, article);

        Response response = get("/issues/" + four);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.asString()).contains("Seite nicht gefunden").doesNotContain("Ausgabe 4");
    }

    @Test
    void unknownAndMalformedIdsAreNotFound() {
        assertThat(get("/issues/999999").statusCode()).isEqualTo(404);
        assertThat(get("/issues/abc").statusCode()).isEqualTo(404);
    }

    @Test
    void issueWithPublicationDate() {
        long three = fixtures.issue(3, true, LocalDate.of(2026, 10, 12));

        assertThat(get("/issues/" + three).asString()).contains(
                "<p class=\"masthead__issue\">Ausgabe 3 · <time datetime=\"2026-10-12\">12. Oktober 2026</time></p>");
        assertThat(given().header("Accept-Language", "en").get("/issues/" + three).asString())
                .contains("Issue 3 · <time datetime=\"2026-10-12\">October 12, 2026</time>");
    }

    @Test
    void issuePageLinksTheArchiveWhenSeveralAreLive() {
        fixtures.issue(1, true, null);
        long two = fixtures.issue(2, true, null);

        assertThat(get("/issues/" + two).asString()).contains("<a href=\"/issues\">Alle Ausgaben</a>");
    }

    // --- archive

    @Test
    void archiveListsLiveIssuesOnlyHighestFirst() {
        long alpha = published("Alpha");
        long bravo = published("Bravo");
        fixtures.status(bravo, "OFFLINE");
        long charlie = published("Charlie");
        long one = fixtures.issue(1, true, LocalDate.of(2026, 9, 1));
        long two = fixtures.issue(2, true, null);
        long three = fixtures.issue(3, false, null);
        fixtures.inIssue(one, alpha);
        fixtures.inIssue(two, bravo, charlie);

        Response response = get("/issues");

        assertThat(response.statusCode()).isEqualTo(200);
        String html = response.asString();
        assertThat(html).contains("<main data-view=\"issues\">", "href=\"/issues/%d\"".formatted(one),
                "1. September 2026")
                .doesNotContain("href=\"/issues/%d\"".formatted(three), "Ausgabe 3", "Bravo");
        assertThat(html.indexOf("href=\"/issues/%d\"".formatted(two)))
                .isLessThan(html.indexOf("href=\"/issues/%d\"".formatted(one)));
        // the headline of the first published article of each issue
        assertThat(html.indexOf("Charlie")).isLessThan(html.indexOf("Alpha"));
    }

    @Test
    void issueViewsCarryTheDocumentedHooks() {
        long alpha = published("Alpha");
        long two = fixtures.issue(2, true, null);
        fixtures.inIssue(two, alpha);

        String archive = get("/issues").asString();
        String issue = get("/issues/" + two).asString();

        assertThat(archive).contains("<main data-view=\"issues\">", "<ul class=\"issue-list\">",
                "<li class=\"issue-list__item\">", "<a class=\"issue-list__label\" href=\"/issues/%d\">".formatted(two),
                "<p class=\"issue-list__headline\">Alpha</p>");
        assertThat(issue).contains("<main data-view=\"issue\">", "<p class=\"masthead__issue\">",
                "<div class=\"stories\">");
    }

    @Test
    void emptyArchiveShowsANote() {
        fixtures.issue(1, false, null);

        assertThat(get("/issues").asString()).contains("Noch keine Ausgaben erschienen.");
    }

    // --- front page masthead

    @Test
    void frontPageNamesTheOnlyLiveIssueInBlogMode() {
        long one = fixtures.issue(1, true, null);
        published("Blog post");

        String html = get("/").asString();

        assertThat(html).contains("<p class=\"masthead__issue\"><a href=\"/issues/%d\">Ausgabe 1</a></p>".formatted(one))
                .doesNotContain("href=\"/issues\"");
    }

    @Test
    void frontPageNamesTheNewestLiveIssueAndLinksTheArchive() {
        fixtures.issue(1, true, null);
        fixtures.issue(2, true, null);
        long three = fixtures.issue(3, true, null);
        fixtures.issue(4, false, null);

        String html = get("/").asString();

        assertThat(html).contains("<a href=\"/issues/%d\">Ausgabe 3</a>".formatted(three),
                "<a href=\"/issues\">Alle Ausgaben</a>")
                .doesNotContain("Ausgabe 4");
    }

    @Test
    void frontPageWithoutLiveIssueHasNoIssueLine() {
        fixtures.issue(1, false, null);
        published("Anything");

        assertThat(get("/").asString()).doesNotContain("masthead__issue", "href=\"/issues");
    }

    @Test
    void frontPageListsOnlyArticlesOfLiveIssues() {
        long older = published("Older");
        long newer = fixtures.published("Newer", MONDAY.plusSeconds(3600));
        long one = fixtures.issue(1, true, null);
        fixtures.inIssue(one, newer, older);
        published("Without issue");

        String html = get("/").asString();

        assertThat(html).doesNotContain("Without issue");
        assertThat(html.indexOf("Newer")).isPositive().isLessThan(html.indexOf("Older"));
    }
}
