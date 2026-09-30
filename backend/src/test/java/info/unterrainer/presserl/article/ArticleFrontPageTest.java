package info.unterrainer.presserl.article;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;

import info.unterrainer.presserl.TestSupport;
import info.unterrainer.presserl.bootstrap.KeycloakAdminProducer.KeycloakRealm;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;

/**
 * {@code readerVisible} of article representations and the front-page weight endpoint. Sections,
 * articles and issues are reset before and after every test; afterwards only issue 1 (not live) exists.
 */
@QuarkusTest
class ArticleFrontPageTest {

    @Inject
    DataSource dataSource;

    @Inject
    Keycloak keycloak;

    @Inject
    KeycloakRealm keycloakRealm;

    private String publisher;
    private String chief;
    private String reader;
    private long sport;

    @BeforeEach
    void ready() {
        TestSupport.awaitReady();
        TestSupport.deleteSections(dataSource);
        TestSupport.resetIssues(dataSource);
        publisher = TestSupport.token("publisher", "publisher");
        chief = TestSupport.token("chief", "chief");
        reader = TestSupport.token("reader", "reader");
        sport = as(publisher).body("{\"name\": \"Sport\"}").post("/api/sections").then().statusCode(201).extract()
                .jsonPath().getLong("id");
    }

    @AfterEach
    void cleanUp() {
        TestSupport.deleteSections(dataSource);
        TestSupport.resetIssues(dataSource);
    }

    private static RequestSpecification as(String token) {
        return given().auth().oauth2(token).contentType(ContentType.JSON);
    }

    private long create(String headline) {
        return as(publisher).body("{\"headline\": \"%s\", \"sectionId\": %d}".formatted(headline, sport))
                .post("/api/articles").then().statusCode(201).extract().jsonPath().getLong("id");
    }

    private long published(String headline) {
        long id = create(headline);
        as(publisher).post("/api/articles/%d/publish".formatted(id)).then().statusCode(200);
        return id;
    }

    private static JsonPath article(String token, long id) {
        return as(token).get("/api/articles/" + id).then().statusCode(200).extract().jsonPath();
    }

    private static ValidatableResponse weight(String token, long id, String body) {
        return as(token).body(body).put("/api/articles/%d/front-page-weight".formatted(id)).then();
    }

    private void execute(String sql) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void publishIssueOne() {
        execute("UPDATE issue SET published = true, published_at = now() WHERE number = 1");
    }

    // --- readerVisible

    @Test
    void draftIsNotReaderVisible() {
        long id = create("Draft");

        assertThat(article(publisher, id).getBoolean("readerVisible")).isFalse();
    }

    @Test
    void publishedInPlannedIssueWaits() {
        long id = published("Waiting");

        JsonPath json = article(publisher, id);
        assertThat(json.getString("status")).isEqualTo("PUBLISHED");
        assertThat(json.getInt("issue.number")).isEqualTo(1);
        assertThat(json.getBoolean("readerVisible")).isFalse();
    }

    @Test
    void publishedInLiveIssueIsReaderVisibleInDetailAndList() {
        long id = published("Live");
        publishIssueOne();

        assertThat(article(publisher, id).getBoolean("readerVisible")).isTrue();
        as(publisher).get("/api/articles").then().statusCode(200)
                .body("find { it.id == %d }.readerVisible".formatted(id), equalTo(true))
                .body("find { it.id == %d }.frontPageWeight".formatted(id), nullValue());
    }

    @Test
    void publishedWithoutIssueIsNotReaderVisible() {
        long id = published("Lost");
        publishIssueOne();
        execute("UPDATE article SET issue_id = NULL, issue_position = NULL WHERE id = " + id);

        JsonPath json = article(publisher, id);
        assertThat(json.getString("status")).isEqualTo("PUBLISHED");
        assertThat(json.getBoolean("readerVisible")).isFalse();
    }

    // --- front-page weight

    @Test
    void newArticlesHaveNoWeight() {
        assertThat(article(publisher, create("New")).get("frontPageWeight") == null).isTrue();
    }

