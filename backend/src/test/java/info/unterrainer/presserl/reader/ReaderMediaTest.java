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
import info.unterrainer.presserl.media.MediaFixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;

/**
 * Lead images for readers of a public newspaper: the route {@code /media/{id}/{kind}} and the figures
 * on the article page and the front page.
 */
@QuarkusTest
class ReaderMediaTest {

    private static final Instant MONDAY = Instant.parse("2026-09-21T12:00:00Z");
    private static final Instant TUESDAY = Instant.parse("2026-09-22T12:00:00Z");

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
    }

    private static Response get(String path) {
        return given().redirects().follow(false).get(path);
    }

    private static void assertEmptyNotFound(String path) {
        Response response = get(path);
        assertThat(response.statusCode()).as(path).isEqualTo(404);
        assertThat(response.asByteArray()).as(path).isEmpty();
    }

    // --- route

    @Test
    void publishedLeadImageIsServedInEveryKind() {
        long article = fixtures.published("Minka", MONDAY);
        fixtures.leadImage(article, 1, cat, "Our cat Minka");

        for (String kind : List.of("thumbnail", "web", "print")) {
            Response response = get("/media/%d/%s".formatted(cat, kind));
            assertThat(response.statusCode()).as(kind).isEqualTo(200);
            assertThat(response.header("Content-Type")).isEqualTo("image/jpeg");
            assertThat(response.header("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(response.header("Cache-Control")).isEqualTo("public, max-age=3600");
            byte[] bytes = response.asByteArray();
            assertThat(response.header("Content-Length")).isEqualTo(String.valueOf(bytes.length));
            assertThat(bytes).isEqualTo(ReaderMedia.rendition(cat, kind));
        }
        assertThat(MediaFixtures.read(get("/media/%d/web".formatted(cat)).asByteArray()).getWidth()).isEqualTo(1600);
    }

    @Test
    void theStoredImageIsNeverServed() {
        long article = fixtures.published("Minka", MONDAY);
        fixtures.leadImage(article, 1, cat, "");
        byte[] master = ReaderMedia.content(cat);

        for (String kind : List.of("original", "master", "content", "WEB")) {
            assertEmptyNotFound("/media/%d/%s".formatted(cat, kind));
        }
        for (String kind : List.of("thumbnail", "web", "print")) {
            assertThat(get("/media/%d/%s".formatted(cat, kind)).asByteArray()).isNotEqualTo(master);
        }
    }

    @Test
    void imageOnlyInADraftIsNotFound() {
        long draft = fixtures.article("DRAFT", "anna", "Anna", null, null);
        fixtures.revision(draft, 1, "", "Draft", "", "", ReaderFixtures.EMPTY_BODY, null);
        fixtures.leadImage(draft, 1, cat, "");

        assertEmptyNotFound("/media/%d/web".formatted(cat));
    }

    @Test
    void imageOnlyInAnUnpublishedRevisionIsNotFound() {
        long article = fixtures.published("Minka", MONDAY);
        fixtures.revision(article, 2, "", "Minka", "", "", ReaderFixtures.EMPTY_BODY, null);
        fixtures.leadImage(article, 2, cat, "");

        assertEmptyNotFound("/media/%d/thumbnail".formatted(cat));
    }

    @Test
    void imageOfAnArticleTakenOfflineIsNotFound() {
        long article = fixtures.published("Minka", MONDAY);
        fixtures.leadImage(article, 1, cat, "");
        assertThat(get("/media/%d/web".formatted(cat)).statusCode()).isEqualTo(200);

        fixtures.status(article, "OFFLINE");

        assertEmptyNotFound("/media/%d/web".formatted(cat));
    }

    @Test
    void mediaInNoArticleAndUnknownOrMalformedIdsAreNotFound() {
        assertEmptyNotFound("/media/%d/web".formatted(cat));
        assertEmptyNotFound("/media/999999/web");
        assertEmptyNotFound("/media/abc/web");
        assertEmptyNotFound("/media/-1/web");
        assertEmptyNotFound("/media/1234567890123456789/web");
    }

    // --- pages

    @Test
    void articlePageShowsTheLeadImageWithCaption() {
        long article = fixtures.published("Minka", MONDAY);
        fixtures.leadImage(article, 1, cat, "Our cat <b>Minka</b>");

        String html = get("/articles/" + article).asString();

        assertThat(html).contains("<figure class=\"lead-image\">",
                "src=\"/media/%d/web\"".formatted(cat),
                "srcset=\"/media/%d/thumbnail 480w, /media/%d/web 1600w\"".formatted(cat, cat),
                "sizes=\"(min-width: 60rem) 60rem, 100vw\"",
                "width=\"1600\" height=\"1200\" alt=\"\"",
                "<figcaption class=\"lead-image__caption\">Our cat &lt;b&gt;Minka&lt;/b&gt;</figcaption>")
                .doesNotContain("<b>Minka</b>");
        // after the headline block, before the byline
        assertThat(html.indexOf("lead-image")).isGreaterThan(html.indexOf("class=\"headline\""))
                .isLessThan(html.indexOf("class=\"byline\""));
    }

    @Test
    void articlePageWithoutCaptionHasNoFigcaption() {
        long article = fixtures.published("Minka", MONDAY);
        fixtures.leadImage(article, 1, cat, "");

        assertThat(get("/articles/" + article).asString()).contains("<figure class=\"lead-image\">")
                .doesNotContain("figcaption");
    }

    @Test
    void articlePageWithoutLeadImageHasNoFigure() {
        long article = fixtures.published("Minka", MONDAY);

        assertThat(get("/articles/" + article).asString()).doesNotContain("lead-image", "/media/");
    }

    @Test
    void unpublishedImageChangeReferencesOnlyTheLiveMedia() {
        long dog = ReaderMedia.upload(800, 600);
        long article = fixtures.published("Minka", MONDAY);
        fixtures.leadImage(article, 1, cat, "Live");
        fixtures.revision(article, 2, "", "Minka", "", "", ReaderFixtures.EMPTY_BODY, null);
        fixtures.leadImage(article, 2, dog, "Working");

        String html = get("/articles/" + article).asString();

        assertThat(html).contains("/media/%d/web".formatted(cat), "Live")
                .doesNotContain("/media/%d/".formatted(dog), "Working");
        assertThat(get("/").asString()).doesNotContain("/media/%d/".formatted(dog));
        assertEmptyNotFound("/media/%d/web".formatted(dog));
    }

    @Test
    void frontPageLeadStoryUsesWebAndCardsUseThumbnails() {
        long dog = ReaderMedia.upload(800, 600);
        long older = fixtures.published("Older", MONDAY);
        fixtures.leadImage(older, 1, dog, "A dog");
        long newest = fixtures.published("Newest", TUESDAY);
        fixtures.leadImage(newest, 1, cat, "");
        fixtures.published("Without image", MONDAY.minusSeconds(60));

        String html = get("/").asString();

        String lead = html.substring(html.indexOf("class=\"lead-article\""), html.indexOf("class=\"article-card\""));
        assertThat(lead).contains("src=\"/media/%d/web\"".formatted(cat), "width=\"1600\" height=\"1200\"")
                .doesNotContain("loading=\"lazy\"");
        String cards = html.substring(html.indexOf("class=\"article-card\""));
        assertThat(cards).contains("src=\"/media/%d/thumbnail\" width=\"480\" height=\"360\" alt=\"\" loading=\"lazy\""
                .formatted(dog), "<figcaption class=\"lead-image__caption\">A dog</figcaption>")
                .doesNotContain("/media/%d/web".formatted(dog));
        assertThat(html.split("<figure", -1)).hasSize(3);
    }
}
