package info.unterrainer.presserl.reader;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;

import javax.sql.DataSource;

import org.htmlunit.WebResponse;
import org.htmlunit.http.Cookie;
import org.junit.jupiter.api.AfterEach;
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
    private long issue;

    @BeforeEach
    void articles() {
        ReaderFixtures fixtures = new ReaderFixtures(dataSource);
        fixtures.deleteAllArticles();
        published = fixtures.published("Private headline", Instant.parse("2026-09-20T12:00:00Z"));
        draft = fixtures.article("DRAFT", "anna", "Anna", null, null);
        fixtures.revision(draft, 1, "", "Draft headline", "", "", ReaderFixtures.EMPTY_BODY, null);
        fixtures.deleteAllIssues();
        issue = fixtures.issue(1, true, null);
        fixtures.inIssue(issue, published);
    }

    @AfterEach
    void issues() {
        TestSupport.resetIssues(dataSource);
    }

    @Test
    void entitledReaderSeesIssuePagesAndPrintViewsUncached() {
        try (ReaderBrowser browser = new ReaderBrowser()) {
            WebResponse page = browser.login("/issues/" + issue, "reader", "reader");

            assertThat(path(page)).isEqualTo("/issues/" + issue);
            assertThat(page.getStatusCode()).isEqualTo(200);
            assertThat(page.getContentAsString()).contains("Ausgabe 1", "Private headline");
            assertThat(page.getResponseHeaderValue("Cache-Control")).isEqualTo("private, no-store");
            for (String path : new String[] { "/issues", "/print/issue/" + issue, "/print/article/" + published }) {
                WebResponse response = browser.get(path);
                assertThat(response.getStatusCode()).as(path).isEqualTo(200);
                assertThat(response.getContentAsString()).as(path).contains("Private headline");
                assertThat(response.getResponseHeaderValue("Cache-Control")).as(path).isEqualTo("private, no-store");
            }
        }
    }

    @Test
    void accountWithoutNewspaperRoleGetsNoIssuePages() {
        try (ReaderBrowser browser = new ReaderBrowser()) {
            browser.login("/login", "nogroups", "nogroups");

            for (String path : new String[] { "/issues", "/issues/" + issue, "/print/issue/" + issue,
                    "/print/article/" + published }) {
                WebResponse response = browser.get(path);
                assertThat(response.getStatusCode()).as(path).isEqualTo(404);
                assertThat(response.getContentAsString()).as(path).doesNotContain("Private headline");
            }
        }
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
    void sessionCookieOutlivesTheAccessToken() {
        try (ReaderBrowser browser = new ReaderBrowser()) {
            browser.login("/login", "reader", "reader");

            Cookie session = browser.sessionCookie();
            assertThat(session).isNotNull();
            // Persistent and as long-lived as the realm's 180-day SSO session
            assertThat(session.getExpires()).isNotNull();
            assertThat(session.getExpires().toInstant()).isAfter(Instant.now().plus(Duration.ofDays(179)));
        }
    }

    @Test
    void qrEntryForwardsTheUsernameAndDropsTheFragment() {
        try (ReaderBrowser browser = new ReaderBrowser()) {
            WebResponse qr = browser.getWithoutRedirect("/qr?u=lena");

            assertThat(qr.getStatusCode()).isEqualTo(303);
            assertThat(qr.getResponseHeaderValue("Location")).isEqualTo(ReaderBrowser.url("/login?login_hint=lena#"));
            assertThat(qr.getResponseHeaderValue("Cache-Control")).isEqualTo("no-store");
            assertThat(qr.getResponseHeaderValue("Set-Cookie")).isNull();
            assertThat(browser.sessionCookie()).isNull();
        }
    }

    @Test
    void qrEntryWithInvalidOrMissingUsernameGoesToThePlainLogin() {
        try (ReaderBrowser browser = new ReaderBrowser()) {
            for (String path : new String[] { "/qr?u=Lena%20X", "/qr?u=lena%26next%3D%2F%2Fevil", "/qr?u=", "/qr" }) {
                WebResponse qr = browser.getWithoutRedirect(path);

                assertThat(qr.getStatusCode()).as(path).isEqualTo(303);
                assertThat(qr.getResponseHeaderValue("Location")).as(path).isEqualTo(ReaderBrowser.url("/login#"));
                assertThat(qr.getResponseHeaderValue("Cache-Control")).as(path).isEqualTo("no-store");
                assertThat(qr.getResponseHeaderValue("Set-Cookie")).as(path).isNull();
            }
        }
    }

    @Test
    void loginForwardsTheUsernameHintToKeycloak() {
        try (ReaderBrowser browser = new ReaderBrowser()) {
            String withHint = browser.getWithoutRedirect("/login?login_hint=lena").getResponseHeaderValue("Location");
            String withoutHint = browser.getWithoutRedirect("/login").getResponseHeaderValue("Location");

            assertThat(withHint).contains("/protocol/openid-connect/auth", "response_type=code", "login_hint=lena");
            assertThat(withoutHint).contains("/protocol/openid-connect/auth").doesNotContain("login_hint");
        }
    }

    @Test
    void qrScanEndsOnThePrefilledLoginForm() {
        try (ReaderBrowser browser = new ReaderBrowser()) {
            WebResponse form = browser.get("/qr?u=reader");

            assertThat(form.getWebRequest().getUrl().toString()).contains("/protocol/openid-connect/auth",
                    "login_hint=reader");
            assertThat(form.getContentAsString()).contains("value=\"reader\"");
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
