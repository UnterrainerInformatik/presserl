package info.unterrainer.presserl.reader;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;

@QuarkusTest
class ReaderResourceTest {

    private static final Pattern ABSOLUTE_URL = Pattern.compile("(?i)(src|href|action)\\s*=\\s*[\"']?(https?:)?//");

    @Test
    void frontPageShowsDefaultMasthead() {
        given().get("/").then()
                .statusCode(200)
                .header("Content-Type", "text/html;charset=UTF-8")
                .body(containsString("<h1 class=\"masthead__name\">My Newspaper</h1>"))
                .body(containsString("<main data-view=\"frontpage\">"));
    }

    @Test
    void frontPageHasSameOriginCspAndNoForeignUrls() {
        Response response = given().get("/");

        response.then().statusCode(200)
                .header("Content-Security-Policy", startsWith("default-src 'self';"));
        assertThat(ABSOLUTE_URL.matcher(response.asString()).find()).isFalse();
        assertThat(response.asString()).doesNotContain("<script");
    }

    @Test
    void stylesheetIsServedWithReaderCsp() {
        given().get("/reader/reader.css").then()
                .statusCode(200)
                .header("Content-Security-Policy", startsWith("default-src 'self';"));
    }
}
