package info.unterrainer.presserl.account;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.matchesPattern;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
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
 * afterwards, so the dev realm keeps its four users; sections and section roles are deleted before
 * and after every test.
 */
@QuarkusTest
class AccountResourceTest {

    private static final String PASSWORD = "^[a-z]{3,8}(-[a-z]{3,8}){3}$";

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

    @BeforeEach
    void setUp() {
        TestSupport.awaitReady();
        TestSupport.deleteSections(dataSource);
        realm = keycloak.realm(keycloakRealm.name());
        publisher = TestSupport.token("publisher", "publisher");
        chief = TestSupport.token("chief", "chief");
    }

    @AfterEach
    void deleteCreatedUsers() {
        created.forEach(username -> realm.users().searchByUsername(username, true)
                .forEach(user -> realm.users().delete(user.getId()).close()));
        TestSupport.deleteSections(dataSource);
    }

    private long section(String name) {
        return as(publisher).body("{\"name\": \"%s\"}".formatted(name)).post("/api/sections").then()
                .statusCode(201).extract().jsonPath().getLong("id");
    }

    private void sectionRole(long section, String username, String role) {
        String id = realm.users().searchByUsername(username, true).getFirst().getId();
        as(publisher).body("{\"role\": \"%s\"}".formatted(role))
                .put("/api/sections/%d/members/%s".formatted(section, id)).then().statusCode(200);
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

    @Test
    void sectionEditorMayListWithoutAssignableRoles() {
        sectionRole(section("Sport"), "nogroups", "SECTION_EDITOR");

        as(TestSupport.token("nogroups", "nogroups")).get("/api/accounts").then().statusCode(200)
                .body("assignableRoles", empty())
                .body("mayAssignSectionlessReporter", equalTo(false));
        as(TestSupport.token("nogroups", "nogroups")).get("/api/accounts/username-suggestion?firstName=Anna")
                .then().statusCode(200);
    }

    @Test
    void reporterIsForbidden() {
        sectionRole(section("Sport"), "nogroups", "REPORTER");

        as(TestSupport.token("nogroups", "nogroups")).get("/api/accounts").then().statusCode(403)
                .body(emptyOrNullString());
    }

    // --- list -------------------------------------------------------------------------------

    @Test
    void listShowsSectionRolesByPosition() {
        long sport = section("Sport");
        long kultur = section("Kultur");
        sectionRole(kultur, "reader", "SECTION_EDITOR");
        sectionRole(sport, "reader", "REPORTER");

        JsonPath json = as(publisher).get("/api/accounts").then().statusCode(200).extract().jsonPath();

        assertThat(json.getList("accounts.find { it.username == 'reader' }.sectionRoles.sectionId", Long.class))
                .containsExactly(sport, kultur);
        assertThat(json.getList("accounts.find { it.username == 'reader' }.sectionRoles.role", String.class))
                .containsExactly("REPORTER", "SECTION_EDITOR");
        assertThat(json.getList("accounts.find { it.username == 'chief' }.sectionRoles")).isEmpty();
    }

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
                .body("assignableRoles", contains("PUBLISHER", "EDITOR_IN_CHIEF", "READER"))
                .body("mayAssignSectionlessReporter", equalTo(true))
                .body("accounts.sectionlessReporter", everyItem(equalTo(false)));
    }

