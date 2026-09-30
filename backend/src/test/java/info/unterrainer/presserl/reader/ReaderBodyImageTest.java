package info.unterrainer.presserl.reader;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import info.unterrainer.presserl.TestSupport;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;

/**
 * Image blocks in article bodies for readers of a public newspaper: the reader route, the figures on
 * the article page and in both print views.
 */
@QuarkusTest
class ReaderBodyImageTest {

    private static final Instant MONDAY = Instant.parse("2026-09-21T12:00:00Z");

    @Inject
    DataSource dataSource;

    private ReaderFixtures fixtures;
    private long cat;

    @BeforeEach
    void media() {
        TestSupport.awaitReady();
        fixtures = new ReaderFixtures(dataSource);
        fixtures.deleteAllArticlesAndMedia();
        cat = ReaderMedia.upload(3200, 2400);
    }

    @AfterEach
    void cleanUp() {
        fixtures.deleteAllArticlesAndMedia();
        TestSupport.resetIssues(dataSource);
    }

    private static Response get(String path) {
        return given().redirects().follow(false).get(path);
    }

    private static void assertEmptyNotFound(String path) {
        Response response = get(path);
        assertThat(response.statusCode()).as(path).isEqualTo(404);
        assertThat(response.asByteArray()).as(path).isEmpty();
    }

    private static String paragraph(String text) {
        return "{\"type\": \"paragraph\", \"content\": [{\"text\": \"" + text + "\"}]}";
    }

    private static String image(long mediaId, String caption) {
        return caption == null ? "{\"type\": \"image\", \"mediaId\": " + mediaId + "}"
                : "{\"type\": \"image\", \"mediaId\": " + mediaId + ", \"caption\": \"" + caption + "\"}";
    }

    private static String body(String... blocks) {
        return "{\"version\": 1, \"blocks\": [" + String.join(", ", blocks) + "]}";
    }

    /**
     * A published article whose live revision 1 has {@code body}.
     */
    private long published(String headline, String body) {
        long id = fixtures.article("PUBLISHED", "anna", "Anna", 1, MONDAY);
        fixtures.revision(id, 1, "", headline, "", "", body, MONDAY);
        return id;
    }

    private void execute(String sql) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    // --- route

    @Test
    void publishedBodyImageIsServed() {
        published("Race", body(paragraph("Start"), image(cat, "The finish line")));

        for (String kind : new String[] {"thumbnail", "web", "print"}) {
            Response response = get("/media/%d/%s?v=0".formatted(cat, kind));
            assertThat(response.statusCode()).as(kind).isEqualTo(200);
            assertThat(response.asByteArray()).as(kind).isEqualTo(ReaderMedia.rendition(cat, kind));
        }
    }

    @Test
    void bodyImageOnlyInADraftIsNotFound() {
        long draft = fixtures.article("DRAFT", "anna", "Anna", null, null);
        fixtures.revision(draft, 1, "", "Draft", "", "", body(image(cat, "x")), null);

        assertEmptyNotFound("/media/%d/web".formatted(cat));
    }

    @Test
    void bodyImageRemovedInAWorkingRevisionIsStillServed() {
        long article = published("Race", body(paragraph("Start"), image(cat, "")));
        fixtures.revision(article, 2, "", "Race", "", "", body(paragraph("Start")), null);

        assertThat(get("/media/%d/web".formatted(cat)).statusCode()).isEqualTo(200);
    }

    @Test
    void bodyImageOfAnArticleTakenOfflineIsNotFound() {
        long article = published("Race", body(image(cat, "")));
        fixtures.status(article, "OFFLINE");

        assertEmptyNotFound("/media/%d/web".formatted(cat));
    }

    // --- article page

    @Test
    void allBlockTypesInOrder() {
        long article = published("Race", body(
                "{\"type\": \"paragraph\", \"content\": [{\"text\": \"Bold\", \"bold\": true}]}",
                "{\"type\": \"subhead\", \"text\": \"Sub\"}",
                "{\"type\": \"quote\", \"content\": [{\"text\": \"Quote\"}]}",
                "{\"type\": \"list\", \"items\": [[{\"text\": \"One\"}], [{\"text\": \"Two\"}]]}",
                image(cat, "Pic")));

        String html = get("/articles/" + article).asString();
        String body = html.substring(html.indexOf("class=\"article__body\""));

        int strong = body.indexOf("<strong>");
        int h2 = body.indexOf("<h2>");
        int quote = body.indexOf("<blockquote>");
        int list = body.indexOf("<ul>");
        int figure = body.indexOf("<figure class=\"article__figure\">");
        assertThat(strong).isNotNegative();
        assertThat(h2).isGreaterThan(strong);
        assertThat(quote).isGreaterThan(h2);
        assertThat(list).isGreaterThan(quote);
        assertThat(figure).isGreaterThan(list);
        assertThat(body.split("<li>", -1)).hasSize(3);
    }

