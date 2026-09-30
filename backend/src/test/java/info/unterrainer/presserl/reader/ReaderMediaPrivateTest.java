package info.unterrainer.presserl.reader;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import javax.sql.DataSource;

import org.htmlunit.WebResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import info.unterrainer.presserl.TestSupport;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import jakarta.inject.Inject;

/**
 * Lead images of a private newspaper: only entitled readers get them, through their reader session.
 */
@QuarkusTest
@TestProfile(ReaderPrivateTest.Private.class)
class ReaderMediaPrivateTest {

    @Inject
    DataSource dataSource;

    private ReaderFixtures fixtures;
    private String path;

    @BeforeEach
    void publishedLeadImage() {
        TestSupport.awaitReady();
        fixtures = new ReaderFixtures(dataSource);
        fixtures.deleteAllArticlesAndMedia();
        long cat = ReaderMedia.upload(1200, 900);
        long article = fixtures.published("Private headline", Instant.parse("2026-09-20T12:00:00Z"));
        fixtures.leadImage(article, 1, cat, "Our cat Minka");
        path = "/media/%d/web?v=0".formatted(cat);
    }

    @AfterEach
    void cleanUp() {
        TestSupport.resetIssues(dataSource);
        fixtures.deleteAllArticlesAndMedia();
    }

    @Test
    void anonymousVisitorGetsAnEmptyNotFound() {
        Response response = given().redirects().follow(false).get(path);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.asByteArray()).isEmpty();
    }

    @Test
    void loggedInVisitorWithoutRoleGetsNotFound() {
        try (ReaderBrowser browser = new ReaderBrowser()) {
            browser.login("/login", "nogroups", "nogroups");

            WebResponse image = browser.get(path);

            assertThat(image.getStatusCode()).isEqualTo(404);
            assertThat(image.getContentAsStream().readAllBytes()).isEmpty();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @Test
    void entitledReaderGetsTheImagePrivatelyCached() {
        try (ReaderBrowser browser = new ReaderBrowser()) {
            browser.login("/login", "reader", "reader");

            WebResponse image = browser.get(path);

            assertThat(image.getStatusCode()).isEqualTo(200);
            assertThat(image.getContentType()).isEqualTo("image/jpeg");
            assertThat(image.getResponseHeaderValue("Cache-Control")).isEqualTo("private, max-age=3600");
            assertThat(image.getResponseHeaderValue("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(browser.get("/").getContentAsString()).contains("src=\"" + path + "\"");
        }
    }
}
