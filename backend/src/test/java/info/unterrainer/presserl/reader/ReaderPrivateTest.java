package info.unterrainer.presserl.reader;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

/**
 * A newspaper made private by the deployment ({@code PRESSERL_NEWSPAPER_VISIBILITY=private}).
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

    private long published;

    @BeforeEach
    void publishedArticle() {
        ReaderFixtures fixtures = new ReaderFixtures(dataSource);
        fixtures.deleteAllArticles();
        published = fixtures.published("Private headline", Instant.parse("2026-09-20T12:00:00Z"));
    }

    @Test
    void frontPageShowsOnlyMastheadAndPrivateNote() {
        String html = given().get("/").then().statusCode(200).extract().asString();

        assertThat(html).contains("<h1 class=\"masthead__name\">My Newspaper</h1>", "Diese Zeitung ist privat.")
                .doesNotContain("Private headline", "class=\"story", "/articles/");
    }

    @Test
    void articlePageIsNotFound() {
        String html = given().get("/articles/" + published).then().statusCode(404).extract().asString();

        assertThat(html).contains("<main data-view=\"not-found\">").doesNotContain("Private headline");
    }
}
