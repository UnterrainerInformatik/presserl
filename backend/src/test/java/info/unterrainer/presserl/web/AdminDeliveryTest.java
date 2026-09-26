package info.unterrainer.presserl.web;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import info.unterrainer.presserl.TestSupport;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;

/**
 * Delivery of the admin bundle; the test classpath carries a stub {@code META-INF/resources/admin/index.html}.
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

    private static Map<String, String> directives(String policy) {
        assertThat(policy).isNotNull();
        return Arrays.stream(policy.split(";"))
                .map(String::trim)
                .map(d -> d.split(" ", 2))
                .collect(Collectors.toMap(d -> d[0], d -> d.length > 1 ? d[1] : ""));
    }
}
