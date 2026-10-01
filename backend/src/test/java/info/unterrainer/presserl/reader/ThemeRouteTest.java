package info.unterrainer.presserl.reader;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;

/**
 * The fork's theme directory at {@code /theme/*}.
 */
@QuarkusTest
@TestProfile(ThemeProfile.class)
class ThemeRouteTest {

    @BeforeEach
    void theme() {
        ThemeProfile.clear();
        ThemeProfile.write("custom.css", ":root { --presserl-color-accent: #1f4e79; }");
        ThemeProfile.write("fonts/comic.woff2", "font");
        ThemeProfile.write("notes.txt", "not served");
        ThemeProfile.write("folder.css/inner.css", "inner");
    }

    @Test
    void customStylesheetIsServedAndRevalidated() {
        given().get("/theme/custom.css").then()
                .statusCode(200)
                .contentType(startsWith("text/css"))
                .header("Cache-Control", "no-cache")
                .header("Last-Modified", notNullValue())
                .header("Content-Security-Policy", startsWith("default-src 'self';"))
                .body(equalTo(":root { --presserl-color-accent: #1f4e79; }"));
    }

    @Test
    void unchangedFileIsNotModified() {
        String modified = given().get("/theme/custom.css").then().statusCode(200).extract().header("Last-Modified");

        given().header("If-Modified-Since", modified).get("/theme/custom.css").then()
                .statusCode(304);
    }

    @Test
    void forkFontHasFontContentType() {
        given().get("/theme/fonts/comic.woff2").then()
                .statusCode(200)
                .contentType("font/woff2");
    }

    @Test
    void servedWithoutLoginWhileAReaderSessionCookieIsSent() {
        given().cookie("q_session_reader", "garbage").get("/theme/custom.css").then()
                .statusCode(200);
    }

    @Test
    void readerPagesLinkCustomCssAfterTheDefaultThemeOnlyWhilePresent() throws IOException {
        String html = given().get("/").asString();

        int defaultTheme = html.indexOf("<link rel=\"stylesheet\" href=\"/reader/reader.css\">");
        assertThat(defaultTheme).isPositive()
                .isLessThan(html.indexOf("<link rel=\"stylesheet\" href=\"/theme/custom.css\">"));
        assertThat(given().get("/articles/999999").asString()).contains("href=\"/theme/custom.css\"");

        Files.delete(ThemeProfile.DIR.resolve("custom.css"));

        assertThat(given().get("/").asString()).doesNotContain("/theme/custom.css");
    }

    @Test
    void themeIconsReplaceTheDefaultsOfTheirKindWhilePresent() throws IOException {
        ThemeProfile.write("favicon.svg", "<svg xmlns=\"http://www.w3.org/2000/svg\"/>");
        ThemeProfile.write("favicon.ico", "fork icon");

        String html = given().get("/").asString();

        assertThat(html).contains("<link rel=\"icon\" href=\"/theme/favicon.svg\" type=\"image/svg+xml\">",
                "<link rel=\"icon\" href=\"/theme/favicon.ico\" sizes=\"48x48\">",
                "<link rel=\"apple-touch-icon\" href=\"/reader/icons/apple-touch-icon.png\">");
        given().get("/theme/favicon.svg").then().statusCode(200).contentType("image/svg+xml");
        given().get("/favicon.ico").then()
                .statusCode(200)
                .contentType("image/x-icon")
                .header("Cache-Control", "no-cache")
                .body(equalTo("fork icon"));

        Files.delete(ThemeProfile.DIR.resolve("favicon.svg"));
        Files.delete(ThemeProfile.DIR.resolve("favicon.ico"));

        assertThat(given().get("/").asString()).doesNotContain("/theme/favicon")
                .contains("href=\"/reader/icons/favicon.svg\"");
        assertThat(given().get("/favicon.ico").asString()).isNotEqualTo("fork icon");
    }

    @ParameterizedTest
    @ValueSource(strings = { "/theme/notes.txt", "/theme/folder.css", "/theme/missing.css", "/theme/",
            "/theme/../application.properties", "/theme/%2e%2e/%2e%2e/etc/passwd",
            "/theme/fonts/%2e%2e/%2e%2e/%2e%2e/pom.xml", "/theme/..%2fpom.xml" })
    void everythingElseIs404(String path) throws IOException {
        // raw request: the client must not normalise dot segments itself
        HttpURLConnection connection = (HttpURLConnection) URI
                .create(RestAssured.baseURI + ":" + RestAssured.port + path).toURL().openConnection();
        try {
            assertThat(connection.getResponseCode()).as(path).isEqualTo(404);
            try (InputStream body = connection.getErrorStream()) {
                String text = body == null ? "" : new String(body.readAllBytes());
                assertThat(text).doesNotContain("root:", "<project", "quarkus.");
            }
        } finally {
            connection.disconnect();
        }
    }
}
