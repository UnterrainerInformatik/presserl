package info.unterrainer.presserl.account;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;

import info.unterrainer.presserl.TestSupport;
import info.unterrainer.presserl.bootstrap.KeycloakAdminProducer.KeycloakRealm;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;

/**
 * When deleting the Keycloak user fails, {@code DELETE /api/accounts/{id}} answers {@code 503} and
 * the database is rolled back: names, section roles, pending submissions and the deletion request
 * stay.
 */
@QuarkusTest
@TestProfile(FailingKeycloakDeleteProfile.class)
class AccountDeletionKeycloakDownTest {

    @Inject
    Keycloak keycloak;

    @Inject
    KeycloakRealm keycloakRealm;

    @Inject
    DataSource dataSource;

    private RealmResource realm;
    private String publisher;

    @BeforeEach
    void setUp() {
        TestSupport.awaitReady();
        TestSupport.deleteSections(dataSource);
        TestSupport.deleteDeletionRequests(dataSource);
        realm = keycloak.realm(keycloakRealm.name());
        publisher = TestSupport.token("publisher", "publisher");
    }

    @AfterEach
    void cleanUp() {
        realm.users().searchByUsername("survivor", true).forEach(user -> realm.users().delete(user.getId()).close());
        TestSupport.deleteSections(dataSource);
        TestSupport.deleteDeletionRequests(dataSource);
    }

    private static RequestSpecification as(String token) {
        return given().auth().oauth2(token).contentType(ContentType.JSON);
    }

    @Test
    void keycloakFailureRollsBack() {
        long sport = as(publisher).body("{\"name\": \"Sport\"}").post("/api/sections").then().statusCode(201)
                .extract().jsonPath().getLong("id");
        String password = as(publisher).body("""
                {"firstName": "Survivor", "username": "survivor", "roles": ["READER"],
                 "sectionRoles": [{"sectionId": %d, "role": "REPORTER"}]}""".formatted(sport))
                .post("/api/accounts").then().statusCode(201).extract().path("password");
        String id = realm.users().searchByUsername("survivor", true).getFirst().getId();
        String survivor = TestSupport.token("survivor", password);
        long article = as(survivor).body("{\"headline\": \"Story\", \"sectionId\": %d}".formatted(sport))
                .post("/api/articles").then().statusCode(201).extract().jsonPath().getLong("id");
        as(survivor).post("/api/articles/%d/submit".formatted(article)).then().statusCode(200);
        as(survivor).post("/api/me/deletion-request").then().statusCode(200);

        as(publisher).delete("/api/accounts/" + id).then().statusCode(503)
                .body("errors[0].message", equalTo(AccountException.UNAVAILABLE));

        assertThat(realm.users().searchByUsername("survivor", true)).hasSize(1);
        as(publisher).get("/api/articles/" + article).then().statusCode(200)
                .body("author.username", equalTo("survivor"))
                .body("status", equalTo("SUBMITTED"));
        as(publisher).get("/api/articles/%d/revisions".formatted(article)).then()
                .body("author.username[0]", equalTo("survivor"));
        assertThat(count("SELECT count(*) FROM section_role WHERE account_id = ?", id)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM account_deletion_request WHERE account_id = ?", id)).isEqualTo(1);
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
}
