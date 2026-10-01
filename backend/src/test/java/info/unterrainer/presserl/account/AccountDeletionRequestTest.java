package info.unterrainer.presserl.account;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.time.Instant;
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

import info.unterrainer.presserl.TestSupport;
import info.unterrainer.presserl.auth.CurrentUser;
import info.unterrainer.presserl.bootstrap.KeycloakAdminProducer.KeycloakRealm;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;

/**
 * {@code POST}/{@code DELETE /api/me/deletion-request} and {@code deletionRequestedAt} in
 * {@code GET /api/me} and {@code GET /api/accounts}. The dev realm's {@code reader} holds only
 * {@code READER}, {@code nogroups} no role. Deletion requests and sections are deleted before and
 * after every test.
 */
@QuarkusTest
class AccountDeletionRequestTest {

    @Inject
    DataSource dataSource;

    @Inject
    Keycloak keycloak;

    @Inject
    KeycloakRealm keycloakRealm;

    private String reader;

    @BeforeEach
    void setUp() {
        TestSupport.awaitReady();
        TestSupport.deleteSections(dataSource);
        TestSupport.deleteDeletionRequests(dataSource);
        reader = TestSupport.token("reader", "reader");
    }

    @AfterEach
    void cleanUp() {
        TestSupport.deleteSections(dataSource);
        TestSupport.deleteDeletionRequests(dataSource);
    }

    private static RequestSpecification as(String token) {
        return given().auth().oauth2(token);
    }

    private static Instant request(String token) {
        return Instant.parse(as(token).post("/api/me/deletion-request").then().statusCode(200)
                .body("deletionRequestedAt", notNullValue())
                .extract().<String>path("deletionRequestedAt"));
    }

    @Test
    void withoutTokenIsUnauthorized() {
        given().post("/api/me/deletion-request").then().statusCode(401);
        given().delete("/api/me/deletion-request").then().statusCode(401);
    }

    @Test
    void meCarriesNoRequestByDefault() {
        as(reader).get("/api/me").then().statusCode(200).body("deletionRequestedAt", nullValue());
    }

    @Test
    void readerRequestsDeletionAndKeepsWorking() {
        Instant requested = request(reader);

        assertThat(requested).isBetween(Instant.now().minusSeconds(60), Instant.now().plusSeconds(1));
        as(reader).get("/api/me").then().statusCode(200)
                .body("deletionRequestedAt", equalTo(requested.toString()));
        as(TestSupport.token("reader", "reader")).get("/api/me").then().statusCode(200);
    }

    @Test
    void reporterRequestsDeletion() {
        long sport = as(TestSupport.token("publisher", "publisher")).contentType("application/json")
                .body("{\"name\": \"Sport\"}").post("/api/sections").then().statusCode(201).extract().jsonPath()
                .getLong("id");
        String id = keycloak.realm(keycloakRealm.name()).users().searchByUsername("reader", true).getFirst().getId();
        as(TestSupport.token("publisher", "publisher")).contentType("application/json")
                .body("{\"role\": \"REPORTER\"}").put("/api/sections/%d/members/%s".formatted(sport, id))
                .then().statusCode(200);

        request(TestSupport.token("reader", "reader"));
    }

    @Test
    void accountWithoutRoleRequestsDeletion() {
        request(TestSupport.token("nogroups", "nogroups"));
    }

    @Test
    void repeatedRequestKeepsTheFirstTime() throws InterruptedException {
        Instant first = request(reader);
        Thread.sleep(20);

        assertThat(request(reader)).isEqualTo(first);
    }

    @Test
    void withdrawRemovesTheRequest() {
        request(reader);

        as(reader).delete("/api/me/deletion-request").then().statusCode(200)
                .body("deletionRequestedAt", nullValue());
        as(reader).get("/api/me").then().body("deletionRequestedAt", nullValue());
        as(TestSupport.token("publisher", "publisher")).get("/api/accounts").then().statusCode(200)
                .body("accounts.find { it.username == 'reader' }.deletionRequestedAt", nullValue());
    }

    @Test
    void withdrawWithoutRequestIsIdempotent() {
        as(reader).delete("/api/me/deletion-request").then().statusCode(200)
                .body("deletionRequestedAt", nullValue());
    }

    @Test
    void accountListShowsTheRequest() {
        Instant requested = request(reader);

        as(TestSupport.token("publisher", "publisher")).get("/api/accounts").then().statusCode(200)
                .body("accounts.find { it.username == 'reader' }.deletionRequestedAt", equalTo(requested.toString()))
                .body("accounts.find { it.username == 'chief' }.deletionRequestedAt", nullValue());
    }

    @Test
    void serviceAccountIsRefused() {
        String serviceAccount = keycloak.tokenManager().getAccessTokenString();

        as(serviceAccount).post("/api/me/deletion-request").then().statusCode(anyOf(equalTo(401), equalTo(403)))
                .body(emptyOrNullString());
        as(serviceAccount).delete("/api/me/deletion-request").then().statusCode(anyOf(equalTo(401), equalTo(403)));
    }

    @Test
    void serviceAccountIsForbiddenEvenWithTheBackendsAudience() {
        CurrentUser serviceAccount = new CurrentUser("sub", "service-account-presserl-backend",
                "service-account-presserl-backend", List.of());
        CurrentUser user = new CurrentUser("sub", "reader", "Reader", List.of());

        assertThatThrownBy(() -> AccountDeletionRequestResource.requireNoServiceAccount(serviceAccount))
                .isInstanceOf(ForbiddenException.class);
        assertThat(AccountDeletionRequestResource.requireNoServiceAccount(user)).isSameAs(user);
    }

    @Test
    void requestAndWithdrawAreLogged() {
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
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(AccountDeletionRequestResource.class.getName());
        logger.addHandler(handler);
        try {
            request(reader);
            as(reader).delete("/api/me/deletion-request").then().statusCode(200);
        } finally {
            logger.removeHandler(handler);
        }

        assertThat(messages).contains("Account 'reader' requested its deletion",
                "Account 'reader' withdrew its deletion request");
    }
}
