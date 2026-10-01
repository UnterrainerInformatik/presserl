package info.unterrainer.presserl.trust;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import javax.sql.DataSource;

import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;

import com.fasterxml.jackson.databind.ObjectMapper;

import info.unterrainer.presserl.TestSupport;
import info.unterrainer.presserl.account.AccountResource;
import info.unterrainer.presserl.bootstrap.KeycloakAdminProducer.KeycloakRealm;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;

/**
 * Trust over {@code PUT /api/accounts/{id}/trust}, the {@code trusts}/{@code trustScopes} account
 * fields and its effect on the approval chain. The dev realm has {@code chief}
 * ({@code EDITOR_IN_CHIEF}), {@code publisher}, {@code reader} ({@code READER}) and {@code nogroups}
 * (no role). Articles, sections (with section roles) and trust entries are deleted before and after
 * every test, and so is every account a test creates.
 */
@QuarkusTest
class TrustResourceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    DataSource dataSource;

    @Inject
    Keycloak keycloak;

    @Inject
    KeycloakRealm keycloakRealm;

    private String publisher;
    private String chief;
    private String reader;
    private final List<String> created = new ArrayList<>();

    @BeforeEach
    void ready() {
        TestSupport.awaitReady();
        TestSupport.deleteSections(dataSource);
        TestSupport.deleteTrust(dataSource);
        publisher = TestSupport.token("publisher", "publisher");
        chief = TestSupport.token("chief", "chief");
        reader = TestSupport.token("reader", "reader");
    }

    @AfterEach
    void cleanUp() {
        created.forEach(username -> realm().users().searchByUsername(username, true)
                .forEach(user -> realm().users().delete(user.getId()).close()));
        TestSupport.deleteSections(dataSource);
        TestSupport.deleteTrust(dataSource);
    }

    // --- helpers

    private RealmResource realm() {
        return keycloak.realm(keycloakRealm.name());
    }

    private String accountId(String username) {
        return realm().users().searchByUsername(username, true).getFirst().getId();
    }

    private String nogroups() {
        return TestSupport.token("nogroups", "nogroups");
    }

    private long section(String name) {
        return as(publisher).body("{\"name\": \"%s\"}".formatted(name)).post("/api/sections").then().statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    private void assign(long section, String username, String role) {
        as(publisher).body("{\"role\": \"%s\"}".formatted(role))
                .put("/api/sections/%d/members/%s".formatted(section, accountId(username))).then().statusCode(200);
    }

    /**
     * Sport with {@code reader} as reporter and {@code nogroups} as section editor.
     */
    private long staffedSport() {
        long sport = section("Sport");
        assign(sport, "reader", "REPORTER");
        assign(sport, "nogroups", "SECTION_EDITOR");
        return sport;
    }

    /**
     * A new account with {@code roles}, created by the publisher; returns its password.
     */
    private String createAccount(String username, String roles) {
        created.add(username);
        return as(publisher).body("""
                {"firstName": "%s", "username": "%s", "roles": %s}""".formatted(username, username, roles))
                .post("/api/accounts").then().statusCode(201).extract().path("password");
    }

    private static RequestSpecification as(String token) {
        return given().auth().oauth2(token).contentType(ContentType.JSON);
    }

    private static ValidatableResponse putTrust(String token, String accountId, String body) {
        return as(token).body(body).put("/api/accounts/%s/trust".formatted(accountId)).then();
    }

    private ValidatableResponse trust(String token, String username, String level, Long sectionId, boolean on) {
        return putTrust(token, accountId(username), """
                {"level": "%s", "sectionId": %s, "trusted": %s}""".formatted(level, sectionId, on));
    }

    private static JsonPath listedAs(String token, String username) {
        return new JsonPath(as(token).get("/api/accounts").then().statusCode(200).extract().asString())
                .setRootPath("accounts.find { it.username == '%s' }".formatted(username));
    }

    /**
     * The entries of a trust list as {@code LEVEL:sectionId}.
     */
    private static List<String> entries(JsonPath path, String field) {
        List<Map<String, Object>> list = path.getList(field);
        return list.stream().map(entry -> entry.get("level") + ":" + entry.get("sectionId")).toList();
    }

    private static List<String> entries(ValidatableResponse response, String field) {
        return entries(response.extract().jsonPath(), field);
    }

    private Instant setAt(String username) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT set_by, set_at FROM trust WHERE account_id = ?")) {
            statement.setString(1, accountId(username));
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("set_by")).isEqualTo(accountId("publisher"));
                return rows.getTimestamp("set_at").toInstant();
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    // --- article helpers

    private static long create(String token, long section, String headline) {
        return as(token).body(MAPPER.createObjectNode().put("headline", headline).put("sectionId", section).toString())
                .post("/api/articles").then().statusCode(201).extract().jsonPath().getLong("id");
    }

    private static ValidatableResponse get(String token, long id) {
        return as(token).get("/api/articles/" + id).then().statusCode(200);
    }

    private static ValidatableResponse action(String token, long id, String action) {
        return as(token).post("/api/articles/" + id + "/" + action).then();
    }

    // --- set and clear

    @Test
    void publisherTrustsTheEditorInChief() {
        ValidatableResponse response = trust(publisher, "chief", "PUBLISHER", null, true).statusCode(200)
                .body("username", equalTo("chief"))
                .body("allowedActions", contains("EDIT_ROLES", "RESET_PASSWORD", "LOCK", "DELETE"));
        assertThat(entries(response, "trusts")).containsExactly("PUBLISHER:null");
        assertThat(entries(response, "trustScopes")).containsExactly("PUBLISHER:null");
        assertThat(entries(listedAs(publisher, "chief"), "trusts")).containsExactly("PUBLISHER:null");
    }

    @Test
    void anotherPublisherClearsIt() {
        String password = createAccount("second-publisher", "[\"PUBLISHER\"]");
        trust(publisher, "chief", "PUBLISHER", null, true).statusCode(200);

        ValidatableResponse response = trust(TestSupport.token("second-publisher", password), "chief", "PUBLISHER",
                null, false).statusCode(200);
        assertThat(entries(response, "trusts")).isEmpty();
        assertThat(entries(response, "trustScopes")).containsExactly("PUBLISHER:null");
    }

    @Test
    void sectionEditorTrustsTheirReporter() {
        long sport = staffedSport();
        ValidatableResponse response = trust(nogroups(), "reader", "SECTION_EDITOR", sport, true).statusCode(200);
        assertThat(entries(response, "trusts")).containsExactly("SECTION_EDITOR:" + sport);
    }

    @Test
    void setTwiceKeepsTheOriginalSetterAndTime() {
        trust(publisher, "chief", "PUBLISHER", null, true).statusCode(200);
        Instant first = setAt("chief");

        ValidatableResponse response = trust(publisher, "chief", "PUBLISHER", null, true).statusCode(200);
        assertThat(entries(response, "trusts")).containsExactly("PUBLISHER:null");
        assertThat(setAt("chief")).isEqualTo(first);
    }

    @Test
    void clearingAMissingEntryIsIdempotent() {
        ValidatableResponse response = trust(publisher, "chief", "PUBLISHER", null, false).statusCode(200);
        assertThat(entries(response, "trusts")).isEmpty();
    }

    @Test
    void changesAreLoggedOnlyWhenSomethingChanged() {
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
        long sport = staffedSport();
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(AccountResource.class.getName());
        logger.addHandler(handler);
        try {
            trust(publisher, "chief", "PUBLISHER", null, true).statusCode(200);
            trust(publisher, "chief", "PUBLISHER", null, true).statusCode(200);
            trust(nogroups(), "reader", "SECTION_EDITOR", sport, true).statusCode(200);
            trust(publisher, "chief", "PUBLISHER", null, false).statusCode(200);
            trust(publisher, "chief", "PUBLISHER", null, false).statusCode(200);
        } finally {
            logger.removeHandler(handler);
        }

        assertThat(messages).containsExactly(
                "Trust of account 'chief' at PUBLISHER set by 'publisher'",
                "Trust of account 'reader' at SECTION_EDITOR in section " + sport + " set by 'nogroups'",
                "Trust of account 'chief' at PUBLISHER cleared by 'publisher'");
    }

    // --- refusals

    @Test
    void trustAtALevelThatIsNotTheOwn() {
        long sport = section("Sport");
        assign(sport, "reader", "REPORTER");
        trust(publisher, "reader", "EDITOR_IN_CHIEF", null, true).statusCode(403)
                .body("errors[0].message", equalTo("you may not change trust of account 'reader' at EDITOR_IN_CHIEF"));
        assertThat(entries(listedAs(publisher, "reader"), "trusts")).isEmpty();
    }

    @Test
    void sectionEditorOutsideTheirSection() {
        staffedSport();
        long kultur = section("Kultur");
        assign(kultur, "reader", "REPORTER");
        trust(nogroups(), "reader", "SECTION_EDITOR", kultur, true).statusCode(403);
    }

    @Test
    void trustOnOneself() {
        trust(chief, "chief", "EDITOR_IN_CHIEF", null, true).statusCode(403);
    }

    @Test
    void trustOnAPersonNotBelow() {
        createAccount("second-chief", "[\"EDITOR_IN_CHIEF\"]");
        trust(chief, "second-chief", "EDITOR_IN_CHIEF", null, true).statusCode(403);
        trust(chief, "publisher", "EDITOR_IN_CHIEF", null, true).statusCode(403);
    }

    @Test
    void readerWithoutAccountAccessGetsAnEmptyForbidden() {
        trust(reader, "chief", "PUBLISHER", null, true).statusCode(403).body(emptyOrNullString());
    }

    @Test
    void sectionMissing() {
        long sport = staffedSport();
        putTrust(nogroups(), accountId("reader"), """
                {"level": "SECTION_EDITOR", "trusted": true}""").statusCode(400)
                .body("errors.field", contains("sectionId"));
        trust(nogroups(), "reader", "SECTION_EDITOR", sport + 1000, true).statusCode(400)
                .body("errors.field", contains("sectionId"));
    }

    @Test
    void sectionWithANewspaperWideLevel() {
        long sport = section("Sport");
        trust(publisher, "chief", "PUBLISHER", sport, true).statusCode(400)
                .body("errors.field", contains("sectionId"));
    }

    @Test
    void invalidBodyNamesEveryField() {
        putTrust(publisher, accountId("chief"), """
                {"level": "REPORTER", "trusted": "yes", "extra": 1}""").statusCode(400)
                .body("errors.field", contains("extra", "level", "trusted"));
    }

    @Test
    void unknownAccount() {
        putTrust(publisher, "00000000-0000-0000-0000-000000000000", """
                {"level": "PUBLISHER", "trusted": true}""").statusCode(404);
    }

    // --- in the list

    @Test
    void trustInTheListOfASectionEditor() {
        staffedSport();
        trust(publisher, "chief", "PUBLISHER", null, true).statusCode(200);

        JsonPath chiefListed = listedAs(nogroups(), "chief");
        assertThat(entries(chiefListed, "trusts")).containsExactly("PUBLISHER:null");
        assertThat(entries(chiefListed, "trustScopes")).isEmpty();
    }

    @Test
    void publishersTrustScopes() {
        assign(section("Sport"), "reader", "REPORTER");
        assertThat(entries(listedAs(publisher, "chief"), "trustScopes")).containsExactly("PUBLISHER:null");
        assertThat(entries(listedAs(publisher, "reader"), "trustScopes")).containsExactly("PUBLISHER:null");
        assertThat(entries(listedAs(publisher, "nogroups"), "trustScopes")).isEmpty();
        assertThat(entries(listedAs(publisher, "publisher"), "trustScopes")).isEmpty();
    }

    @Test
    void editorInChiefsTrustScopes() {
        assign(section("Sport"), "reader", "REPORTER");
        assertThat(entries(listedAs(chief, "reader"), "trustScopes")).containsExactly("EDITOR_IN_CHIEF:null");
        assertThat(entries(listedAs(chief, "publisher"), "trustScopes")).isEmpty();
        assertThat(entries(listedAs(chief, "chief"), "trustScopes")).isEmpty();
    }

    @Test
    void sectionEditorsTrustScopes() {
        long sport = staffedSport();
        assign(section("Kultur"), "reader", "REPORTER");
        assertThat(entries(listedAs(nogroups(), "reader"), "trustScopes")).containsExactly("SECTION_EDITOR:" + sport);
    }

    @Test
    void editorInChiefDoesNotTrustForTheSectionEditorLevel() {
        long sport = section("Sport");
        assign(sport, "chief", "SECTION_EDITOR");
        assign(sport, "reader", "REPORTER");
        assertThat(entries(listedAs(chief, "reader"), "trustScopes")).containsExactly("EDITOR_IN_CHIEF:null");
    }

    @Test
    void otherAccountResponsesCarryTrust() {
        trust(publisher, "chief", "PUBLISHER", null, true).statusCode(200);
        String chiefId = accountId("chief");
        try {
            ValidatableResponse locked = as(publisher).post("/api/accounts/" + chiefId + "/lock").then()
                    .statusCode(200);
            assertThat(entries(locked, "trusts")).containsExactly("PUBLISHER:null");
            assertThat(entries(locked, "trustScopes")).containsExactly("PUBLISHER:null");
        } finally {
            as(publisher).post("/api/accounts/" + chiefId + "/unlock").then().statusCode(200);
        }
    }

    @Test
    void sectionDeleteDeletesItsTrust() {
        long kultur = section("Kultur");
        assign(kultur, "reader", "REPORTER");
        assign(kultur, "nogroups", "SECTION_EDITOR");
        trust(nogroups(), "reader", "SECTION_EDITOR", kultur, true).statusCode(200);

        as(publisher).delete("/api/sections/" + kultur).then().statusCode(204);
        assertThat(entries(listedAs(publisher, "reader"), "trusts")).isEmpty();
    }

    @Test
    void roleChangeKeepsTrust() {
        String chiefId = accountId("chief");
        trust(publisher, "chief", "PUBLISHER", null, true).statusCode(200);
        try {
            as(publisher).body("{\"roles\": [\"READER\"], \"sectionRoles\": []}")
                    .put("/api/accounts/%s/roles".formatted(chiefId)).then().statusCode(200);
        } finally {
            ValidatableResponse restored = as(publisher)
                    .body("{\"roles\": [\"EDITOR_IN_CHIEF\"], \"sectionRoles\": []}")
                    .put("/api/accounts/%s/roles".formatted(chiefId)).then().statusCode(200);
            assertThat(entries(restored, "trusts")).containsExactly("PUBLISHER:null");
        }
    }

    // --- the chain

    @Test
    void trustedEditorInChiefPublishesDirectly() {
        long sport = section("Sport");
        long id = create(chief, sport, "Direct");
        get(chief, id).body("allowedActions", hasItem("SUBMIT")).body("allowedActions", not(hasItem("PUBLISH")));

        trust(publisher, "chief", "PUBLISHER", null, true).statusCode(200);
        get(chief, id).body("allowedActions", hasItem("PUBLISH")).body("allowedActions", not(hasItem("SUBMIT")));
        action(chief, id, "publish").statusCode(200).body("status", equalTo("PUBLISHED"));
    }

    @Test
    void trustedSectionEditorLevelIsSkipped() {
        long sport = staffedSport();
        trust(nogroups(), "reader", "SECTION_EDITOR", sport, true).statusCode(200);
        long id = create(reader, sport, "Skip");
        action(reader, id, "submit").statusCode(200).body("pendingLevel", equalTo("EDITOR_IN_CHIEF"));
    }

    @Test
    void sectionTrustAppliesToItsSectionOnly() {
        long sport = staffedSport();
        long kultur = section("Kultur");
        assign(kultur, "reader", "REPORTER");
        assign(kultur, "nogroups", "SECTION_EDITOR");
        trust(nogroups(), "reader", "SECTION_EDITOR", sport, true).statusCode(200);

        long id = create(reader, kultur, "Other");
        action(reader, id, "submit").statusCode(200).body("pendingLevel", equalTo("SECTION_EDITOR"));
    }

    @Test
    void fullyTrustedReporterPublishesDirectly() {
        long sport = staffedSport();
        trust(nogroups(), "reader", "SECTION_EDITOR", sport, true).statusCode(200);
        trust(chief, "reader", "EDITOR_IN_CHIEF", null, true).statusCode(200);
        trust(publisher, "reader", "PUBLISHER", null, true).statusCode(200);

        long id = create(reader, sport, "Trusted");
        get(reader, id).body("allowedActions", hasItem("PUBLISH")).body("allowedActions", not(hasItem("SUBMIT")));
        action(reader, id, "publish").statusCode(200).body("status", equalTo("PUBLISHED"));
    }

    @Test
    void trustedLevelsAboveAreSkippedAfterAnApproval() {
        long sport = staffedSport();
        long id = create(reader, sport, "Approved");
        action(reader, id, "submit").statusCode(200).body("pendingLevel", equalTo("SECTION_EDITOR"));
        trust(chief, "reader", "EDITOR_IN_CHIEF", null, true).statusCode(200);

        action(nogroups(), id, "approve").statusCode(200).body("pendingLevel", equalTo("PUBLISHER"));
    }

    @Test
    void pendingArticleKeepsWaitingAndWithdrawThenOffersPublish() {
        long id = create(chief, section("Sport"), "Pending");
        action(chief, id, "submit").statusCode(200).body("pendingLevel", equalTo("PUBLISHER"));

        trust(publisher, "chief", "PUBLISHER", null, true).statusCode(200);
        get(chief, id).body("pendingLevel", equalTo("PUBLISHER"));
        get(publisher, id).body("allowedActions", hasItem("APPROVE"));

        action(chief, id, "withdraw").statusCode(200);
        get(chief, id).body("allowedActions", hasItem("PUBLISH")).body("allowedActions", not(hasItem("SUBMIT")));
    }

    @Test
    void lockOverridesTrust() {
        long sport = section("Sport");
        long id = create(chief, sport, "Locked");
        action(chief, id, "submit").statusCode(200);
        action(publisher, id, "approve").statusCode(200).body("status", equalTo("PUBLISHED"));
        action(publisher, id, "offline").statusCode(200).body("locked", equalTo(true));

        trust(publisher, "chief", "PUBLISHER", null, true).statusCode(200);
        get(chief, id).body("allowedActions", hasItem("SUBMIT")).body("allowedActions", not(hasItem("PUBLISH")));
        action(chief, id, "publish").statusCode(403);
        action(chief, id, "submit").statusCode(200).body("pendingLevel", equalTo("PUBLISHER"));
    }
}
