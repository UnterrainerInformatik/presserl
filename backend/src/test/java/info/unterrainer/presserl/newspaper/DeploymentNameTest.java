package info.unterrainer.presserl.newspaper;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

/**
 * The newspaper name set by the deployment ({@code PRESSERL_NEWSPAPER_NAME}).
 */
@QuarkusTest
@TestProfile(DeploymentNameTest.Zwergenpost.class)
class DeploymentNameTest {

    public static class Zwergenpost implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("presserl.newspaper.name", "Die Zwergenpost");
        }
    }

    @Inject
    DataSource dataSource;

    @Test
    void deploymentValueOverridesCodeDefault() {
        given().get("/api/newspaper").then().statusCode(200).body("name", equalTo("Die Zwergenpost"));
    }

    @Test
    void readerMastheadShowsDeploymentName() {
        given().get("/").then().statusCode(200)
                .body(containsString("<h1 class=\"masthead__name\">Die Zwergenpost</h1>"));
    }

    @Test
    void databaseOverrideWinsOverDeploymentValue() throws SQLException {
        execute("UPDATE newspaper SET name = 'Zwergenpost Extra' WHERE id = 1");
        try {
            given().get("/api/newspaper").then().statusCode(200).body("name", equalTo("Zwergenpost Extra"));
        } finally {
            execute("UPDATE newspaper SET name = NULL WHERE id = 1");
        }
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }
}
