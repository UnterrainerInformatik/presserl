package info.unterrainer.presserl.newspaper;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import info.unterrainer.presserl.TestSupport;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

@QuarkusTest
class NewspaperResourceTest {

    @Inject
    DataSource dataSource;

    @Test
    void anonymousRequestOnFreshInstallationReturnsDefaults() {
        given().get("/api/newspaper").then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("name", equalTo("My Newspaper"))
                .body("subtitle", equalTo(""))
                .body("visibility", equalTo("public"))
                .body("settings.'retract.author-can-retract'", equalTo(true))
                .body("settings.'section.default'", equalTo("General"))
                .body("settings.'editor.level'", equalTo("standard"))
                .body("settings.'reader.text-size'", equalTo("m"))
                .body("settings.'media.max-size'", equalTo("10M"))
                .body("settings.size()", equalTo(5));
    }

    @Test
    void authenticatedRequestIsIdenticalToAnonymousOne() {
        TestSupport.awaitReady();
        String anonymous = given().get("/api/newspaper").then().statusCode(200).extract().asString();

        String authenticated = given().auth().oauth2(TestSupport.token("publisher", "publisher"))
                .get("/api/newspaper").then().statusCode(200).extract().asString();

        assertThat(authenticated).isEqualTo(anonymous);
    }

    @Test
    void databaseOverrideWins() throws SQLException {
        execute("UPDATE newspaper SET name = 'Zwergenpost Extra', settings = '{\"editor.level\": \"profi\"}' WHERE id = 1");
        try {
            given().get("/api/newspaper").then()
                    .statusCode(200)
                    .body("name", equalTo("Zwergenpost Extra"))
                    .body("settings.'editor.level'", equalTo("profi"));
        } finally {
            execute("UPDATE newspaper SET name = NULL, settings = '{}' WHERE id = 1");
        }
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }
}
