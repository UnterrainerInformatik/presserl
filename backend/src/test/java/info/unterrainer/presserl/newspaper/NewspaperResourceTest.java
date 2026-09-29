package info.unterrainer.presserl.newspaper;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import info.unterrainer.presserl.TestSupport;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;

@QuarkusTest
class NewspaperResourceTest {

    @Inject
    DataSource dataSource;

    @AfterEach
    void clearOverrides() throws SQLException {
        execute("UPDATE newspaper SET name = NULL, settings = '{}' WHERE id = 1");
    }

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
                .body("settings.'spell-check.help'", equalTo("suggestions"))
                .body("settings.size()", equalTo(6))
                .body("overrides", is(anEmptyMap()));
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

    @Test
    void overridesListOnlyValidOverridesOfSettings() throws SQLException {
        execute("UPDATE newspaper SET settings = '{\"visibility\": \"public\", \"reader.text-size\": \"xl\", "
                + "\"editor.level\": \"expert\"}' WHERE id = 1");

        given().get("/api/newspaper").then()
                .statusCode(200)
                .body("settings.'reader.text-size'", equalTo("xl"))
                .body("settings.'editor.level'", equalTo("standard"))
                .body("overrides.size()", equalTo(1))
                .body("overrides.'reader.text-size'", equalTo("xl"));
    }

    // --- PUT /api/newspaper/settings

    @Test
    void publisherSetsTheDefaultTextSize() {
        TestSupport.awaitReady();
        String response = put(TestSupport.token("publisher", "publisher"), "{\"reader.text-size\": \"l\"}")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("name", equalTo("My Newspaper"))
                .body("settings.'reader.text-size'", equalTo("l"))
                .body("overrides.size()", equalTo(1))
                .body("overrides.'reader.text-size'", equalTo("l"))
                .extract().asString();

        assertThat(given().get("/api/newspaper").then().statusCode(200).extract().asString()).isEqualTo(response);
    }

    @Test
    void editorInChiefClearsTheOverride() throws SQLException {
        TestSupport.awaitReady();
        execute("UPDATE newspaper SET settings = '{\"reader.text-size\": \"l\", \"editor.level\": \"profi\"}' "
                + "WHERE id = 1");

        put(TestSupport.token("chief", "chief"), "{\"reader.text-size\": null}").then()
                .statusCode(200)
                .body("settings.'reader.text-size'", equalTo("m"))
                // keys not in the body stay unchanged
                .body("settings.'editor.level'", equalTo("profi"))
                .body("overrides.size()", equalTo(1))
                .body("overrides.'editor.level'", equalTo("profi"));
        assertThat(storedSettings()).doesNotContain("reader.text-size").contains("editor.level");
    }

    @Test
    void emptyBodyChangesNothing() throws SQLException {
        TestSupport.awaitReady();
        execute("UPDATE newspaper SET settings = '{\"reader.text-size\": \"s\"}' WHERE id = 1");

        put(TestSupport.token("publisher", "publisher"), "{}").then()
                .statusCode(200)
                .body("overrides.'reader.text-size'", equalTo("s"));
    }

    @Test
    void invalidValueIsRefusedAndNothingIsStored() throws SQLException {
        TestSupport.awaitReady();
        execute("UPDATE newspaper SET settings = '{\"reader.text-size\": \"s\"}' WHERE id = 1");

        put(TestSupport.token("publisher", "publisher"), "{\"reader.text-size\": \"huge\"}").then()
                .statusCode(400)
                .body("errors.size()", equalTo(1))
                .body("errors[0].field", equalTo("reader.text-size"))
                .body("errors[0].message", equalTo("must be one of s, m, l, xl"));
        assertThat(storedSettings()).contains("\"reader.text-size\": \"s\"");
    }