    @Test
    void assignableRolesOfEditorInChief() {
        as(chief).get("/api/accounts").then().body("assignableRoles", contains("EDITOR_IN_CHIEF", "READER"))
                .body("mayAssignSectionlessReporter", equalTo(true));
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
                .body("account.sectionRoles", empty())
                .body("account.sectionlessReporter", equalTo(false))
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

    @Test
    void publisherCreatesAccountWithSectionRoleOnly() {
        long sport = section("Sport");
        long kultur = section("Kultur");

        io.restassured.response.Response response = post(publisher, "max", """
                {"firstName": "Max", "username": "max", "roles": [],
                 "sectionRoles": [{"sectionId": %d, "role": "SECTION_EDITOR"}, {"sectionId": %d, "role": "REPORTER"}]}"""
                .formatted(kultur, sport));

        response.then().statusCode(201)
                .body("account.roles", empty())
                .body("account.sectionRoles.sectionId", contains((int) sport, (int) kultur))
                .body("account.sectionRoles.role", contains("REPORTER", "SECTION_EDITOR"));
        as(TestSupport.token("max", response.path("password"))).get("/api/me").then().statusCode(200)
                .body("roles", empty())
                .body("sectionRoles.sectionName", contains("Sport", "Kultur"))
                .body("sectionRoles.role", contains("REPORTER", "SECTION_EDITOR"));
    }

    @Test
    void editorInChiefCreatesAPhotographer() {
        io.restassured.response.Response response = post(chief, "pia", """
                {"firstName": "Pia", "username": "pia", "roles": [], "sectionlessReporter": true}""");

        response.then().statusCode(201)
                .body("account.roles", empty())
                .body("account.sectionRoles", empty())
                .body("account.sectionlessReporter", equalTo(true));
        as(TestSupport.token("pia", response.path("password"))).get("/api/me").then().statusCode(200)
                .body("sectionlessReporter", equalTo(true))
                .body("allowedActions", contains("USE_MEDIA"));
        assertThat(listedAs(publisher, "pia").getBoolean("sectionlessReporter")).isTrue();
    }

    @Test
    void sectionEditorMayNotAssignTheMarker() {
        long sport = section("Sport");
        sectionRole(sport, "nogroups", "SECTION_EDITOR");

        post(TestSupport.token("nogroups", "nogroups"), "snapper", """
                {"firstName": "Snap", "username": "snapper", "roles": [],
                 "sectionRoles": [{"sectionId": %d, "role": "REPORTER"}], "sectionlessReporter": true}"""
                .formatted(sport))
                .then().statusCode(403).body("errors.field", contains("sectionlessReporter"));

        assertThat(realm.users().searchByUsername("snapper", true)).isEmpty();
    }

    @Test
    void markerMustBeABoolean() {
        post(publisher, "yesman", """
                {"firstName": "Yes", "username": "yesman", "roles": ["READER"], "sectionlessReporter": "yes"}""")
                .then().statusCode(400).body("errors.field", contains("sectionlessReporter"));
    }

    @Test
    void sectionEditorCreatesReporterOfOwnSection() {
        long sport = section("Sport");
        sectionRole(sport, "nogroups", "SECTION_EDITOR");

        post(TestSupport.token("nogroups", "nogroups"), "kiddo", """
                {"firstName": "Kiddo", "username": "kiddo", "roles": [],
                 "sectionRoles": [{"sectionId": %d, "role": "REPORTER"}]}""".formatted(sport))
                .then().statusCode(201).body("account.sectionRoles.role", contains("REPORTER"));
    }

    // --- refusals ---------------------------------------------------------------------------

    @Test
    void sectionEditorOutsideScope() {
        long sport = section("Sport");
        long kultur = section("Kultur");
        sectionRole(sport, "nogroups", "SECTION_EDITOR");

        post(TestSupport.token("nogroups", "nogroups"), "outsider", """
                {"firstName": "Out", "username": "outsider", "roles": [],
                 "sectionRoles": [{"sectionId": %d, "role": "REPORTER"}]}""".formatted(kultur))
                .then().statusCode(403).body("errors.field", contains("sectionRoles"));

        assertThat(realm.users().searchByUsername("outsider", true)).isEmpty();
    }

    @Test
    void sectionEditorMayNotAssignNewspaperRoles() {
        sectionRole(section("Sport"), "nogroups", "SECTION_EDITOR");

        post(TestSupport.token("nogroups", "nogroups"), "leser", "[\"READER\"]", "Leser").then().statusCode(403)
                .body("errors.field", contains("roles"));

        assertThat(realm.users().searchByUsername("leser", true)).isEmpty();
    }

    @Test
    void unknownSection() {
        post(publisher, "ghost", """
                {"firstName": "Ghost", "username": "ghost", "roles": [],
                 "sectionRoles": [{"sectionId": 999999, "role": "REPORTER"}]}""")
                .then().statusCode(400).body("errors.field", contains("sectionRoles"));

        assertThat(realm.users().searchByUsername("ghost", true)).isEmpty();
    }

    @Test
    void invalidSectionRoles() {
        long sport = section("Sport");

        post(publisher, "bad", """
                {"firstName": "Bad", "username": "bad", "roles": ["READER"],
                 "sectionRoles": [{"sectionId": %d, "role": "PUBLISHER"}, {"sectionId": %d, "role": "REPORTER"},
                                  {"sectionId": %d, "role": "REPORTER"}, "x"]}""".formatted(sport, sport, sport))
                .then().statusCode(400).body("errors.field", everyItem(equalTo("sectionRoles")))
                .body("errors.size()", equalTo(3));
        post(publisher, "bad", """
                {"firstName": "Bad", "username": "bad", "roles": ["READER"], "sectionRoles": {}}""")
                .then().statusCode(400).body("errors.field", contains("sectionRoles"));
    }

    @Test
    void noRoleOfEitherKind() {
        post(publisher, "norole", """
                {"firstName": "No", "username": "norole", "roles": [], "sectionRoles": []}""")
                .then().statusCode(400).body("errors.field", contains("roles"));
    }

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

    // --- allowed actions --------------------------------------------------------------------

    @Test
    void allowedActionsOfPublisher() {
        JsonPath json = as(publisher).get("/api/accounts").then().statusCode(200).extract().jsonPath();

        for (String username : List.of("chief", "reader", "nogroups")) {
            assertThat(json.getList("accounts.find { it.username == '%s' }.allowedActions".formatted(username),
                    String.class)).containsExactly("EDIT_ROLES", "RESET_PASSWORD", "LOCK");
        }
        assertThat(json.getList("accounts.find { it.username == 'publisher' }.allowedActions")).isEmpty();
    }

    @Test
    void allowedActionsOnLockedAccount() {
        String id = createReader("locky");
        as(publisher).post("/api/accounts/%s/lock".formatted(id)).then().statusCode(200);

        as(publisher).get("/api/accounts").then()
                .body("accounts.find { it.username == 'locky' }.allowedActions", contains("EDIT_ROLES", "RESET_PASSWORD", "UNLOCK"));
    }

    @Test
    void allowedActionsOfEditorInChief() {
        JsonPath json = as(chief).get("/api/accounts").then().statusCode(200).extract().jsonPath();

        assertThat(json.getList("accounts.find { it.username == 'reader' }.allowedActions", String.class))
                .containsExactly("EDIT_ROLES", "RESET_PASSWORD");
        assertThat(json.getList("accounts.find { it.username == 'nogroups' }.allowedActions", String.class))
                .containsExactly("EDIT_ROLES", "RESET_PASSWORD");
        assertThat(json.getList("accounts.find { it.username == 'chief' }.allowedActions")).isEmpty();
        assertThat(json.getList("accounts.find { it.username == 'publisher' }.allowedActions")).isEmpty();
    }

    @Test
    void createdAccountCarriesAllowedActionsOfCreator() {
        post(publisher, "fresh", "[\"READER\"]", "Fresh").then().statusCode(201)
                .body("account.allowedActions", contains("EDIT_ROLES", "RESET_PASSWORD", "LOCK"));
    }

    // --- password reset ---------------------------------------------------------------------

    @Test
    void publisherResetsPassword() {
        String oldPassword = createReaderWithPassword("resetme");
        String id = idOf("resetme");
        String refreshToken = refreshToken("resetme", oldPassword);

        String password = as(publisher).post("/api/accounts/%s/password-reset".formatted(id)).then().statusCode(200)
                .body("account.username", equalTo("resetme"))
                .body("account.enabled", equalTo(true))
                .body("account.allowedActions", contains("EDIT_ROLES", "RESET_PASSWORD", "LOCK"))
                .body("password", matchesPattern(PASSWORD))
                .extract().path("password");

        assertThat(password).isNotEqualTo(oldPassword);
        TestSupport.passwordGrant(TestSupport.HTTP_CLIENT, "resetme", password).then().statusCode(200);
        TestSupport.passwordGrant(TestSupport.HTTP_CLIENT, "resetme", oldPassword).then().statusCode(401);
        refresh(refreshToken).then().statusCode(400);
    }

    @Test
    void resetOfLockedAccountKeepsItLocked() {
        String id = createReader("lockedreset");
        as(publisher).post("/api/accounts/%s/lock".formatted(id)).then().statusCode(200);

        String password = as(publisher).post("/api/accounts/%s/password-reset".formatted(id)).then().statusCode(200)
                .body("account.enabled", equalTo(false))
                .extract().path("password");

        TestSupport.passwordGrant(TestSupport.HTTP_CLIENT, "lockedreset", password).then().statusCode(400);
    }

    @Test
    void publisherMayNotResetPublisher() {
        String password = createWithPassword("pub2", "[\"PUBLISHER\"]");

        as(publisher).post("/api/accounts/%s/password-reset".formatted(idOf("pub2"))).then().statusCode(403)
                .body("errors.message", hasItems(matchesPattern(".*reset the password of.*")));
        TestSupport.passwordGrant(TestSupport.HTTP_CLIENT, "pub2", password).then().statusCode(200);
    }

    @Test
    void editorInChiefMayNotResetEditorInChief() {
        createWithPassword("chief2", "[\"EDITOR_IN_CHIEF\"]");

        as(chief).post("/api/accounts/%s/password-reset".formatted(idOf("chief2"))).then().statusCode(403);
    }

    @Test
    void editorInChiefResetsReader() {
        as(chief).post("/api/accounts/%s/password-reset".formatted(createReader("kid"))).then().statusCode(200)
                .body("account.allowedActions", contains("EDIT_ROLES", "RESET_PASSWORD"));
    }

    @Test
    void sectionEditorResetsOwnReporter() {
        long sport = section("Sport");
        sectionRole(sport, "nogroups", "SECTION_EDITOR");
        String id = createReader("sportkid");
        sectionRole(sport, "sportkid", "REPORTER");

        as(TestSupport.token("nogroups", "nogroups")).post("/api/accounts/%s/password-reset".formatted(id)).then()
                .statusCode(200)
                .body("account.sectionRoles.role", contains("REPORTER"));
    }

    @Test
    void sectionEditorMayNotResetReporterAlsoInOtherSection() {
        long sport = section("Sport");
        long kultur = section("Kultur");
        sectionRole(sport, "nogroups", "SECTION_EDITOR");
        String id = createReader("twokid");
        sectionRole(sport, "twokid", "REPORTER");
        sectionRole(kultur, "twokid", "REPORTER");

        as(TestSupport.token("nogroups", "nogroups")).post("/api/accounts/%s/password-reset".formatted(id)).then()
                .statusCode(403);
    }

    @Test
    void sectionEditorMayNotResetPlainReader() {
        sectionRole(section("Sport"), "nogroups", "SECTION_EDITOR");

        as(TestSupport.token("nogroups", "nogroups")).post("/api/accounts/%s/password-reset".formatted(idOf("reader")))
                .then().statusCode(403);
    }

    @Test
    void nobodyResetsOwnPassword() {
        as(publisher).post("/api/accounts/%s/password-reset".formatted(idOf("publisher"))).then().statusCode(403);
        as(chief).post("/api/accounts/%s/password-reset".formatted(idOf("chief"))).then().statusCode(403);
    }

    @Test
    void readerMayNotReset() {
        as(TestSupport.token("reader", "reader")).post("/api/accounts/%s/password-reset".formatted(idOf("nogroups")))
                .then().statusCode(403);
    }

    @Test
    void unknownAndServiceAccountsAreNotFound() {
        String serviceAccountToken = keycloak.tokenManager().getAccessTokenString();
        String serviceAccount = new JsonPath(new String(Base64.getUrlDecoder().decode(
                serviceAccountToken.split("\\.")[1]), StandardCharsets.UTF_8)).getString("sub");

        for (String id : List.of("00000000-0000-0000-0000-000000000000", serviceAccount)) {
            as(publisher).post("/api/accounts/%s/password-reset".formatted(id)).then().statusCode(404)
                    .body("errors.size()", equalTo(1));
            as(publisher).post("/api/accounts/%s/lock".formatted(id)).then().statusCode(404);
            as(publisher).post("/api/accounts/%s/unlock".formatted(id)).then().statusCode(404);
            putRoles(publisher, id, "{\"roles\": [\"READER\"], \"sectionRoles\": []}").then().statusCode(404);
        }
    }

    // --- lock / unlock ----------------------------------------------------------------------

    @Test
    void publisherLocksAndUnlocks() {
        String password = createReaderWithPassword("locked");
        String id = idOf("locked");
        String refreshToken = refreshToken("locked", password);

        as(publisher).post("/api/accounts/%s/lock".formatted(id)).then().statusCode(200)
                .body("username", equalTo("locked"))
                .body("enabled", equalTo(false))
                .body("allowedActions", contains("EDIT_ROLES", "RESET_PASSWORD", "UNLOCK"));
        assertThat(realm.users().get(id).toRepresentation().isEnabled()).isFalse();
        TestSupport.passwordGrant(TestSupport.HTTP_CLIENT, "locked", password).then().statusCode(400);
        refresh(refreshToken).then().statusCode(400);

        as(publisher).post("/api/accounts/%s/unlock".formatted(id)).then().statusCode(200)
                .body("enabled", equalTo(true))
                .body("allowedActions", contains("EDIT_ROLES", "RESET_PASSWORD", "LOCK"));
        TestSupport.passwordGrant(TestSupport.HTTP_CLIENT, "locked", password).then().statusCode(200);
    }

    @Test
    void lockAndUnlockAreIdempotent() {
        String id = createReader("twice");

        as(publisher).post("/api/accounts/%s/lock".formatted(id)).then().statusCode(200);
        as(publisher).post("/api/accounts/%s/lock".formatted(id)).then().statusCode(200).body("enabled", equalTo(false));
        as(publisher).post("/api/accounts/%s/unlock".formatted(id)).then().statusCode(200);
        as(publisher).post("/api/accounts/%s/unlock".formatted(id)).then().statusCode(200).body("enabled", equalTo(true));
    }

    @Test
    void editorInChiefMayNotLock() {
        String readerId = idOf("reader");

        as(chief).post("/api/accounts/%s/lock".formatted(readerId)).then().statusCode(403)
                .body("errors.message", hasItems(matchesPattern(".*lock.*")));
        as(chief).post("/api/accounts/%s/unlock".formatted(readerId)).then().statusCode(403);
        assertThat(realm.users().get(readerId).toRepresentation().isEnabled()).isTrue();
    }

    @Test
    void publisherMayNotLockPublisherOrSelf() {
        createWithPassword("pub3", "[\"PUBLISHER\"]");

        as(publisher).post("/api/accounts/%s/lock".formatted(idOf("pub3"))).then().statusCode(403);
        as(publisher).post("/api/accounts/%s/lock".formatted(idOf("publisher"))).then().statusCode(403);
        assertThat(realm.users().get(idOf("pub3")).toRepresentation().isEnabled()).isTrue();
    }

    @Test
    void readerMayNotLock() {
        as(TestSupport.token("reader", "reader")).post("/api/accounts/%s/lock".formatted(idOf("nogroups")))
                .then().statusCode(403).body(emptyOrNullString());
    }

    // --- role edit --------------------------------------------------------------------------

    private static io.restassured.response.Response putRoles(String token, String id, String body) {
        return as(token).body(body).put("/api/accounts/%s/roles".formatted(id));
    }

    private List<String> groupsOf(String id) {
        return realm.users().get(id).groups().stream().map(GroupRepresentation::getName).toList();
    }

    private JsonPath listedAs(String token, String username) {
        return new JsonPath(as(token).get("/api/accounts").then().statusCode(200).extract().asString())
                .setRootPath("accounts.find { it.username == '%s' }".formatted(username));
    }

    @Test
    void publisherMakesReaderEditorInChief() {
        String password = createReaderWithPassword("promoted");
        String id = idOf("promoted");

        putRoles(publisher, id, """
                {"roles": ["EDITOR_IN_CHIEF", "READER"], "sectionRoles": []}""").then().statusCode(200)
                .body("id", equalTo(id))
                .body("username", equalTo("promoted"))
                .body("roles", contains("EDITOR_IN_CHIEF", "READER"))
                .body("sectionRoles", empty())
                .body("enabled", equalTo(true))
                .body("allowedActions", contains("EDIT_ROLES", "RESET_PASSWORD", "LOCK"));

        assertThat(groupsOf(id)).containsExactlyInAnyOrder("editor-in-chief", "reader");
        as(TestSupport.token("promoted", password)).get("/api/me").then().statusCode(200)
                .body("roles", contains("EDITOR_IN_CHIEF", "READER"));
    }

    @Test
    void publisherMovesReporterToAnotherSection() {
        long sport = section("Sport");
        long kultur = section("Kultur");
        String id = createReader("mover");
        sectionRole(sport, "mover", "REPORTER");

        putRoles(publisher, id, """
                {"roles": ["READER"], "sectionRoles": [{"sectionId": %d, "role": "REPORTER"}]}""".formatted(kultur))
                .then().statusCode(200)
                .body("roles", contains("READER"))
                .body("sectionRoles.sectionId", contains((int) kultur))
                .body("sectionRoles.role", contains("REPORTER"));

        JsonPath listed = listedAs(publisher, "mover");
        assertThat(listed.getList("sectionRoles.sectionId", Long.class)).containsExactly(kultur);
        assertThat(groupsOf(id)).containsExactly("reader");
    }

    @Test
    void roleEditIsLoggedOnlyWhenSomethingChanged() {
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
        String id = createReader("same");
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(AccountRoleEdit.class.getName());
        logger.addHandler(handler);
        try {
            putRoles(publisher, id, """
                    {"roles": ["READER"], "sectionRoles": []}""").then().statusCode(200)
                    .body("roles", contains("READER"));
            assertThat(messages).isEmpty();

            putRoles(publisher, id, """
                    {"roles": ["EDITOR_IN_CHIEF"], "sectionRoles": []}""").then().statusCode(200);
            putRoles(publisher, id, """
                    {"roles": ["EDITOR_IN_CHIEF"], "sectionRoles": []}""").then().statusCode(200);
        } finally {
            logger.removeHandler(handler);
        }

        assertThat(messages).containsExactly(
                "Roles of account 'same' changed by 'publisher': roles [READER] -> [EDITOR_IN_CHIEF], "
                        + "section roles [] -> [], sectionless reporter false -> false");
    }

    @Test
    void markerAloneIsARole() {
        String id = createReader("marked");

        putRoles(publisher, id, """
                {"roles": [], "sectionRoles": [], "sectionlessReporter": true}""").then().statusCode(200)
                .body("roles", empty())
                .body("sectionRoles", empty())
                .body("sectionlessReporter", equalTo(true));

        assertThat(groupsOf(id)).isEmpty();
        assertThat(listedAs(publisher, "marked").getBoolean("sectionlessReporter")).isTrue();
    }

    @Test
    void explicitRemovalOfEveryWritingRight() {
        long sport = section("Sport");
        String id = createReader("quitter");
        sectionRole(sport, "quitter", "REPORTER");

        putRoles(publisher, id, """
                {"roles": ["READER"], "sectionRoles": [], "sectionlessReporter": false}""").then().statusCode(200)
                .body("roles", contains("READER"))
                .body("sectionRoles", empty())
                .body("sectionlessReporter", equalTo(false));
    }

    @Test
    void lastSectionRemovedWithoutTheField() {
        long sport = section("Sport");
        sectionRole(sport, "nogroups", "SECTION_EDITOR");
        String id = createReader("leaver");
        sectionRole(sport, "leaver", "REPORTER");

        putRoles(TestSupport.token("nogroups", "nogroups"), id, """
                {"roles": ["READER"], "sectionRoles": []}""").then().statusCode(200)
                .body("sectionRoles", empty())
                .body("sectionlessReporter", equalTo(true));
        assertThat(listedAs(publisher, "leaver").getBoolean("sectionlessReporter")).isTrue();
    }

    @Test
    void sectionEditorMayNotRemoveTheMarker() {
        long sport = section("Sport");
        sectionRole(sport, "nogroups", "SECTION_EDITOR");
        String id = createReader("keepmark");
        sectionRole(sport, "keepmark", "REPORTER");
        putRoles(publisher, id, """
                {"roles": ["READER"], "sectionRoles": [{"sectionId": %d, "role": "REPORTER"}],
                 "sectionlessReporter": true}""".formatted(sport)).then().statusCode(200);

        putRoles(TestSupport.token("nogroups", "nogroups"), id, """
                {"roles": ["READER"], "sectionRoles": [{"sectionId": %d, "role": "REPORTER"}],
                 "sectionlessReporter": false}""".formatted(sport))
                .then().statusCode(403).body("errors.field", contains("sectionlessReporter"));
        assertThat(listedAs(publisher, "keepmark").getBoolean("sectionlessReporter")).isTrue();
    }

    @Test
    void noRoleLeftIsRejected() {
        String id = createReader("keeper");

        putRoles(publisher, id, """
                {"roles": [], "sectionRoles": []}""").then().statusCode(400).body("errors.field", contains("roles"));

        assertThat(groupsOf(id)).containsExactly("reader");
    }

    @Test
    void missingSectionRolesAreRejected() {
        String id = createReader("forgetful");

        putRoles(publisher, id, """
                {"roles": ["EDITOR_IN_CHIEF"]}""").then().statusCode(400)
                .body("errors.field", contains("sectionRoles"));

        assertThat(groupsOf(id)).containsExactly("reader");
    }

    @Test
    void unknownSectionLeavesAccountUnchanged() {
        String id = createReader("lost");

        putRoles(publisher, id, """
                {"roles": ["EDITOR_IN_CHIEF"], "sectionRoles": [{"sectionId": 999999, "role": "REPORTER"}]}""")
                .then().statusCode(400).body("errors.field", contains("sectionRoles"));

        assertThat(groupsOf(id)).containsExactly("reader");
    }

    @Test
    void nobodyEditsOwnRoles() {
        putRoles(publisher, idOf("publisher"), """
                {"roles": ["PUBLISHER", "READER"], "sectionRoles": []}""").then().statusCode(403)
                .body("errors.message", hasItems(matchesPattern(".*edit the roles of.*")));
        putRoles(chief, idOf("chief"), """
                {"roles": ["EDITOR_IN_CHIEF", "READER"], "sectionRoles": []}""").then().statusCode(403);
    }

    @Test
    void editorInChiefMayNotEditEditorInChief() {
        createWithPassword("chief4", "[\"EDITOR_IN_CHIEF\"]");
        String id = idOf("chief4");

        putRoles(chief, id, """
                {"roles": ["READER"], "sectionRoles": []}""").then().statusCode(403);

        assertThat(groupsOf(id)).containsExactly("editor-in-chief");
    }

    @Test
    void editorInChiefMayNotMakePublisher() {
        String id = createReader("climber");

        putRoles(chief, id, """
                {"roles": ["PUBLISHER", "READER"], "sectionRoles": []}""").then().statusCode(403)
                .body("errors.field", contains("roles"));

        assertThat(groupsOf(id)).containsExactly("reader");
    }

    @Test
    void sectionEditorMayNotEditPlainReader() {
        sectionRole(section("Sport"), "nogroups", "SECTION_EDITOR");

        putRoles(TestSupport.token("nogroups", "nogroups"), idOf("reader"), """
                {"roles": ["READER"], "sectionRoles": []}""").then().statusCode(403);
    }

    @Test
    void sectionEditorPromotesOwnReporterKeepingReader() {
        long sport = section("Sport");
        sectionRole(sport, "nogroups", "SECTION_EDITOR");
        String id = createReader("sporty");
        sectionRole(sport, "sporty", "REPORTER");

        putRoles(TestSupport.token("nogroups", "nogroups"), id, """
                {"roles": ["READER"], "sectionRoles": [{"sectionId": %d, "role": "SECTION_EDITOR"}]}""".formatted(sport))
                .then().statusCode(200)
                .body("roles", contains("READER"))
                .body("sectionRoles.role", contains("SECTION_EDITOR"))
                .body("allowedActions", empty());

        assertThat(groupsOf(id)).containsExactly("reader");
    }

    @Test
    void sectionEditorMayNotRemoveReader() {
        long sport = section("Sport");
        sectionRole(sport, "nogroups", "SECTION_EDITOR");
        String id = createReader("stays");
        sectionRole(sport, "stays", "REPORTER");

        putRoles(TestSupport.token("nogroups", "nogroups"), id, """
                {"roles": [], "sectionRoles": [{"sectionId": %d, "role": "REPORTER"}]}""".formatted(sport))
                .then().statusCode(403).body("errors.field", contains("roles"));

        assertThat(groupsOf(id)).containsExactly("reader");
    }

    @Test
    void sectionEditorMayNotAssignOutsideOwnSection() {
        long sport = section("Sport");
        long kultur = section("Kultur");
        sectionRole(sport, "nogroups", "SECTION_EDITOR");
        String id = createReader("fenced");
        sectionRole(sport, "fenced", "REPORTER");

        putRoles(TestSupport.token("nogroups", "nogroups"), id, """
                {"roles": ["READER"], "sectionRoles": [{"sectionId": %d, "role": "REPORTER"},
                                                       {"sectionId": %d, "role": "REPORTER"}]}"""
                .formatted(sport, kultur))
                .then().statusCode(403).body("errors.field", contains("sectionRoles"));

        assertThat(listedAs(publisher, "fenced").getList("sectionRoles.sectionId", Long.class))
                .containsExactly(sport);
    }

    private String idOf(String username) {
        return realm.users().searchByUsername(username, true).getFirst().getId();
    }

    private String createWithPassword(String username, String roles) {
        return post(publisher, username, roles, username).then().statusCode(201).extract().path("password");
    }

    private String createReaderWithPassword(String username) {
        return createWithPassword(username, "[\"READER\"]");
    }

    private String createReader(String username) {
        createReaderWithPassword(username);
        return idOf(username);
    }

    private static String refreshToken(String username, String password) {
        return TestSupport.passwordGrant(TestSupport.HTTP_CLIENT, username, password).then().statusCode(200)
                .extract().path("refresh_token");
    }

    private static io.restassured.response.Response refresh(String refreshToken) {
        return given().formParam("grant_type", "refresh_token")
                .formParam("client_id", TestSupport.HTTP_CLIENT)
                .formParam("refresh_token", refreshToken)
                .post(TestSupport.issuer() + "/protocol/openid-connect/token");
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
        long sport = section("Sport");
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(AccountCreation.class.getName());
        logger.addHandler(handler);
        String password;
        try {
            password = post(publisher, "logged", """
                    {"firstName": "Logged", "username": "logged", "roles": ["EDITOR_IN_CHIEF", "READER"],
                     "sectionRoles": [{"sectionId": %d, "role": "REPORTER"}]}""".formatted(sport))
                    .then().statusCode(201).extract().path("password");
        } finally {
            logger.removeHandler(handler);
        }

        assertThat(messages).anySatisfy(message -> assertThat(message).contains("'logged'", "'publisher'",
                "EDITOR_IN_CHIEF", "READER", "REPORTER", "sectionless reporter false"));
        assertThat(messages).allSatisfy(message -> assertThat(message).doesNotContain(password));
        assertThat(password).isNotBlank();
    }

    @Test
    void resetAndLockAreLoggedWithoutPassword() {
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
        String id = createReader("audited");
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(AccountResource.class.getName());
        logger.addHandler(handler);
        String password;
        try {
            password = as(publisher).post("/api/accounts/%s/password-reset".formatted(id)).then().statusCode(200)
                    .extract().path("password");
            as(publisher).post("/api/accounts/%s/lock".formatted(id)).then().statusCode(200);
            as(publisher).post("/api/accounts/%s/unlock".formatted(id)).then().statusCode(200);
        } finally {
            logger.removeHandler(handler);
        }

        assertThat(messages).contains("Password of account 'audited' reset by 'publisher'",
                "Account 'audited' locked by 'publisher'", "Account 'audited' unlocked by 'publisher'");
        assertThat(messages).allSatisfy(message -> assertThat(message).doesNotContain(password));
    }
}
