package info.unterrainer.presserl.web;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
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
 * {@code startup.js}, {@code styles.css}, {@code composeApp.js}, {@code favicon.svg}, {@code favicon.ico} and a
 * content-hashed {@code .wasm} module, and Brotli variants
 * ({@code brotli --best --keep}) of {@code composeApp.js} and the {@code .wasm} module.
 */
@QuarkusTest
class AdminDeliveryTest {

    private static final String WASM = "0123456789abcdef0123.wasm";
    private static final String BROWSER_ENCODINGS = "gzip, deflate, br";

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
    @ValueSource(strings = { "/admin/", "/admin/startup.js", "/admin/composeApp.js" })
    void entryFilesAreRevalidated(String path) {
        given().get(path).then()
                .statusCode(200)
                .header("Cache-Control", "no-cache");
    }

    @Test
    void faviconsAreServedWithImageTypesUnderTheAdminCsp() {
        for (String[] icon : new String[][] { { "/admin/favicon.svg", "image/svg+xml" },
                { "/admin/favicon.ico", "image/x-icon" } }) {
            Response response = given().get(icon[0]);

            assertThat(response.statusCode()).as(icon[0]).isEqualTo(200);
            assertThat(response.contentType()).as(icon[0]).startsWith(icon[1]);
            assertThat(response.header("Cache-Control")).as(icon[0]).isEqualTo("no-cache");
            assertThat(directives(response.header("Content-Security-Policy"))).as(icon[0])
                    .containsEntry("img-src", "'self' data:");
        }
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
    void wasmModuleIsSentBrotliCompressedWhenAccepted() {
        byte[] body = given().header("Accept-Encoding", BROWSER_ENCODINGS).get("/admin/" + WASM).then()
                .statusCode(200)
                .header("Content-Encoding", "br")
                .header("Content-Type", "application/wasm")
                .header("Vary", "Accept-Encoding")
                .header("Cache-Control", "public, max-age=31536000, immutable")
                .extract().asByteArray();
        assertThat(body).isEqualTo(resource(WASM + ".br"));
    }

    @Test
    void wasmModuleIsSentUncompressedWithoutBrotli() {
        Response response = given().header("Accept-Encoding", "gzip, deflate").get("/admin/" + WASM);
        response.then()
                .statusCode(200)
                .header("Content-Type", "application/wasm")
                .header("Vary", "Accept-Encoding")
                .header("Cache-Control", "public, max-age=31536000, immutable");
        assertThat(response.header("Content-Encoding")).isNull();
        assertThat(response.asByteArray()).isEqualTo(resource(WASM));
    }

    @Test
    void brotliWithZeroQualityIsNotAccepted() {
        Response response = given().header("Accept-Encoding", "gzip, br;q=0").get("/admin/" + WASM);
        assertThat(response.header("Content-Encoding")).isNull();
        assertThat(response.asByteArray()).isEqualTo(resource(WASM));
    }

    @Test
    void compressedEntryScriptIsRevalidated() {
        String uncompressedType = given().header("Accept-Encoding", "identity").get("/admin/composeApp.js")
                .header("Content-Type");
        byte[] body = given().header("Accept-Encoding", BROWSER_ENCODINGS).get("/admin/composeApp.js").then()
                .statusCode(200)
                .header("Content-Encoding", "br")
                .header("Content-Type", uncompressedType)
                .header("Vary", "Accept-Encoding")
                .header("Cache-Control", "no-cache")
                .extract().asByteArray();
        assertThat(uncompressedType).startsWith("text/javascript");
        assertThat(body).isEqualTo(resource("composeApp.js.br"));
    }

    @Test
    void unchangedCompressedEntryScriptAnswersNotModified() {
        String lastModified = given().header("Accept-Encoding", BROWSER_ENCODINGS).get("/admin/composeApp.js").then()
                .statusCode(200)
                .header("Content-Encoding", "br")
                .extract().header("Last-Modified");
        assertThat(lastModified).isNotNull();

        given().header("Accept-Encoding", BROWSER_ENCODINGS).header("If-Modified-Since", lastModified)
                .get("/admin/composeApp.js").then()
                .statusCode(304)
                .header("Cache-Control", "no-cache");
    }

    @ParameterizedTest
    @ValueSource(strings = { "/admin/styles.css", "/admin/startup.js", "/admin/" })
    void filesWithoutCompressedVariantAreUnchanged(String path) {
        Response response = given().header("Accept-Encoding", BROWSER_ENCODINGS).get(path);
        response.then().statusCode(200);
        assertThat(response.header("Content-Encoding")).isNull();
    }

    @Test
    void compressedResponsesCarryAdminCsp() {
        String policy = given().header("Accept-Encoding", BROWSER_ENCODINGS).get("/admin/" + WASM).then()
                .statusCode(200)
                .header("Content-Encoding", "br")
                .extract().header("Content-Security-Policy");
        assertThat(directives(policy)).containsEntry("script-src", "'self' 'wasm-unsafe-eval'");
    }

    @Test
    void apiCachingIsUnchanged() {
        assertThat(given().get("/api/newspaper").header("Cache-Control")).isNull();
    }

    private static byte[] resource(String name) {
        try (InputStream in = AdminDeliveryTest.class.getResourceAsStream("/META-INF/resources/admin/" + name)) {
            assertThat(in).as(name).isNotNull();
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<String, String> directives(String policy) {
        assertThat(policy).isNotNull();
        return Arrays.stream(policy.split(";"))
                .map(String::trim)
                .map(d -> d.split(" ", 2))
                .collect(Collectors.toMap(d -> d[0], d -> d.length > 1 ? d[1] : ""));
    }
}
