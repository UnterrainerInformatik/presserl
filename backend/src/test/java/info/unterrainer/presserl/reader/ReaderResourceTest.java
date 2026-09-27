package info.unterrainer.presserl.reader;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;

import java.time.Instant;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;

@QuarkusTest
class ReaderResourceTest {

    private static final Pattern ABSOLUTE_URL = Pattern.compile("(?i)(src|href|action)\\s*=\\s*[\"']?(https?:)?//");

    @Inject
    DataSource dataSource;

    private ReaderFixtures emptyNewspaper() {
        ReaderFixtures fixtures = new ReaderFixtures(dataSource);
        fixtures.deleteAllArticles();
        return fixtures;
    }

    @Test
    void frontPageShowsDefaultMasthead() {
        given().get("/").then()
                .statusCode(200)
                .header("Content-Type", "text/html;charset=UTF-8")
                .header("Vary", "Accept-Language, Cookie")
                .body(containsString("<h1 class=\"masthead__name\">My Newspaper</h1>"))
                .body(containsString("<main data-view=\"frontpage\">"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "/", "/articles/{published}", "/articles/999999", "/articles/abc" })
    void pagesHaveSameOriginCspAndNoScriptsOrForeignUrls(String path) {
        long published = emptyNewspaper().published("Csp check", Instant.parse("2026-09-20T12:00:00Z"));

        Response response = given().get(path.replace("{published}", Long.toString(published)));

        response.then().header("Content-Security-Policy", startsWith("default-src 'self';"));
        assertThat(response.statusCode()).isIn(200, 404);
        assertThat(ABSOLUTE_URL.matcher(response.asString()).find()).isFalse();
        assertThat(response.asString()).doesNotContain("<script", "style=");
    }

    @Test
    void stylesheetIsServedWithReaderCsp() {
        given().get("/reader/reader.css").then()
                .statusCode(200)
                .header("Content-Security-Policy", startsWith("default-src 'self';"));
    }

    // --- localization

    @Test
    void germanBrowserGetsGerman() {
        emptyNewspaper();

        assertThat(given().header("Accept-Language", "de-AT,de;q=0.9").get("/").asString())
                .contains("<html lang=\"de\" data-text-size=\"m\">", "Noch keine Artikel veröffentlicht.");
    }

    @Test
    void englishBrowserGetsEnglish() {
        emptyNewspaper();

        assertThat(given().header("Accept-Language", "en-GB,en;q=0.9").get("/").asString())
                .contains("<html lang=\"en\" data-text-size=\"m\">", "No articles published yet.");
    }

    @Test
    void englishOnlyWhenPreferred() {
        emptyNewspaper();

        assertThat(given().header("Accept-Language", "fr-FR,en;q=0.8").get("/").asString())
                .contains("<html lang=\"de\" data-text-size=\"m\">", "Noch keine Artikel veröffentlicht.");
    }

    @Test
    void noLanguagePreferenceGetsGerman() {
        emptyNewspaper();

        assertThat(given().get("/").asString())
                .contains("<html lang=\"de\" data-text-size=\"m\">", "Noch keine Artikel veröffentlicht.");
    }

    @Test
    void englishArticleAndNotFoundPages() {
        long id = emptyNewspaper().published("Hello", Instant.parse("2026-09-20T12:00:00Z"));

        assertThat(given().header("Accept-Language", "en-US").get("/articles/" + id).asString())
                .contains("<html lang=\"en\" data-text-size=\"m\">", "By Anna", "Published on September 20, 2026");
        assertThat(given().header("Accept-Language", "en-US").get("/articles/abc").asString())
                .contains("<html lang=\"en\" data-text-size=\"m\">", "Page not found", "<a href=\"/\">To the front page</a>");
    }
}
