package info.unterrainer.presserl.web;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import info.unterrainer.presserl.TestSupport;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;

/**
 * Delivery of the admin bundle; the test classpath carries stubs of {@code META-INF/resources/admin/index.html},
 * {@code composeApp.js} and a content-hashed {@code .wasm} module.
 */
@QuarkusTest
class AdminDeliveryTest {

    @Test
    void missingTrailingSlashRedirects() {
        given().redirects().follow(false).get("/admin").then()
                .statusCode(302)
                .header("Location", "/admin/");
    }

    @Test
    void adminAppIsServedWithAdminCsp() {
        Response response = given().get("/admin/");
        response.then().statusCode(200).contentType("text/html");

        Map<String, String> csp = directives(response.header("Content-Security-Policy"));
        URI issuer = URI.create(TestSupport.issuer());
        assertThat(csp).containsEntry("default-src", "'self'")
                .containsEntry("script-src", "'self' 'wasm-unsafe-eval'")
                .containsEntry("connect-src", "'self' " + issuer.getScheme() + "://" + issuer.getRawAuthority())
                // from the stub csp-style-hashes.txt; comments and malformed lines are skipped
                .containsEntry("style-src", "'self' 'sha256-dGVzdC1zdHlsZS1oYXNo'");
    }

    @Test
    void apiResponsesCarryReaderCsp() {
        assertThat(directives(given().get("/api/newspaper").header("Content-Security-Policy")))
                .containsEntry("default-src", "'self'")
                .doesNotContainKey("connect-src")
                .doesNotContainKey("style-src");
    }

    @ParameterizedTest
    @ValueSource(strings = { "/admin/", "/admin/composeApp.js" })
    void entryFilesAreRevalidated(String path) {
        given().get(path).then()
                .statusCode(200)
                .header("Cache-Control", "no-cache");
    }

    @Test
    void hashedWasmModuleIsCachedLongTerm() {
        given().get("/admin/0123456789abcdef0123.wasm").then()
                .statusCode(200)
                .header("Cache-Control", "public, max-age=31536000, immutable");
    }

    @Test
    void unchangedEntryFileAnswersNotModified() {
        String lastModified = given().get("/admin/composeApp.js").then()
                .statusCode(200)
                .extract().header("Last-Modified");
        assertThat(lastModified).isNotNull();

        given().header("If-Modified-Since", lastModified).get("/admin/composeApp.js").then()
                .statusCode(304)
                .header("Cache-Control", "no-cache");
    }

    @Test
    void apiCachingIsUnchanged() {
        assertThat(given().get("/api/newspaper").header("Cache-Control")).isNull();
    }

    private static Map<String, String> directives(String policy) {
        assertThat(policy).isNotNull();
        return Arrays.stream(policy.split(";"))
                .map(String::trim)
                .map(d -> d.split(" ", 2))
                .collect(Collectors.toMap(d -> d[0], d -> d.length > 1 ? d[1] : ""));
    }
}
