package info.unterrainer.presserl;

import static io.restassured.RestAssured.given;

import java.time.Duration;

import org.eclipse.microprofile.config.ConfigProvider;

import io.restassured.response.Response;

/**
 * Tokens from the Dev Services Keycloak (dev realm, dev-only public clients) and readiness waiting.
 */
public final class TestSupport {

    public static final String HTTP_CLIENT = "presserl-http";
    public static final String NO_AUDIENCE_CLIENT = "presserl-noaud";

    private TestSupport() {
    }

    public static String issuer() {
        return ConfigProvider.getConfig().getValue("presserl.oidc.issuer", String.class);
    }

    public static Response passwordGrant(String clientId, String username, String password) {
        return given().formParam("grant_type", "password")
                .formParam("client_id", clientId)
                .formParam("username", username)
                .formParam("password", password)
                .post(issuer() + "/protocol/openid-connect/token");
    }

    public static String token(String clientId, String username, String password) {
        return passwordGrant(clientId, username, password).then().statusCode(200).extract().path("access_token");
    }

    public static String token(String username, String password) {
        return token(HTTP_CLIENT, username, password);
    }

    /**
     * Waits until {@code /q/health/ready} is UP, i.e. the publisher bootstrap has completed.
     */
    public static void awaitReady() {
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (given().get("/q/health/ready").statusCode() != 200) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("Backend not ready: " + given().get("/q/health/ready").asString());
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }
}
