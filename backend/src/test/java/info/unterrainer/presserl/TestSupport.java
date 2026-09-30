package info.unterrainer.presserl;

import static io.restassured.RestAssured.given;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import javax.sql.DataSource;

import org.eclipse.microprofile.config.ConfigProvider;

import io.restassured.response.Response;

/**
 * Tokens from the Dev Services Keycloak (dev realm, dev-only public clients), readiness waiting and
 * database clean-up.
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
     * Deletes every article (a section with articles cannot be deleted) and every section, and with
     * them every section role, and every sectionless-reporter marker, so the dev users hold none.
     */
    public static void deleteSections(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM article");
            statement.executeUpdate("DELETE FROM section");
            statement.executeUpdate("DELETE FROM sectionless_reporter");
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Restores the issues of a fresh installation: only issue 1, not published, without date and
     * without articles.
     */
    public static void resetIssues(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE article SET issue_id = NULL, issue_position = NULL");
            statement.executeUpdate("DELETE FROM issue");
            statement.executeUpdate("INSERT INTO issue (number) VALUES (1)");
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Deletes every trust entry (section entries also go with their sections).
     */
    public static void deleteTrust(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM trust");
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
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
