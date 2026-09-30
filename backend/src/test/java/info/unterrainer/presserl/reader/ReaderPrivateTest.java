package info.unterrainer.presserl.reader;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import info.unterrainer.presserl.TestSupport;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import jakarta.inject.Inject;

/**
 * A newspaper made private by the deployment ({@code PRESSERL_NEWSPAPER_VISIBILITY=private}), seen by
 * an anonymous visitor. Logged-in visitors are covered by {@link ReaderLoginTest}.
 */
@QuarkusTest
@TestProfile(ReaderPrivateTest.Private.class)
class ReaderPrivateTest {

    public static class Private implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("presserl.newspaper.visibility", "private");
        }
    }

    @Inject
    DataSource dataSource;

    @AfterEach
    void resetIssues() {
        TestSupport.resetIssues(dataSource);
    }

    private long published;

    @BeforeEach
    void publishedArticle() {
        ReaderFixtures fixtures = new ReaderFixtures(dataSource);
        fixtures.deleteAllArticles();
        published = fixtures.published("Private headline", Instant.parse("2026-09-20T12:00:00Z"));
    }

    @Test
    void frontPageShowsOnlyMastheadPrivateNoteAndLoginLink() {
        Response response = given().get("/");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.header("Cache-Control")).isEqualTo("private, no-store");
        assertThat(response.asString())
                .contains("<h1 class=\"masthead__name\">My Newspaper</h1>", "Diese Zeitung ist privat.",
                        "<a href=\"/login\">Anmelden</a>")
                .doesNotContain("Private headline", "lead-article", "article-card", "/articles/", "/logout",
                        "section-bar", "section-tag");
    }

    @Test
    void sectionFilterShowsOnlyThePrivateNote() {
        for (String section : new String[] { "1", "999999", "abc" }) {
            Response response = given().redirects().follow(false).get("/?section=" + section);

            assertThat(response.statusCode()).as(section).isEqualTo(200);
            assertThat(response.asString()).as(section)
                    .contains("Diese Zeitung ist privat.", "<a href=\"/login\">Anmelden</a>")
                    .doesNotContain("Private headline", "lead-article", "section-tag");
        }
    }

    @Test
    void anonymousVisitorChoosesATextSize() {
        Response response = given().redirects().follow(false).formParam("size", "xl").formParam("next", "/")
                .post("/text-size");

        assertThat(response.statusCode()).isEqualTo(303);
        assertThat(response.cookie("presserl_text_size")).isEqualTo("xl");
        assertThat(given().cookie("presserl_text_size", "xl").get("/").asString())
                .contains("data-text-size=\"xl\"", "class=\"text-size-switch\"", "Diese Zeitung ist privat.");
    }

    @Test
    void publishedArticleRedirectsToLogin() {
        assertLoginRedirect(String.valueOf(published), "/login?next=/articles/" + published);
    }

    @Test
    void unknownArticleRedirectsLikeAPublishedOne() {
        assertLoginRedirect("999999", "/login?next=/articles/999999");
    }

    @Test
    void malformedIdRedirectsEncoded() {
        assertLoginRedirect("a%20b", "/login?next=/articles/a%2520b");
    }

    @Test
    void issuePagesAndPrintViewsRedirectToLogin() {
        assertLoginRedirectOf("/issues", "/login?next=/issues");
        assertLoginRedirectOf("/issues/3", "/login?next=/issues/3");
        assertLoginRedirectOf("/print/issue/3", "/login?next=/print/issue/3");
        assertLoginRedirectOf("/print/article/" + published, "/login?next=/print/article/" + published);
    }

    @Test
    void logoutWithoutSessionGoesHome() {
        Response response = given().redirects().follow(false).get("/logout");

        assertThat(response.statusCode()).isEqualTo(303);
        assertThat(response.header("Location")).endsWith(":8081/");
        assertThat(response.header("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    void loginStartsTheCodeFlowWithTheReaderClient() {
        Response response = given().redirects().follow(false).get("/login?next=/articles/" + published);

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.header("Location"))
                .startsWith(TestSupport.issuer() + "/protocol/openid-connect/auth")
                .contains("client_id=presserl-reader", "response_type=code", "code_challenge_method=S256");
    }

    private void assertLoginRedirect(String id, String location) {
        assertLoginRedirectOf("/articles/" + id, location);
    }

    private void assertLoginRedirectOf(String path, String location) {
        Response response = given().redirects().follow(false).urlEncodingEnabled(false).get(path);

        assertThat(response.statusCode()).isEqualTo(303);
        assertThat(response.header("Location")).endsWith(":8081" + location);
        assertThat(response.asString()).doesNotContain("Private headline");
    }
}