    @Test
    void imageBlockWithCaptionBetweenTwoParagraphs() {
        long article = published("Race", body(paragraph("Before"), image(cat, "The finish line"),
                paragraph("After")));

        String html = get("/articles/" + article).asString();

        String figure = html.substring(html.indexOf("<figure class=\"article__figure\">"),
                html.indexOf("</figure>", html.indexOf("<figure class=\"article__figure\">")));
        assertThat(figure).contains("src=\"/media/%d/web?v=0\"".formatted(cat),
                "srcset=\"/media/%d/thumbnail?v=0 480w, /media/%d/web?v=0 1600w\"".formatted(cat, cat),
                "width=\"1600\" height=\"1200\" alt=\"The finish line\" loading=\"lazy\"",
                "<figcaption class=\"article__caption\">The finish line</figcaption>");
        assertThat(html.indexOf("article__figure")).isGreaterThan(html.indexOf("Before"))
                .isLessThan(html.indexOf("After"));
    }

    @Test
    void imageBlockWithoutCaption() {
        long article = published("Race", body(image(cat, null)));

        String html = get("/articles/" + article).asString();

        assertThat(html).contains("<figure class=\"article__figure\">", "alt=\"\"").doesNotContain("figcaption");
    }

    @Test
    void captionWithMarkupIsText() {
        long article = published("Race", body(image(cat, "<b>Finish</b>")));

        String html = get("/articles/" + article).asString();

        assertThat(html).contains("alt=\"&lt;b&gt;Finish&lt;/b&gt;\"",
                "<figcaption class=\"article__caption\">&lt;b&gt;Finish&lt;/b&gt;</figcaption>")
                .doesNotContain("<b>Finish</b>");
    }

    @Test
    void unpublishedBodyImageIsNeverReferenced() {
        long dog = ReaderMedia.upload(800, 600);
        long article = published("Race", body(paragraph("Start")));
        fixtures.revision(article, 2, "", "Race", "", "", body(paragraph("Start"), image(dog, "Working")), null);

        assertThat(get("/articles/" + article).asString()).doesNotContain("/media/%d/".formatted(dog), "Working");
        assertThat(get("/print/article/" + article).asString()).doesNotContain("/media/%d/".formatted(dog));
        assertEmptyNotFound("/media/%d/web".formatted(dog));
    }

    @Test
    void bodyImageWithoutRenditionsIsLeftOut() {
        long article = published("Race", body(paragraph("Start"), image(cat, "Pic")));
        execute("DELETE FROM media_rendition WHERE media_id = " + cat + " AND kind = 'web'");

        assertThat(get("/articles/" + article).asString()).contains("Start").doesNotContain("article__figure", "Pic");
    }

    @Test
    void bodyImageAfterAnEditLinksTheNewVersion() {
        long article = published("Race", body(image(cat, "Pic")));
        assertThat(get("/articles/" + article).asString()).contains("/media/%d/web?v=0".formatted(cat));

        ReaderMedia.pixelate(cat, 0);

        assertThat(get("/articles/" + article).asString()).contains("src=\"/media/%d/web?v=1\"".formatted(cat))
                .doesNotContain("?v=0");
    }

    // --- print views

    @Test
    void printArticleWithABodyImage() {
        long article = published("Race", body(paragraph("Before"), image(cat, "The finish line"),
                paragraph("After")));

        String html = get("/print/article/" + article).asString();

        String body = html.substring(html.indexOf("class=\"article__body\""));
        assertThat(body).contains("<figure class=\"article__figure\">",
                "<img src=\"/media/%d/print?v=0\" width=\"3000\" height=\"2250\" alt=\"The finish line\">"
                        .formatted(cat),
                "<figcaption class=\"article__caption\">The finish line</figcaption>")
                .doesNotContain("/media/%d/web".formatted(cat), "loading=\"lazy\"");
        assertThat(body.indexOf("article__figure")).isGreaterThan(body.indexOf("Before"))
                .isLessThan(body.indexOf("After"));
    }

    @Test
    void issuePrintWithABodyImage() {
        long dog = ReaderMedia.upload(800, 600);
        long first = published("First", body(paragraph("One")));
        long second = published("Second", body(paragraph("Two"), image(cat, "Cat")));
        long third = published("Third", body(image(dog, "Dog")));
        fixtures.deleteAllIssues();
        long issue = fixtures.issue(2, true, null);
        fixtures.inIssue(issue, first, second, third);

        String html = get("/print/issue/" + issue).asString();

        int secondArticle = html.indexOf(">Second<");
        int thirdArticle = html.indexOf(">Third<");
        assertThat(html.indexOf("/media/%d/print?v=0".formatted(cat))).isGreaterThan(secondArticle)
                .isLessThan(thirdArticle);
        // the 800 x 600 upload has no print rendition of its own: the web rendition stands in
        assertThat(html.indexOf("/media/%d/".formatted(dog))).isGreaterThan(thirdArticle);
        assertThat(html).contains("<figcaption class=\"article__caption\">Cat</figcaption>",
                "<figcaption class=\"article__caption\">Dog</figcaption>");
    }
}