    @Test
    void nonWritableKeyIsRefusedTogetherWithEveryOtherViolation() throws SQLException {
        TestSupport.awaitReady();

        put(TestSupport.token("publisher", "publisher"),
                "{\"reader.text-size\": \"l\", \"media.max-size\": \"50M\", \"size\": 3}").then()
                .statusCode(400)
                .body("errors.field", containsInAnyOrder("media.max-size", "size"))
                .body("errors.message", everyItem(equalTo("is not a writable setting")));
        // all or nothing: the valid key was not written either
        assertThat(storedSettings()).isEqualTo("{}");
    }

    @Test
    void nonStringValueIsRefused() {
        TestSupport.awaitReady();

        put(TestSupport.token("publisher", "publisher"), "{\"reader.text-size\": 2}").then()
                .statusCode(400)
                .body("errors[0].field", equalTo("reader.text-size"));
    }

    @Test
    void readerIsRefused() throws SQLException {
        TestSupport.awaitReady();

        put(TestSupport.token("reader", "reader"), "{\"reader.text-size\": \"l\"}").then()
                .statusCode(403)
                .body(equalTo(""));
        assertThat(storedSettings()).isEqualTo("{}");
    }

    @Test
    void publisherLimitsAndClearsTheSpellCheckHelp() throws SQLException {
        TestSupport.awaitReady();
        String publisher = TestSupport.token("publisher", "publisher");

        put(publisher, "{\"spell-check.help\": \"marks\"}").then()
                .statusCode(200)
                .body("settings.'spell-check.help'", equalTo("marks"))
                .body("overrides.size()", equalTo(1))
                .body("overrides.'spell-check.help'", equalTo("marks"));
        assertThat(storedSettings()).contains("\"spell-check.help\": \"marks\"");

        put(publisher, "{\"spell-check.help\": null}").then()
                .statusCode(200)
                .body("settings.'spell-check.help'", equalTo("suggestions"))
                .body("overrides", is(anEmptyMap()));
        assertThat(storedSettings()).isEqualTo("{}");
    }

    @Test
    void invalidSpellCheckHelpIsRefused() {
        TestSupport.awaitReady();

        put(TestSupport.token("publisher", "publisher"), "{\"spell-check.help\": \"hints\"}").then()
                .statusCode(400)
                .body("errors.size()", equalTo(1))
                .body("errors[0].field", equalTo("spell-check.help"))
                .body("errors[0].message", equalTo("must be one of suggestions, messages, marks"));
    }

    @Test
    void editorInChiefMayNotChooseTheSpellCheckHelp() throws SQLException {
        TestSupport.awaitReady();
        String chief = TestSupport.token("chief", "chief");
        execute("UPDATE newspaper SET settings = '{\"spell-check.help\": \"messages\"}' WHERE id = 1");

        put(chief, "{\"spell-check.help\": \"marks\"}").then()
                .statusCode(403)
                .body(equalTo(""));
        // all or nothing: the text size the editor-in-chief may write is not stored either
        put(chief, "{\"reader.text-size\": \"l\", \"spell-check.help\": \"suggestions\"}").then()
                .statusCode(403)
                .body(equalTo(""));
        put(chief, "{\"spell-check.help\": null}").then()
                .statusCode(403);
        assertThat(storedSettings()).isEqualTo("{\"spell-check.help\": \"messages\"}");
    }

    @Test
    void editorInChiefStillSetsTheTextSize() throws SQLException {
        TestSupport.awaitReady();

        put(TestSupport.token("chief", "chief"), "{\"reader.text-size\": \"l\"}").then()
                .statusCode(200)
                .body("overrides.'reader.text-size'", equalTo("l"));
        assertThat(storedSettings()).contains("\"reader.text-size\": \"l\"");
    }

    @Test
    void anonymousRequestIsRefused() {
        given().contentType(ContentType.JSON).body("{\"reader.text-size\": \"l\"}")
                .put("/api/newspaper/settings").then()
                .statusCode(401);
    }

    private static Response put(String token, String body) {
        return given().auth().oauth2(token).contentType(ContentType.JSON).body(body).put("/api/newspaper/settings");
    }

    private String storedSettings() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT settings::text FROM newspaper WHERE id = 1")) {
            result.next();
            return result.getString(1);
        }
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }
}
