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
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import jakarta.inject.Inject;

/**
 * The public {@code /account-deletion} page, its link on the legal notice, and the byline of articles
 * whose author's account was deleted.
 */
@QuarkusTest
@TestProfile(ThemeProfile.class)
class ReaderAccountDeletionTest {

    @Inject
    DataSource dataSource;

    private ReaderFixtures fixtures;

    @BeforeEach
    void setUp() {
        TestSupport.awaitReady();
        ThemeProfile.clear();
        fixtures = new ReaderFixtures(dataSource);
        fixtures.deleteAllArticles();
    }

    @AfterEach
    void cleanUp() {
        ThemeProfile.clear();
        fixtures.deleteAllArticles();
        TestSupport.resetIssues(dataSource);
    }

    private static Response get(String path, String language) {
        return given().redirects().follow(false).header("Accept-Language", language).get(path);
    }

    // --- the page

    @Test
    void pageWithLegalNoticeLinksIt() {
        ThemeProfile.write("legal-notice.txt", ReaderLegalNoticeTest.NOTICE);

        Response response = get("/account-deletion", "de");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asString()).contains("<main data-view=\"account-deletion\">",
                "<p class=\"masthead__name\"><a href=\"/\">", "<h1 class=\"legal-notice__title\">Konto löschen</h1>",
                "unter „Mein Konto“ beantragen", "Die Kontaktdaten stehen im <a href=\"/legal-notice\">Impressum</a>.",
                "„ehemaliges Redaktionsmitglied“", "nicht rückgängig", "durch Abmelden oder Deinstallieren der App");
    }

    @Test
    void pageWithoutLegalNoticeHasNoLink() {
        Response response = get("/account-deletion", "de");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asString()).contains("<main data-view=\"account-deletion\">")
                .doesNotContain("/legal-notice", "Kontaktdaten");
    }

    @Test
    void pageInEnglish() {
        ThemeProfile.write("legal-notice.txt", ReaderLegalNoticeTest.NOTICE);

        assertThat(get("/account-deletion", "en").asString()).contains(
                "<h1 class=\"legal-notice__title\">Delete an account</h1>", "under “My account”",
                "Their contact details are in the <a href=\"/legal-notice\">Legal notice</a>.",
                "“former newsroom member”", "cannot be undone", "logging out or uninstalling the app");
    }

    @Test
    void pageIsCachedLikeTheLegalNotice() {
        ThemeProfile.write("legal-notice.txt", ReaderLegalNoticeTest.NOTICE);

        for (String path : List.of("/account-deletion", "/legal-notice")) {
            Response response = get(path, "de");
            assertThat(response.header("Cache-Control")).as(path).isNull();
            assertThat(response.header("Vary")).as(path).isEqualTo("Accept-Language, Cookie");
        }
    }

    @Test
    void legalNoticeLinksThePage() {
        ThemeProfile.write("legal-notice.txt", ReaderLegalNoticeTest.NOTICE);

        String german = get("/legal-notice", "de").asString();
        assertThat(german).contains("<a href=\"/account-deletion\">Konto löschen</a>");
        assertThat(german.indexOf("/account-deletion")).isLessThan(german.indexOf("</main>"))
                .isGreaterThan(german.indexOf("Mediengesetz"));
        assertThat(get("/legal-notice", "en").asString())
                .contains("<a href=\"/account-deletion\">Delete an account</a>");
    }

    // --- byline of a deleted author

    @Test
    void deletedAuthorIsAFormerNewsroomMember() {
        Instant at = Instant.parse("2026-09-20T12:00:00Z");
        long gone = fixtures.publishedByDeletedAuthor("Orphaned story", at);
        fixtures.published("Anna's story", at.plusSeconds(60));
        long issue = fixtures.liveIssue();

        for (String path : List.of("/", "/articles/" + gone, "/issues/" + issue, "/print/article/" + gone,
                "/print/issue/" + issue)) {
            String german = get(path, "de").asString();
            assertThat(german).as(path).contains("<p class=\"byline\">Ehemaliges Redaktionsmitglied · ")
                    .doesNotContain("Von null", "Von  ·");
            assertThat(get(path, "en").asString()).as(path).contains("<p class=\"byline\">Former newsroom member · ");
        }
        assertThat(get("/", "de").asString()).contains("<p class=\"byline\">Von Anna · ");
    }
}
