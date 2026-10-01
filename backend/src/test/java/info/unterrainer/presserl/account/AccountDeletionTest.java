package info.unterrainer.presserl.account;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import javax.sql.DataSource;

import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.UserRepresentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import info.unterrainer.presserl.TestSupport;
import info.unterrainer.presserl.bootstrap.KeycloakAdminProducer.KeycloakRealm;
import info.unterrainer.presserl.media.MediaFixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;

/**
 * {@code DELETE /api/accounts/{id}}: who may delete, and what happens to the deleted account's content
 * (kept, names anonymised). The dev realm has {@code publisher}, {@code chief}
 * ({@code EDITOR_IN_CHIEF}), {@code reader} ({@code READER}) and {@code nogroups}; tests delete only
 * accounts they created. Articles, sections, trust entries, deletion requests and the media a test
 * uploads are removed before and after every test.
 */
@QuarkusTest
class AccountDeletionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    Keycloak keycloak;

    @Inject
    KeycloakRealm keycloakRealm;

    @Inject
    DataSource dataSource;

    private RealmResource realm;
    private String publisher;
    private String chief;
    private final List<String> created = new ArrayList<>();
    private final List<Long> media = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TestSupport.awaitReady();
        cleanDatabase();
        realm = keycloak.realm(keycloakRealm.name());
        publisher = TestSupport.token("publisher", "publisher");
        chief = TestSupport.token("chief", "chief");
    }

    @AfterEach
    void cleanUp() {
        created.forEach(username -> realm.users().searchByUsername(username, true)
                .forEach(user -> realm.users().delete(user.getId()).close()));
        cleanDatabase();
        media.forEach(id -> execute("DELETE FROM media WHERE id = " + id));
    }

    private void cleanDatabase() {
        TestSupport.deleteSections(dataSource);
        TestSupport.deleteTrust(dataSource);
        TestSupport.deleteDeletionRequests(dataSource);
    }

    // --- helpers

    private static RequestSpecification as(String token) {
        return given().auth().oauth2(token).contentType(ContentType.JSON);
    }

    /**
     * A new account created by the publisher; returns its password.
     */
    private String create(String username, String roles) {
        created.add(username);
        return as(publisher).body("""
                {"firstName": "%s", "username": "%s", "roles": %s}""".formatted(username, username, roles))
                .post("/api/accounts").then().statusCode(201).extract().path("password");
    }

    private String idOf(String username) {
        return realm.users().searchByUsername(username, true).getFirst().getId();
    }

    private static ValidatableResponse delete(String token, String id) {
        return as(token).delete("/api/accounts/" + id).then();
    }

    private static String requestDeletion(String username, String password) {
        String token = TestSupport.token(username, password);
        as(token).post("/api/me/deletion-request").then().statusCode(200);
        return token;
    }

    private long section(String name) {
        return as(publisher).body("{\"name\": \"%s\"}".formatted(name)).post("/api/sections").then().statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    private void assign(long section, String username, String role) {
        as(publisher).body("{\"role\": \"%s\"}".formatted(role))
                .put("/api/sections/%d/members/%s".formatted(section, idOf(username))).then().statusCode(200);
    }

    private static long article(String token, long section, String headline, Long leadImage) {
        ObjectNode content = MAPPER.createObjectNode().put("headline", headline).put("sectionId", section);
        if (leadImage != null) {
            content.putObject("leadImage").put("mediaId", leadImage);
        }
        return as(token).body(content.toString()).post("/api/articles").then().statusCode(201).extract().jsonPath()
                .getLong("id");
    }

    private static ValidatableResponse action(String token, long id, String action) {
        return as(token).post("/api/articles/" + id + "/" + action).then();
    }

    private long upload(String token) {
        long id = given().auth().oauth2(token).multiPart("file", "photo.jpg", MediaFixtures.jpeg(64, 48), "image/jpeg")
                .post("/api/media").then().statusCode(201).extract().jsonPath().getLong("id");
        media.add(id);
        return id;
    }

    private static List<String> listedUsernames(String token) {
        return as(token).get("/api/accounts").then().statusCode(200).extract().jsonPath().getList("accounts.username");
    }

    private long count(String sql, String parameter) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, parameter);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void execute(String sql) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void setEnabled(String username, boolean enabled) {
        UserRepresentation user = realm.users().get(idOf(username)).toRepresentation();
        user.setEnabled(enabled);
        realm.users().get(user.getId()).update(user);
    }

    // --- who may delete

    @Test
    void publisherDeletesAReporter() {
        String password = create("doomed", "[\"READER\"]");
        assign(section("Sport"), "doomed", "REPORTER");
        String id = idOf("doomed");

        delete(publisher, id).statusCode(204);

        assertThat(listedUsernames(publisher)).doesNotContain("doomed");
        assertThat(realm.users().searchByUsername("doomed", true)).isEmpty();
        TestSupport.passwordGrant(TestSupport.HTTP_CLIENT, "doomed", password).then().statusCode(401);
        assertThat(count("SELECT count(*) FROM section_role WHERE account_id = ?", id)).isZero();
    }

    @Test
    void deletionWithRequestRemovesTheRequest() {
        String password = create("asked", "[\"READER\"]");
        requestDeletion("asked", password);
        String id = idOf("asked");

        delete(publisher, id).statusCode(204);

        assertThat(count("SELECT count(*) FROM account_deletion_request WHERE account_id = ?", id)).isZero();
    }

    @Test
    void lockedAccountIsDeleted() {
        create("frozen", "[\"READER\"]");
        String id = idOf("frozen");
        as(publisher).post("/api/accounts/%s/lock".formatted(id)).then().statusCode(200);

        delete(publisher, id).statusCode(204);
    }

    @Test
    void editorInChiefMayNotDelete() {
        create("spared", "[\"READER\"]");

        delete(chief, idOf("spared")).statusCode(403)
                .body("errors.message", hasItems(matchesPattern(".*delete account 'spared'.*")));
        assertThat(realm.users().searchByUsername("spared", true)).hasSize(1);
    }

    @Test
    void readerMayNotDelete() {
        create("spared2", "[\"READER\"]");

        delete(TestSupport.token("reader", "reader"), idOf("spared2")).statusCode(403);
    }

    @Test
    void nobodyDeletesTheirOwnAccount() {
        requestDeletion("publisher", "publisher");

        delete(publisher, idOf("publisher")).statusCode(403);
        assertThat(realm.users().searchByUsername("publisher", true)).hasSize(1);
    }

    @Test
    void publisherWithoutRequestIsNotDeleted() {
        create("pubkeep", "[\"PUBLISHER\"]");

        as(publisher).get("/api/accounts").then()
                .body("accounts.find { it.username == 'pubkeep' }.allowedActions", empty());
        delete(publisher, idOf("pubkeep")).statusCode(403);
        assertThat(realm.users().searchByUsername("pubkeep", true)).hasSize(1);
    }

    @Test
    void publisherWhoRequestedDeletionIsDeleted() {
        String password = create("pubgone", "[\"PUBLISHER\"]");
        requestDeletion("pubgone", password);

        as(publisher).get("/api/accounts").then()
                .body("accounts.find { it.username == 'pubgone' }.allowedActions", contains("DELETE"));
        delete(publisher, idOf("pubgone")).statusCode(204);
        assertThat(realm.users().searchByUsername("pubgone", true)).isEmpty();
    }

    @Test
    void lastRemainingPublisherIsNotDeleted() {
        String password = create("puba", "[\"PUBLISHER\"]");
        requestDeletion("puba", password);
        // the requester's access token stays valid after locking, its account is disabled
        String lockedPublisher = TestSupport.token("publisher", "publisher");
        setEnabled("publisher", false);
        try {
            delete(lockedPublisher, idOf("puba")).statusCode(403);
            as(lockedPublisher).get("/api/accounts").then()
                    .body("accounts.find { it.username == 'puba' }.allowedActions", empty());
        } finally {
            setEnabled("publisher", true);
        }
        assertThat(realm.users().searchByUsername("puba", true)).hasSize(1);
    }

    @Test
    void unknownAndServiceAccountsAreNotFound() {
        String serviceAccount = realm.users().searchByUsername("service-account-presserl-backend", true).getFirst()
                .getId();

        delete(publisher, "00000000-0000-0000-0000-000000000000").statusCode(404);
        delete(publisher, serviceAccount).statusCode(404);
    }

    @Test
    void withoutTokenIsUnauthorized() {
        given().delete("/api/accounts/" + idOf("reader")).then().statusCode(401);
    }

    @Test
    void deletionIsLogged() {
        create("logged", "[\"READER\"]");
        String id = idOf("logged");
        List<String> messages = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record instanceof ExtLogRecord ext) {
                    messages.add(ext.getFormattedMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(AccountResource.class.getName());
        logger.addHandler(handler);
        try {
            delete(publisher, id).statusCode(204);
        } finally {
            logger.removeHandler(handler);
        }

        assertThat(messages).contains("Account 'logged' deleted by 'publisher'");
    }

    // --- content of the deleted account

    @Test
    void contentIsKeptAndAnonymised() {
        long sport = section("Sport");
        String gonePassword = create("gone", "[\"READER\"]");
        assign(sport, "gone", "REPORTER");
        assign(sport, "reader", "REPORTER");
        String chiefPassword = create("oldchief", "[\"EDITOR_IN_CHIEF\", \"READER\"]");
        String gone = TestSupport.token("gone", gonePassword);
        String oldChief = TestSupport.token("oldchief", chiefPassword);
        String goneId = idOf("gone");
        String oldChiefId = idOf("oldchief");

        long image = upload(gone);
        long published = article(gone, sport, "Live story", image);
        action(gone, published, "submit").statusCode(200);
        action(oldChief, published, "approve").statusCode(200);
        action(publisher, published, "approve").statusCode(200).body("status", equalTo("PUBLISHED"));
        long waiting = article(gone, sport, "Waiting story", null);
        action(gone, waiting, "submit").statusCode(200).body("status", equalTo("SUBMITTED"));
        as(chief).get("/api/articles?awaitingMe=true").then().statusCode(200).body("id", contains((int) waiting));
        as(oldChief).body("""
                {"level": "EDITOR_IN_CHIEF", "sectionId": null, "trusted": true}""")
                .put("/api/accounts/%s/trust".formatted(idOf("reader"))).then().statusCode(200);

        delete(publisher, goneId).statusCode(204);
        delete(publisher, oldChiefId).statusCode(204);

        as(publisher).get("/api/articles/" + published).then().statusCode(200)
                .body("status", equalTo("PUBLISHED"))
                .body("liveRevision", equalTo(1))
                .body("author.username", nullValue())
                .body("author.displayName", nullValue())
                .body("lastEditor.username", nullValue())
                .body("leadImage.mediaId", equalTo((int) image));
        as(publisher).get("/api/articles/%d/revisions".formatted(published)).then().statusCode(200)
                .body("author.username", contains((Object) null))
                .body("author.displayName", contains((Object) null));
        JsonPath reviews = as(publisher).get("/api/articles/%d/reviews".formatted(published)).then().statusCode(200)
                .extract().jsonPath();
        assertThat(reviews.getList("level", String.class)).containsExactly("PUBLISHER", "EDITOR_IN_CHIEF");
        assertThat(reviews.getList("revision", Integer.class)).containsExactly(1, 1);
        assertThat(reviews.getList("reviewer.username")).containsExactly("publisher", null);
        assertThat(reviews.getList("reviewer.displayName")).containsExactly("publisher", null);
        as(publisher).get("/api/articles?status=PUBLISHED").then().statusCode(200)
                .body("find { it.id == %d }.author.username".formatted(published), nullValue());

        as(publisher).get("/api/articles/" + waiting).then().statusCode(200)
                .body("status", equalTo("DRAFT"))
                .body("pendingLevel", nullValue())
                .body("author.username", nullValue());
        as(chief).get("/api/articles?awaitingMe=true").then().statusCode(200).body("id", empty());
        as(publisher).get("/api/articles?awaitingMe=true").then().statusCode(200).body("id", empty());

        as(publisher).get("/api/media/" + image).then().statusCode(200)
                .body("uploadedBy.username", nullValue())
                .body("uploadedBy.displayName", nullValue());
        as(publisher).get("/api/media/%d/content".formatted(image)).then().statusCode(200);

        assertThat(count("SELECT count(*) FROM trust WHERE account_id = ? AND level = 'EDITOR_IN_CHIEF'",
                idOf("reader"))).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM trust WHERE set_by = ?", oldChiefId)).isEqualTo(1);
    }

    @Test
    void trustAndMarkerOnTheDeletedAccountGo() {
        create("trusted", "[\"READER\"]");
        assign(section("Sport"), "trusted", "REPORTER");
        String id = idOf("trusted");
        as(publisher).body("""
                {"level": "PUBLISHER", "sectionId": null, "trusted": true}""")
                .put("/api/accounts/%s/trust".formatted(id)).then().statusCode(200);
        execute("INSERT INTO sectionless_reporter VALUES ('%s', 'x', now())".formatted(id));

        delete(publisher, id).statusCode(204);

        assertThat(count("SELECT count(*) FROM trust WHERE account_id = ?", id)).isZero();
        assertThat(count("SELECT count(*) FROM sectionless_reporter WHERE account_id = ?", id)).isZero();
    }

    @Test
    void eraseIsIdempotent() {
        long sport = section("Sport");
        String password = create("twice", "[\"READER\"]");
        assign(sport, "twice", "REPORTER");
        String id = idOf("twice");
        long article = article(TestSupport.token("twice", password), sport, "Story", null);

        delete(publisher, id).statusCode(204);
        long version = as(publisher).get("/api/articles/" + article).then().extract().jsonPath().getLong("version");
        given().post("/test/erase/" + id).then().statusCode(204);
        given().post("/test/erase/" + id).then().statusCode(204);

        as(publisher).get("/api/articles/" + article).then().statusCode(200)
                .body("author.username", nullValue())
                .body("version", equalTo((int) version));
    }
}
