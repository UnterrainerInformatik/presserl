package info.unterrainer.presserl.spellcheck;

import static info.unterrainer.presserl.spellcheck.SpellCheckResourceTest.check;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import info.unterrainer.presserl.TestSupport;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * With LanguageTool unreachable checks answer {@code 503} while readiness and the client
 * configuration are unaffected.
 */
@QuarkusTest
@TestProfile(SpellCheckDownProfile.class)
class SpellCheckDownTest {

    @Test
    void checkIsUnavailableAndReadinessStaysUp() {
        TestSupport.awaitReady();
        check(TestSupport.token("publisher", "publisher"), Map.of("text", "Der Hund ist gros.")).statusCode(503)
                .body("errors[0].field", nullValue());
        given().get("/q/health/ready").then().statusCode(200).body("status", equalTo("UP"));
        given().get("/api/client-config").then().statusCode(200).body("spellCheck", equalTo(true));
    }
}
