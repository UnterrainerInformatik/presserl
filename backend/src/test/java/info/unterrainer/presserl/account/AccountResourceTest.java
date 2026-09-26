package info.unterrainer.presserl.account;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.matchesPattern;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import info.unterrainer.presserl.TestSupport;
import info.unterrainer.presserl.bootstrap.KeycloakAdminProducer.KeycloakRealm;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;

/**
 * Account endpoints against the Dev Services Keycloak. Every user a test creates is deleted
 * afterwards, so the dev realm keeps its four users.
 */
@QuarkusTest
class AccountResourceTest {

    private static final String PASSWORD = "^[a-z]{3,8}(-[a-z]{3,8}){3}$";

    @Inject
    Keycloak keycloak;

    @Inject
    KeycloakRealm keycloakRealm;

    private RealmResource realm;
    private String publisher;
    private String chief;
    private final List<String> created = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TestSupport.awaitReady();
        realm = keycloak.realm(keycloakRealm.name());
        publisher = TestSupport.token("publisher", "publisher");
        chief = TestSupport.token("chief", "chief");
    }

    @AfterEach
    void deleteCreatedUsers() {
        created.forEach(username -> realm.users().searchByUsername(username, true)
                .forEach(user -> realm.users().delete(user.getId()).close()));
    }

    private static RequestSpecification as(String token) {
        return given().auth().oauth2(token).contentType(ContentType.JSON);
    }

    private io.restassured.response.Response post(String token, String username, String body) {
        created.add(username);
        return as(token).body(body).post("/api/accounts");
    }

    private io.restassured.response.Response post(String token, String username, String roles, String firstName) {
        return post(token, username, """
                {"firstName": "%s", "username": "%s", "roles": %s}""".formatted(firstName, username, roles));
    }

    // --- access -----------------------------------------------------------------------------

    @Test
    void withoutTokenIsUnauthorized() {
        given().get("/api/accounts").then().statusCode(401);
    }

    @Test
    void readerIsForbidden() {
        as(TestSupport.token("reader", "reader")).get("/api/accounts").then().statusCode(403);
        as(TestSupport.token("reader", "reader")).get("/api/accounts/username-suggestion?firstName=Anna")
                .then().statusCode(403);
    }

    @Test
    void publisherAndEditorInChiefMayList() {
        as(publisher).get("/api/accounts").then().statusCode(200);
        as(chief).get("/api/accounts").then().statusCode(200);
    }

    // --- list -------------------------------------------------------------------------------

    @Test
    void publisherListsDevRealmAccountsSortedWithoutServiceAccounts() {
        JsonPath json = as(publisher).get("/api/accounts").then().statusCode(200).extract().jsonPath();

        List<String> usernames = json.getList("accounts.username");
        assertThat(usernames).contains("chief", "nogroups", "publisher", "reader")
                .isSorted()
                .noneMatch(username -> username.startsWith("service-account-"));
        assertThat(json.getList("accounts.find { it.username == 'chief' }.roles", String.class))
                .containsExactly("EDITOR_IN_CHIEF");
        assertThat(json.getList("accounts.find { it.username == 'reader' }.roles", String.class))
                .containsExactly("READER");
        assertThat(json.getList("accounts.find { it.username == 'nogroups' }.roles", String.class)).isEmpty();
        assertThat(json.getList("accounts.find { it.username == 'publisher' }.roles", String.class))
                .containsExactly("PUBLISHER");
        assertThat(json.getString("accounts.find { it.username == 'chief' }.firstName")).isEqualTo("Chief");
        assertThat(json.getString("accounts.find { it.username == 'chief' }.lastName")).isEqualTo("Editor");
        assertThat(json.getString("accounts.find { it.username == 'publisher' }.lastName")).isEmpty();
        assertThat(json.getBoolean("accounts.find { it.username == 'chief' }.enabled")).isTrue();
        assertThat(json.getString("accounts.find { it.username == 'chief' }.id")).isNotBlank();
    }

    @Test
    void assignableRolesOfPublisher() {
        as(publisher).get("/api/accounts").then()
                .body("assignableRoles", contains("PUBLISHER", "EDITOR_IN_CHIEF", "READER"));
    }

    @Test
    void assignableRolesOfEditorInChief() {
        as(chief).get("/api/accounts").then().body("assignableRoles", contains("EDITOR_IN_CHIEF", "READER"));
    }

    // --- username suggestion ----------------------------------------------------------------

    @Test
    void suggestionFoldsUmlautAndSpace() {
        as(publisher).queryParam("firstName", "Jürgen Maria").get("/api/accounts/username-suggestion").then()
                .statusCode(200).body("username", equalTo("juergen-maria"));
    }

    @Test
    void suggestionSkipsTakenUsernames() {
        post(publisher, "anna", "[\"READER\"]", "Anna").then().statusCode(201);
        post(publisher, "anna-2", "[\"READER\"]", "Anna").then().statusCode(201);

        as(publisher).queryParam("firstName", "Anna").get("/api/accounts/username-suggestion").then()
                .statusCode(200).body("username", equalTo("anna-3"));
    }

    @Test
    void suggestionForShortFirstNameIsNumbered() {
        as(publisher).queryParam("firstName", "Li").get("/api/accounts/username-suggestion").then()
                .statusCode(200).body("username", equalTo("li-1"));
    }

    @Test
    void suggestionWithNothingUsableIsUser() {
        as(publisher).queryParam("firstName", "李").get("/api/accounts/username-suggestion").then()
                .statusCode(200).body("username", equalTo("user"));
    }

    @Test
    void suggestionWithoutFirstNameIsRejected() {
        as(publisher).get("/api/accounts/username-suggestion").then()
                .statusCode(400).body("errors.field", contains("firstName"));
        as(publisher).queryParam("firstName", "  ").get("/api/accounts/username-suggestion").then()
                .statusCode(400).body("errors.field", contains("firstName"));
    }

    // --- create -----------------------------------------------------------------------------

    @Test
    void publisherCreatesEditorInChiefWhoCanLogIn() {
        io.restassured.response.Response response = post(publisher, "lena", """
                {"firstName": " Lena ", "username": "lena", "roles": ["EDITOR_IN_CHIEF"]}""");

        response.then().statusCode(201)
                .header("Location", matchesPattern(".*/api/accounts/[0-9a-f-]+$"))
                .body("account.username", equalTo("lena"))
                .body("account.firstName", equalTo("Lena"))
                .body("account.lastName", equalTo(""))
                .body("account.roles", contains("EDITOR_IN_CHIEF"))
                .body("account.enabled", equalTo(true))
                .body("password", matchesPattern(PASSWORD));
        assertThat(response.header("Location")).endsWith("/api/accounts/" + response.path("account.id"));

        String token = TestSupport.token("lena", response.path("password"));
        as(token).get("/api/me").then().statusCode(200).body("roles", contains("EDITOR_IN_CHIEF"));
        as(publisher).get("/api/accounts").then()
                .body("accounts.find { it.username == 'lena' }.roles", contains("EDITOR_IN_CHIEF"));
    }

    @Test
    void severalRolesAreJoinedInRoleOrder() {
        post(publisher, "multi", """
                {"firstName": "Multi", "lastName": "Role", "username": "multi", "roles": ["READER", "PUBLISHER", "READER"]}""")
                .then().statusCode(201).body("account.roles", contains("PUBLISHER", "READER"))
                .body("account.lastName", equalTo("Role"));

        UserRepresentation user = realm.users().searchByUsername("multi", true).getFirst();
        assertThat(realm.users().get(user.getId()).groups()).extracting(GroupRepresentation::getName)
                .containsExactlyInAnyOrder("publisher", "reader");
    }

    @Test
    void twoCreationsYieldDifferentPasswords() {
        String first = post(publisher, "first", "[\"READER\"]", "First").then().statusCode(201)
                .extract().path("password");
        String second = post(publisher, "second", "[\"READER\"]", "Second").then().statusCode(201)
                .extract().path("password");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void editorInChiefCreatesReader() {
        post(chief, "kid", "[\"READER\"]", "Kid").then().statusCode(201).body("account.roles", contains("READER"));
    }

    // --- refusals ---------------------------------------------------------------------------

    @Test
    void invalidUsernameAndNoRolesAreReportedTogether() {
        post(publisher, "max mustermann", """
                {"firstName": "Max", "username": "Max Mustermann", "roles": []}""")
                .then().statusCode(400).body("errors.field", contains("username", "roles"));

        assertThat(realm.users().searchByUsername("max mustermann", true)).isEmpty();
    }

    @Test
    void tooShortUsernameIsRejected() {
        post(publisher, "li", "[\"READER\"]", "Li").then().statusCode(400)
                .body("errors.field", contains("username"))
                .body("errors.message", contains("must be at least 3 characters"));

        assertThat(realm.users().searchByUsername("li", true)).isEmpty();
    }

    @Test
    void everyViolationIsListed() {
        post(publisher, "service-account-x", """
                {"firstName": "  ", "lastName": "%s", "username": "service-account-x", "roles": ["EDITOR"], "email": "x@y"}"""
                .formatted("x".repeat(101)))
                .then().statusCode(400)
                .body("errors.field", hasItems("email", "firstName", "lastName", "username", "roles"));
    }

    @Test
    void takenUsernameIsConflict() {
        String chiefId = realm.users().searchByUsername("chief", true).getFirst().getId();

        as(publisher).body("""
                {"firstName": "Other", "username": "chief", "roles": ["READER"]}""").post("/api/accounts")
                .then().statusCode(409).body("errors.field", contains("username"));

        UserRepresentation chiefUser = realm.users().get(chiefId).toRepresentation();
        assertThat(chiefUser.getFirstName()).isEqualTo("Chief");
        assertThat(realm.users().get(chiefId).groups()).extracting(GroupRepresentation::getName)
                .containsExactly("editor-in-chief");
    }

    @Test
    void editorInChiefMayNotCreatePublisher() {
        post(chief, "boss", "[\"PUBLISHER\"]", "Boss").then().statusCode(403).body("errors.field", contains("roles"));

        assertThat(realm.users().searchByUsername("boss", true)).isEmpty();
    }

    @Test
    void readerMayNotCreate() {
        post(TestSupport.token("reader", "reader"), "sneaky", "[\"READER\"]", "Sneaky").then().statusCode(403)
                .body(emptyOrNullString());
    }

    // --- logging ----------------------------------------------------------------------------

    @Test
    void creationIsLoggedWithoutPassword() {
        List<String> messages = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                messages.add(record instanceof ExtLogRecord ext ? ext.getFormattedMessage() : record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(AccountService.class.getName());
        logger.addHandler(handler);
        String password;
        try {
            password = post(publisher, "logged", "[\"EDITOR_IN_CHIEF\", \"READER\"]", "Logged").then().statusCode(201)
                    .extract().path("password");
        } finally {
            logger.removeHandler(handler);
        }

        assertThat(messages).anySatisfy(message -> assertThat(message).contains("'logged'", "'publisher'",
                "EDITOR_IN_CHIEF", "READER"));
        assertThat(messages).allSatisfy(message -> assertThat(message).doesNotContain(password));
        assertThat(password).isNotBlank();
    }
}
