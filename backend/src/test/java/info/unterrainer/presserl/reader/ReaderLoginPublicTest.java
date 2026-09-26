package info.unterrainer.presserl.reader;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Objects;

import javax.sql.DataSource;

import org.htmlunit.WebResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Reader login on a public newspaper: no login link, but a logged-in visitor sees who they are.
 */
@QuarkusTest
class ReaderLoginPublicTest {

    @Inject
    DataSource dataSource;

    @BeforeEach
    void publishedArticle() {
        ReaderFixtures fixtures = new ReaderFixtures(dataSource);
        fixtures.deleteAllArticles();
        fixtures.published("Public headline", Instant.parse("2026-09-20T12:00:00Z"));
    }

    @Test
    void anonymousVisitorSeesNeitherLoginNorLogout() {
        try (ReaderBrowser browser = new ReaderBrowser()) {
            WebResponse front = browser.get("/");

            assertThat(front.getContentAsString()).contains("Public headline")
                    .doesNotContain("href=\"/login\"", "href=\"/logout\"", "Angemeldet als");
            assertThat(Objects.toString(front.getResponseHeaderValue("Cache-Control"), "")).doesNotContain("no-store");
        }
    }

    @Test
    void loggedInVisitorSeesNameAndLogoutAndIsNotCached() {
        try (ReaderBrowser browser = new ReaderBrowser()) {
            WebResponse front = browser.login("/login", "reader", "reader");

            assertThat(front.getContentAsString()).contains("Public headline", "Angemeldet als Reader Test",
                    "<a href=\"/logout\">Abmelden</a>");
            assertThat(front.getResponseHeaderValue("Cache-Control")).isEqualTo("private, no-store");
        }
    }
}
