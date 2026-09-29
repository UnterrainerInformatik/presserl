package info.unterrainer.presserl.spellcheck;

import static info.unterrainer.presserl.spellcheck.SpellCheckResourceTest.check;
import static info.unterrainer.presserl.spellcheck.SpellCheckResourceTest.stubCalls;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import info.unterrainer.presserl.TestSupport;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * With {@code presserl.spell-check.enabled=false} checks answer {@code 503} without contacting
 * LanguageTool, and the client configuration says so.
 */
@QuarkusTest
@TestProfile(SpellCheckDisabledProfile.class)
class SpellCheckDisabledTest {

    @Test
    void checkIsUnavailableWithoutCallingLanguageTool() {
        TestSupport.awaitReady();
        int before = stubCalls();
        check(TestSupport.token("publisher", "publisher"), Map.of("text", "Der Hund ist gros.")).statusCode(503)
                .body("errors[0].field", nullValue());
        assertThat(stubCalls()).isEqualTo(before);
    }

    @Test
    void clientConfigReportsTheSpellCheckOff() {
        given().get("/api/client-config").then().statusCode(200).body("spellCheck", equalTo(false));
    }
}
