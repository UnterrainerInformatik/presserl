package info.unterrainer.presserl.reader;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.time.Instant;

import javax.sql.DataSource;

import org.htmlunit.WebResponse;
import org.htmlunit.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import info.unterrainer.presserl.TestSupport;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import jakarta.inject.Inject;

/**
 * Reader login through the real code flow against the Dev Services Keycloak, on a private newspaper.
 */
@QuarkusTest
@TestProfile(ReaderPrivateTest.Private.class)
class ReaderLoginTest {

    @Inject
    DataSource dataSource;

    private long published;
    private long draft;

    @BeforeEach
    void articles() {
        ReaderFixtures fixtures = new ReaderFixtures(dataSource);
        fixtures.deleteAllArticles();
        published = fixtures.published("Private headline", Instant.parse("2026-09-20T12:00:00Z"));
        draft = fixtures.article("DRAFT", "anna", "Anna", null, null);
        fixtures.revision(draft, 1, "", "Draft headline", "", "", ReaderFixtures.EMPTY_BODY, null);
    }

    @Test
    void readerLandsOnTheArticleAfterLogin() {
        try (ReaderBrowser browser = new ReaderBrowser()) {
            WebResponse article = browser.login("/articles/" + published, "reader", "reader");

            assertThat(path(article)).isEqualTo("/articles/" + published);
            assertThat(article.getStatusCode()).isEqualTo(200);
            assertThat(article.getContentAsString()).contains("Private headline", "Angemeldet als Reader Test",
                    "<a href=\"/logout\">Abmelden</a>");
            assertThat(article.getResponseHeaderValue("Cache-Control")).isEqualTo("private, no-store");

            WebResponse front = browser.get("/");
            assertThat(front.getContentAsString()).contains("Private headline")
                    .doesNotContain("Diese Zeitung ist privat.", "href=\"/login\"");
            assertThat(browser.get("/articles/" + draft).getStatusCode()).isEqualTo(404);
        }
    }

    @Test
    void higherRoleIsEntitled() {
        try (ReaderBrowser browser = new ReaderBrowser()) {
            WebResponse front = browser.login("/login", "chief", "chief");

            assertThat(path(front)).isEqualTo("/");
            assertThat(front.getContentAsString()).contains("Private headline", "Angemeldet als Chief Editor");
        }
    }

    @Test
    void accountWithoutNewspaperRoleGetsNoAccess() {
        try (ReaderBrowser browser = new ReaderBrowser()) {
            WebResponse front = browser.login("/login", "nogroups", "nogroups");

            assertThat(front.getContentAsString())
                    .contains("Dieses Konto hat keinen Zugang zu dieser Zeitung.", "Angemeldet als No Groups")
                    .doesNotContain("Private headline", "href=\"/login\"");
            WebResponse article = browser.get("/articles/" + published);
            assertThat(article.getStatusCode()).isEqualTo(404);
            assertThat(article.getContentAsString()).doesNotContain("Private headline");
        }
    }

    @Test
    void foreignTargetEndsOnTheFrontPage() {
        try (ReaderBrowser browser = new ReaderBrowser()) {
            WebResponse landed = browser.login("/login?next=//evil.example/x", "reader", "reader");

            assertThat(landed.getWebRequest().getUrl().getHost()).isEqualTo("localhost");
            assertThat(path(landed)).isEqualTo("/");
        }
    }

    @Test
    void loginWithSessionRedirectsWithoutKeycloak() {
        try (ReaderBrowser browser = new ReaderBrowser()) {
            browser.login("/login", "reader", "reader");

            WebResponse again = browser.getWithoutRedirect("/login?next=/articles/" + published);
            assertThat(again.getStatusCode()).isEqualTo(303);
            assertThat(again.getResponseHeaderValue("Location")).isEqualTo(ReaderBrowser.url("/articles/" + published));
            assertThat(again.getResponseHeaderValue("Cache-Control")).isEqualTo("no-store");
        }
    }

    @Test
    void sessionCookieIsHttpOnlyLaxAndNoApiCredential() {
        try (ReaderBrowser browser = new ReaderBrowser()) {
            browser.login("/login", "reader", "reader");

            Cookie session = browser.sessionCookie();
            assertThat(session).isNotNull();
            assertThat(session.isHttpOnly()).isTrue();
            assertThat(session.getSameSite()).isEqualToIgnoringCase("lax");
            assertThat(session.getPath()).isEqualTo("/");
            assertThat(session.getValue()).doesNotContain("reader");

            assertThat(browser.get("/api/me").getStatusCode()).isEqualTo(401);
            // Same origin as the admin app: a bearer token still works next to the reader cookie
            assertThat(browser.getWithBearer("/api/me", TestSupport.token("chief", "chief")).getContentAsString())
                    .contains("\"username\":\"chief\"");
        }
    }

    @Test
    void logoutEndsTheSession() {
        try (ReaderBrowser browser = new ReaderBrowser()) {
            browser.login("/login", "reader", "reader");

            WebResponse front = browser.get("/logout");

            assertThat(path(front)).isEqualTo("/");
            assertThat(front.getWebRequest().getUrl().getPort()).isEqualTo(RestAssured.port);
            assertThat(front.getContentAsString()).contains("Diese Zeitung ist privat.", "href=\"/login\"")
                    .doesNotContain("Private headline", "/logout");
            assertThat(browser.sessionCookie()).isNull();
            // The Keycloak session is gone too: logging in asks for the password again
            assertThat(browser.get("/login").getWebRequest().getUrl().toString()).contains("/protocol/openid-connect/auth");
        }
    }

    private static String path(WebResponse response) {
        URL url = response.getWebRequest().getUrl();
        return url.getPath();
    }
}
