package info.unterrainer.presserl.account;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;

import org.junit.jupiter.api.Test;

import info.unterrainer.presserl.TestSupport;
import io.quarkus.test.junit.QuarkusTest;

/**
 * {@link KeycloakCalls} returns to the Vert.x event loop, so Panache works after a Keycloak call.
 */
@QuarkusTest
class KeycloakCallsTest {

    @Test
    void keycloakCallThenPanacheQueryInOneRequest() {
        TestSupport.awaitReady();
        given().get("/test/keycloak-then-panache").then().statusCode(200)
                .body(matchesPattern("\\d+ users, \\d+ sections, on .*"))
                .body(containsString("eventloop"));
    }
}