    @Test
    void chiefWeightsPublishedArticleWithoutNewRevisionOrVersion() {
        long id = published("Lead story");
        JsonPath before = article(publisher, id);
        int revisions = as(publisher).get("/api/articles/%d/revisions".formatted(id)).then().statusCode(200).extract()
                .jsonPath().getList("$").size();

        weight(chief, id, "{\"weight\": 1}").statusCode(200)
                .body("frontPageWeight", equalTo(1))
                .body("status", equalTo("PUBLISHED"))
                .body("liveRevision", equalTo(before.getInt("liveRevision")))
                .body("revision", equalTo(before.getInt("revision")))
                .body("version", equalTo(before.getInt("version")))
                .body("updatedAt", equalTo(before.getString("updatedAt")));

        assertThat(article(publisher, id).getInt("frontPageWeight")).isEqualTo(1);
        assertThat(as(publisher).get("/api/articles/%d/revisions".formatted(id)).then().extract().jsonPath()
                .getList("$")).hasSize(revisions);
        as(publisher).get("/api/articles").then()
                .body("find { it.id == %d }.frontPageWeight".formatted(id), equalTo(1));
    }

    @Test
    void contentSaveWithTheVersionFromBeforeTheWeightSucceedsAndKeepsTheWeight() {
        long id = create("Draft");
        long version = article(publisher, id).getLong("version");
        weight(chief, id, "{\"weight\": 2}").statusCode(200);

        as(publisher).body("{\"headline\": \"Edited\", \"version\": %d}".formatted(version)).put("/api/articles/" + id)
                .then().statusCode(200)
                .body("frontPageWeight", equalTo(2));
    }

    @Test
    void publisherClearsTheWeight() {
        long id = published("Cleared");
        weight(publisher, id, "{\"weight\": 2}").statusCode(200);

        weight(publisher, id, "{\"weight\": null}").statusCode(200).body("frontPageWeight", nullValue());
        assertThat(article(publisher, id).get("frontPageWeight") == null).isTrue();
    }

    @Test
    void invalidWeightsAreRejectedNamingTheField() {
        long id = published("Invalid");

        for (String body : new String[] { "{\"weight\": 0}", "{\"weight\": 1000}", "{\"weight\": -1}",
                "{\"weight\": 1.5}", "{\"weight\": \"1\"}", "{}", "[1]", "{\"weight\": 99999999999}" }) {
            weight(chief, id, body).statusCode(400).body("errors.field", hasItem("weight"));
        }
        assertThat(article(publisher, id).get("frontPageWeight") == null).isTrue();
    }

    @Test
    void boundariesAreAccepted() {
        long id = published("Bounds");

        weight(chief, id, "{\"weight\": 999}").statusCode(200).body("frontPageWeight", equalTo(999));
        weight(chief, id, "{\"weight\": 1}").statusCode(200).body("frontPageWeight", equalTo(1));
    }

    @Test
    void sectionEditorMayNotWeight() {
        long id = published("Sport news");
        String account = keycloak.realm(keycloakRealm.name()).users().searchByUsername("reader", true).getFirst()
                .getId();
        as(publisher).body("{\"role\": \"SECTION_EDITOR\"}").put("/api/sections/%d/members/%s".formatted(sport, account))
                .then().statusCode(200);
        String editor = TestSupport.token("reader", "reader");

        weight(editor, id, "{\"weight\": 1}").statusCode(403);
        assertThat(article(publisher, id).get("frontPageWeight") == null).isTrue();
    }

    @Test
    void readerWithoutRoleGets403() {
        long id = published("Anything");

        weight(reader, id, "{\"weight\": 1}").statusCode(403);
    }

    @Test
    void unknownArticleIs404() {
        weight(chief, 999999, "{\"weight\": 1}").statusCode(404);
    }

    @Test
    void weightSurvivesGoingOfflineAndOnline() {
        long id = published("Comeback");
        weight(chief, id, "{\"weight\": 1}").statusCode(200);

        as(chief).post("/api/articles/%d/offline".formatted(id)).then().statusCode(200)
                .body("status", equalTo("OFFLINE")).body("frontPageWeight", equalTo(1));
        as(publisher).post("/api/articles/%d/publish".formatted(id)).then().statusCode(200)
                .body("status", equalTo("PUBLISHED")).body("frontPageWeight", equalTo(1));
    }
}
