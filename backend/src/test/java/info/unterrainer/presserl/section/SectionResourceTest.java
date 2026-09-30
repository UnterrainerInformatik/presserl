package info.unterrainer.presserl.section;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItems;
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

import info.unterrainer.presserl.TestSupport;
import info.unterrainer.presserl.bootstrap.KeycloakAdminProducer.KeycloakRealm;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;

/**
 * Section and member endpoints. Sections (and with them all section roles) are deleted before and
 * after every test, so the dev users hold no section role outside this class.
 */
@QuarkusTest
class SectionResourceTest {

    @Inject
    DataSource dataSource;

    @Inject
    Keycloak keycloak;

    @Inject
    KeycloakRealm keycloakRealm;

    private RealmResource realm;
    private String publisher;
    private String chief;
    private String reader;
    private String nogroups;

    @BeforeEach
    void setUp() {
        TestSupport.awaitReady();
        TestSupport.deleteSections(dataSource);
        realm = keycloak.realm(keycloakRealm.name());
        publisher = TestSupport.token("publisher", "publisher");
        chief = TestSupport.token("chief", "chief");
        reader = TestSupport.token("reader", "reader");
        nogroups = TestSupport.token("nogroups", "nogroups");
    }

    @AfterEach
    void deleteSections() {
        TestSupport.deleteSections(dataSource);
    }

    private static RequestSpecification as(String token) {
        return given().auth().oauth2(token).contentType(ContentType.JSON);
    }

    private static long create(String token, String name) {
        return as(token).body("{\"name\": \"%s\"}".formatted(name)).post("/api/sections").then().statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    private String accountId(String username) {
        return realm.users().searchByUsername(username, true).getFirst().getId();
    }

    private ValidatableResponse assign(String token, long section, String username, String role) {
        return as(token).body("{\"role\": \"%s\"}".formatted(role))
                .put("/api/sections/%d/members/%s".formatted(section, accountId(username))).then();
    }

    // --- list -------------------------------------------------------------------------------

    @Test
    void withoutTokenIsUnauthorized() {
        given().get("/api/sections").then().statusCode(401);
    }

    @Test
    void noSectionsYet() {
        as(publisher).get("/api/sections").then().statusCode(200)
                .body("canManage", equalTo(true))
                .body("sections", empty());
    }

    @Test
    void publisherListsSectionsInOrderWithAllAssignableRoles() {
        create(publisher, "Sport");
        create(publisher, "Kultur");

        as(publisher).get("/api/sections").then().statusCode(200)
                .body("canManage", equalTo(true))
                .body("sections.name", contains("Sport", "Kultur"))
                .body("sections.position", contains(0, 1))
                .body("sections[0].assignableRoles", contains("SECTION_EDITOR", "REPORTER"))
                .body("sections[1].assignableRoles", contains("SECTION_EDITOR", "REPORTER"));
    }

    @Test
    void readerListsSectionsWithoutAssignableRoles() {
        create(publisher, "Sport");

        as(reader).get("/api/sections").then().statusCode(200)
                .body("canManage", equalTo(false))
                .body("sections.name", contains("Sport"))
                .body("sections[0].assignableRoles", empty())
                .body("sections[0].articleCounts", nullValue());
    }

    @Test
    void articleCounts() {
        TestSupport.resetIssues(dataSource);
        long sport = create(publisher, "Sport");
        create(publisher, "Kultur");
        long first = article(sport);
        long second = article(sport);
        long offline = article(sport);
        article(sport);
        for (long id : List.of(first, second, offline)) {
            as(publisher).post("/api/articles/%d/publish".formatted(id)).then().statusCode(200);
        }
        as(publisher).post("/api/articles/%d/offline".formatted(offline)).then().statusCode(200);
        try {
            long issueOne = issueId(1);
            long issueTwo = newIssue(2);
            execute("UPDATE issue SET published = true, published_at = now() WHERE id = " + issueTwo);
            execute("UPDATE article SET issue_id = %d, issue_position = 0 WHERE id IN (%d, %d)"
                    .formatted(issueTwo, first, second));
            execute("UPDATE article SET issue_id = %d, issue_position = 0 WHERE id = %d".formatted(issueOne, offline));

            as(publisher).get("/api/sections").then().statusCode(200)
                    .body("sections[0].articleCounts.live", equalTo(2))
                    .body("sections[0].articleCounts.total", equalTo(4))
                    .body("sections[0].articleCounts.issues.issueId", contains((int) issueTwo, (int) issueOne))
                    .body("sections[0].articleCounts.issues.number", contains(2, 1))
                    .body("sections[0].articleCounts.issues.count", contains(2, 1))
                    .body("sections[1].articleCounts.live", equalTo(0))
                    .body("sections[1].articleCounts.total", equalTo(0))
                    .body("sections[1].articleCounts.issues", empty());
        } finally {
            TestSupport.resetIssues(dataSource);
        }
    }

    @Test
    void articlesWaitingForTheirIssueAreNotLive() {
        TestSupport.resetIssues(dataSource);
        long sport = create(publisher, "Sport");
        long live = article(sport);
        long waiting = article(sport);
        for (long id : List.of(live, waiting)) {
            as(publisher).post("/api/articles/%d/publish".formatted(id)).then().statusCode(200);
        }
        try {
            long issueOne = issueId(1);
            execute("UPDATE issue SET published = true, published_at = now() WHERE id = " + issueOne);
            long issueTwo = newIssue(2);
            execute("UPDATE article SET issue_id = %d, issue_position = 0 WHERE id = %d".formatted(issueOne, live));
            execute("UPDATE article SET issue_id = %d, issue_position = 0 WHERE id = %d".formatted(issueTwo, waiting));

            as(publisher).get("/api/sections").then().statusCode(200)
                    .body("sections[0].articleCounts.live", equalTo(1))
                    .body("sections[0].articleCounts.total", equalTo(2));
        } finally {
            TestSupport.resetIssues(dataSource);
        }
    }

    private long article(long section) {
        return as(publisher).body("{\"headline\": \"Counted\", \"sectionId\": %d}".formatted(section))
                .post("/api/articles").then().statusCode(201).extract().jsonPath().getLong("id");
    }

    @Test
    void sectionEditorAssignsInOwnSectionOnly() {
        long sport = create(publisher, "Sport");
        create(publisher, "Kultur");
        assign(publisher, sport, "nogroups", "SECTION_EDITOR").statusCode(200);

        as(nogroups).get("/api/sections").then().statusCode(200)
                .body("canManage", equalTo(false))
                .body("sections.find { it.name == 'Sport' }.assignableRoles", contains("SECTION_EDITOR", "REPORTER"))
                .body("sections.find { it.name == 'Kultur' }.assignableRoles", empty());
    }

    @Test
    void publisherMayWriteInEverySection() {
        create(publisher, "Sport");
        create(publisher, "Kultur");

        as(publisher).get("/api/sections").then().statusCode(200)
                .body("sections.canWrite", contains(true, true));
    }

    @Test
    void readerMayWriteNowhere() {
        create(publisher, "Sport");
        create(publisher, "Kultur");

        as(reader).get("/api/sections").then().statusCode(200)
                .body("sections.canWrite", contains(false, false));
    }

    @Test
    void reporterMayWriteInTheirSectionOnly() {
        long sport = create(publisher, "Sport");
        create(publisher, "Kultur");
        assign(publisher, sport, "reader", "REPORTER").statusCode(200);

        as(reader).get("/api/sections").then().statusCode(200)
                .body("sections.name", contains("Sport", "Kultur"))
                .body("sections.canWrite", contains(true, false));
    }

    // --- create -----------------------------------------------------------------------------

    @Test
    void editorInChiefCreatesSectionWithSlugAndDefaultColour() {
        io.restassured.response.Response response = as(chief).body("{\"name\": \" Sport & Spiel \"}")
                .post("/api/sections");

        response.then().statusCode(201)
                .body("name", equalTo("Sport & Spiel"))
                .body("slug", equalTo("sport-spiel"))
                .body("color", equalTo("red"))
                .body("position", equalTo(0))
                .body("assignableRoles", contains("SECTION_EDITOR", "REPORTER"));
        assertThat(response.header("Location")).endsWith("/api/sections/" + response.jsonPath().getLong("id"));
    }

    @Test
    void defaultColourFollowsThePaletteAndGivenColourWins() {
        create(publisher, "Eins");
        as(publisher).body("{\"name\": \"Zwei\"}").post("/api/sections").then().statusCode(201)
                .body("color", equalTo("orange")).body("position", equalTo(1));
        as(publisher).body("{\"name\": \"Drei\", \"color\": \"teal\"}").post("/api/sections").then().statusCode(201)
                .body("color", equalTo("teal")).body("position", equalTo(2));
    }

    @Test
    void slugCollisionIsNumbered() {
        create(publisher, "Sport");

        as(publisher).body("{\"name\": \"Sport!\"}").post("/api/sections").then().statusCode(201)
                .body("slug", equalTo("sport-2"));
    }

    @Test
    void readerMayNotCreate() {
        as(reader).body("{\"name\": \"Sport\"}").post("/api/sections").then().statusCode(403)
                .body(emptyOrNullString());

        as(publisher).get("/api/sections").then().body("sections", empty());
    }

    @Test
    void sectionEditorMayNotCreate() {
        long sport = create(publisher, "Sport");
        assign(publisher, sport, "nogroups", "SECTION_EDITOR").statusCode(200);

        as(nogroups).body("{\"name\": \"Kultur\"}").post("/api/sections").then().statusCode(403);
    }

    @Test
    void blankName() {
        as(publisher).body("{\"name\": \"  \"}").post("/api/sections").then().statusCode(400)
                .body("errors.field", contains("name"));
    }

    @Test
    void everyViolationIsListed() {
        as(publisher).body("{\"name\": \"%s\", \"color\": \"#ff0000\", \"slug\": \"x\"}".formatted("x".repeat(41)))
                .post("/api/sections").then().statusCode(400)
                .body("errors.field", hasItems("name", "color", "slug"));
        as(publisher).body("{\"name\": \"A\\nB\"}").post("/api/sections").then().statusCode(400)
                .body("errors.field", contains("name"));
    }

    @Test
    void unknownColour() {
        as(publisher).body("{\"name\": \"Sport\", \"color\": \"#ff0000\"}").post("/api/sections").then()
                .statusCode(400).body("errors.field", contains("color"));
    }

    @Test
    void duplicateNameIgnoringCase() {
        create(publisher, "Sport");

        as(publisher).body("{\"name\": \"sport\"}").post("/api/sections").then().statusCode(409)
                .body("errors.field", contains("name"));
        as(publisher).get("/api/sections").then().body("sections.name", contains("Sport"));
    }

    // --- update -----------------------------------------------------------------------------

    @Test
    void renameKeepsSlugAndPosition() {
        create(publisher, "Kultur");
        long sport = create(publisher, "Sport");

        as(chief).body("{\"name\": \"Sportnews\", \"color\": \"blue\"}").put("/api/sections/" + sport).then()
                .statusCode(200)
                .body("name", equalTo("Sportnews"))
                .body("color", equalTo("blue"))
                .body("slug", equalTo("sport"))
                .body("position", equalTo(1));
    }

    @Test
    void renameToOwnNameInOtherCaseIsAllowedButNotToAnotherSectionsName() {
        long sport = create(publisher, "Sport");
        create(publisher, "Kultur");

        as(publisher).body("{\"name\": \"SPORT\", \"color\": \"red\"}").put("/api/sections/" + sport).then()
                .statusCode(200);
        as(publisher).body("{\"name\": \"kultur\", \"color\": \"red\"}").put("/api/sections/" + sport).then()
                .statusCode(409).body("errors.field", contains("name"));
    }

    @Test
    void updateNeedsColour() {
        long sport = create(publisher, "Sport");

        as(publisher).body("{\"name\": \"Sport\"}").put("/api/sections/" + sport).then().statusCode(400)
                .body("errors.field", contains("color"));
    }

    @Test
    void updateOfUnknownSection() {
        as(publisher).body("{\"name\": \"Sport\", \"color\": \"red\"}").put("/api/sections/999999").then()
                .statusCode(404);
    }

    @Test
    void readerMayNotUpdate() {
        long sport = create(publisher, "Sport");

        as(reader).body("{\"name\": \"Hack\", \"color\": \"red\"}").put("/api/sections/" + sport).then()
                .statusCode(403);
    }

    // --- reorder ----------------------------------------------------------------------------

    @Test
    void moveSectionToTheFront() {
        long sport = create(publisher, "Sport");
        long kultur = create(publisher, "Kultur");

        as(publisher).body("{\"ids\": [%d, %d]}".formatted(kultur, sport)).put("/api/sections/order").then()
                .statusCode(200)
                .body("canManage", equalTo(true))
                .body("sections.name", contains("Kultur", "Sport"))
                .body("sections.position", contains(0, 1));
        as(reader).get("/api/sections").then().body("sections.name", contains("Kultur", "Sport"));
    }

    @Test
    void incompleteOrChangedOrderIsRefused() {
        long sport = create(publisher, "Sport");
        long kultur = create(publisher, "Kultur");
        long wetter = create(publisher, "Wetter");

        as(publisher).body("{\"ids\": [%d, %d]}".formatted(kultur, sport)).put("/api/sections/order").then()
                .statusCode(400).body("errors.field", contains("ids"));
        as(publisher).body("{\"ids\": [%d, %d, %d]}".formatted(kultur, kultur, sport)).put("/api/sections/order")
                .then().statusCode(400).body("errors.field", contains("ids"));
        as(publisher).body("{\"ids\": [%d, %d, %d, 999999]}".formatted(wetter, kultur, sport))
                .put("/api/sections/order").then().statusCode(400).body("errors.field", contains("ids"));
        as(publisher).body("{\"ids\": \"all\"}").put("/api/sections/order").then().statusCode(400)
                .body("errors.field", contains("ids"));

        as(publisher).get("/api/sections").then().body("sections.name", contains("Sport", "Kultur", "Wetter"));
    }

    @Test
    void readerMayNotReorder() {
        long sport = create(publisher, "Sport");

        as(reader).body("{\"ids\": [%d]}".formatted(sport)).put("/api/sections/order").then().statusCode(403);
    }

    // --- delete -----------------------------------------------------------------------------

    @Test
    void editorInChiefDeletesAnEmptySectionAndPositionsAreCompacted() {
        create(publisher, "Sport");
        long kultur = create(publisher, "Kultur");
        create(publisher, "Wetter");

        as(chief).delete("/api/sections/" + kultur).then().statusCode(204).body(emptyOrNullString());

        as(publisher).get("/api/sections").then().statusCode(200)
                .body("sections.name", contains("Sport", "Wetter"))
                .body("sections.position", contains(0, 1));
    }

    @Test
    void sectionWithADraftIsNotDeleted() {
        long sport = create(publisher, "Sport");
        long draft = as(publisher).body("{\"sectionId\": %d}".formatted(sport)).post("/api/articles").then()
                .statusCode(201).extract().jsonPath().getLong("id");

        as(publisher).delete("/api/sections/" + sport).then().statusCode(409)
                .body("errors.field", contains((Object) null))
                .body("errors[0].message", equalTo(
                        "section still contains 1 article(s); move them to another section first"));

        as(publisher).get("/api/sections").then().body("sections.name", contains("Sport"));
        as(publisher).get("/api/articles/" + draft).then().statusCode(200)
                .body("status", equalTo("DRAFT"))
                .body("section.id", equalTo((int) sport));
    }

    @Test
    void sectionRolesGoWithTheSection() {
        long kultur = create(publisher, "Kultur");
        assign(publisher, kultur, "reader", "REPORTER").statusCode(200);

        as(publisher).delete("/api/sections/" + kultur).then().statusCode(204);

        assertThat(sectionRoleCount(accountId("reader"))).isZero();
        assertThat(marked(accountId("reader"))).isTrue();
    }

    @Test
    void deletingASectionKeepsNoMarkerForAnEditorInChiefOrAnotherMember() {
        long kultur = create(publisher, "Kultur");
        long sport = create(publisher, "Sport");
        assign(publisher, kultur, "chief", "REPORTER").statusCode(200);
        assign(publisher, kultur, "reader", "REPORTER").statusCode(200);
        assign(publisher, sport, "reader", "REPORTER").statusCode(200);

        as(publisher).delete("/api/sections/" + kultur).then().statusCode(204);

        assertThat(marked(accountId("chief"))).isFalse();
        assertThat(marked(accountId("reader"))).isFalse();
    }

    @Test
    void sectionEditorAndReaderMayNotDelete() {
        long sport = create(publisher, "Sport");
        assign(publisher, sport, "nogroups", "SECTION_EDITOR").statusCode(200);

        as(nogroups).delete("/api/sections/" + sport).then().statusCode(403).body(emptyOrNullString());
        as(reader).delete("/api/sections/" + sport).then().statusCode(403).body(emptyOrNullString());

        as(publisher).get("/api/sections").then().body("sections.name", contains("Sport"));
    }

    @Test
    void deleteOfUnknownSection() {
        as(publisher).delete("/api/sections/999999").then().statusCode(404).body(emptyOrNullString());
    }

    @Test
    void theOnlySectionMayBeDeleted() {
        long sport = create(publisher, "Sport");

        as(publisher).delete("/api/sections/" + sport).then().statusCode(204);

        as(publisher).get("/api/sections").then().body("sections", empty());
    }

    @Test
    void deletionIsLoggedWithTheActor() {
        List<String> messages = new ArrayList<>();
        Handler handler = collecting(messages);
        long wetter = create(publisher, "Wetter");
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(SectionService.class.getName());
        logger.addHandler(handler);
        try {
            as(chief).delete("/api/sections/" + wetter).then().statusCode(204);
        } finally {
            logger.removeHandler(handler);
        }

        assertThat(messages).anySatisfy(message -> assertThat(message).contains("'Wetter'", "deleted", "'chief'"));
    }

    // --- members ----------------------------------------------------------------------------

    @Test
    void publisherMakesSectionEditorWhoSeesItInMe() {
        long sport = create(publisher, "Sport");

        assign(publisher, sport, "nogroups", "SECTION_EDITOR").statusCode(200)
                .body("accountId", equalTo(accountId("nogroups")))
                .body("username", equalTo("nogroups"))
                .body("firstName", equalTo("No"))
                .body("lastName", equalTo("Groups"))
                .body("role", equalTo("SECTION_EDITOR"));

        as(nogroups).get("/api/me").then().statusCode(200)
                .body("sectionRoles.sectionId", contains((int) sport))
                .body("sectionRoles.sectionName", contains("Sport"))
                .body("sectionRoles.role", contains("SECTION_EDITOR"))
                .body("allowedActions", contains("WRITE_ARTICLES", "USE_MEDIA", "ASSIGN_SECTION_ROLES",
                        "ADMINISTER_ACCOUNTS"));
    }

    @Test
    void reporterMayOnlyWriteAndSeesPromotionAtTheNextCall() {
        long sport = create(publisher, "Sport");
        assign(publisher, sport, "nogroups", "REPORTER").statusCode(200);

        as(nogroups).get("/api/me").then().statusCode(200)
                .body("allowedActions", contains("WRITE_ARTICLES", "USE_MEDIA"));

        assign(publisher, sport, "nogroups", "SECTION_EDITOR").statusCode(200);

        as(nogroups).get("/api/me").then().statusCode(200)
                .body("allowedActions", contains("WRITE_ARTICLES", "USE_MEDIA", "ASSIGN_SECTION_ROLES",
                        "ADMINISTER_ACCOUNTS"));
    }

    @Test
    void membersListSectionEditorsFirst() {
        long sport = create(publisher, "Sport");
        assign(publisher, sport, "reader", "REPORTER").statusCode(200);
        assign(publisher, sport, "nogroups", "SECTION_EDITOR").statusCode(200);

        as(publisher).get("/api/sections/%d/members".formatted(sport)).then().statusCode(200)
                .body("assignableRoles", contains("SECTION_EDITOR", "REPORTER"))
                .body("members.username", contains("nogroups", "reader"))
                .body("members.role", contains("SECTION_EDITOR", "REPORTER"));
    }

    @Test
    void sectionEditorAddsReporterInOwnSectionOnly() {
        long sport = create(publisher, "Sport");
        long kultur = create(publisher, "Kultur");
        assign(publisher, sport, "nogroups", "SECTION_EDITOR").statusCode(200);

        assign(nogroups, sport, "reader", "REPORTER").statusCode(200).body("role", equalTo("REPORTER"));
        assign(nogroups, kultur, "reader", "REPORTER").statusCode(403).body(emptyOrNullString());

        as(nogroups).get("/api/sections/%d/members".formatted(sport)).then().statusCode(200)
                .body("members.username", contains("nogroups", "reader"));
        as(nogroups).get("/api/sections/%d/members".formatted(kultur)).then().statusCode(403);
        as(publisher).get("/api/sections/%d/members".formatted(kultur)).then().body("members", empty());
    }

    @Test
    void reporterMayNotListOrAssign() {
        long sport = create(publisher, "Sport");
        assign(publisher, sport, "nogroups", "REPORTER").statusCode(200);

        as(nogroups).get("/api/sections/%d/members".formatted(sport)).then().statusCode(403);
        assign(nogroups, sport, "reader", "REPORTER").statusCode(403);
        as(publisher).get("/api/sections/%d/members".formatted(sport)).then()
                .body("members.username", contains("nogroups"));
    }

    @Test
    void replacingARoleKeepsOneRolePerSection() {
        long sport = create(publisher, "Sport");
        assign(publisher, sport, "nogroups", "REPORTER").statusCode(200);

        assign(publisher, sport, "nogroups", "SECTION_EDITOR").statusCode(200).body("role", equalTo("SECTION_EDITOR"));

        as(publisher).get("/api/sections/%d/members".formatted(sport)).then()
                .body("members.username", contains("nogroups"))
                .body("members.role", contains("SECTION_EDITOR"));
    }

    @Test
    void removeMember() {
        long sport = create(publisher, "Sport");
        assign(publisher, sport, "reader", "REPORTER").statusCode(200);
        String path = "/api/sections/%d/members/%s".formatted(sport, accountId("reader"));

        as(publisher).delete(path).then().statusCode(204);
        as(publisher).delete(path).then().statusCode(404);
        as(publisher).get("/api/sections/%d/members".formatted(sport)).then().body("members", empty());
        // the last section role went: reader becomes a sectionless reporter
        assertThat(marked(accountId("reader"))).isTrue();
        as(reader).get("/api/me").then().body("sectionlessReporter", equalTo(true))
                .body("allowedActions", contains("USE_MEDIA"));
    }

    @Test
    void removingOneOfTwoSectionsSetsNoMarker() {
        long sport = create(publisher, "Sport");
        long kultur = create(publisher, "Kultur");
        assign(publisher, sport, "reader", "REPORTER").statusCode(200);
        assign(publisher, kultur, "reader", "REPORTER").statusCode(200);

        as(publisher).delete("/api/sections/%d/members/%s".formatted(sport, accountId("reader"))).then()
                .statusCode(204);

        assertThat(marked(accountId("reader"))).isFalse();
    }

    @Test
    void sectionEditorRemovingTheLastSectionSetsTheMarker() {
        long sport = create(publisher, "Sport");
        assign(publisher, sport, "nogroups", "SECTION_EDITOR").statusCode(200);
        assign(publisher, sport, "reader", "REPORTER").statusCode(200);

        as(nogroups).delete("/api/sections/%d/members/%s".formatted(sport, accountId("reader"))).then()
                .statusCode(204);

        assertThat(marked(accountId("reader"))).isTrue();
    }

    @Test
    void editorInChiefLosingTheOnlySectionRoleKeepsNoMarker() {
        long sport = create(publisher, "Sport");
        assign(publisher, sport, "chief", "REPORTER").statusCode(200);

        as(publisher).delete("/api/sections/%d/members/%s".formatted(sport, accountId("chief"))).then()
                .statusCode(204);

        assertThat(marked(accountId("chief"))).isFalse();
    }

    @Test
    void unknownAccountSectionAndRole() {
        long sport = create(publisher, "Sport");

        as(publisher).body("{\"role\": \"REPORTER\"}").put("/api/sections/%d/members/no-such-id".formatted(sport))
                .then().statusCode(404);
        assign(publisher, 999999, "reader", "REPORTER").statusCode(404);
        as(publisher).get("/api/sections/999999/members").then().statusCode(404);
        assign(publisher, sport, "reader", "PUBLISHER").statusCode(400).body("errors.field", contains("role"));
    }

    @Test
    void serviceAccountIsUnknown() {
        long sport = create(publisher, "Sport");
        String serviceAccount = realm.users().searchByUsername("service-account-presserl-backend", true).getFirst()
                .getId();

        as(publisher).body("{\"role\": \"REPORTER\"}")
                .put("/api/sections/%d/members/%s".formatted(sport, serviceAccount)).then().statusCode(404);
    }

    @Test
    void membershipChangesAreLoggedWithTheActor() {
        List<String> messages = new ArrayList<>();
        Handler handler = collecting(messages);
        long sport = create(publisher, "Sport");
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(SectionMembers.class.getName());
        logger.addHandler(handler);
        try {
            assign(chief, sport, "reader", "REPORTER").statusCode(200);
            as(chief).delete("/api/sections/%d/members/%s".formatted(sport, accountId("reader"))).then()
                    .statusCode(204);
        } finally {
            logger.removeHandler(handler);
        }

        assertThat(messages).anySatisfy(message -> assertThat(message).contains("'reader'", "'Sport'", "REPORTER",
                "'chief'"));
        assertThat(messages).anySatisfy(message -> assertThat(message).contains("removed", "'reader'", "'chief'"));
    }

    @Test
    void readerSeesNoAssignableRolesAnywhere() {
        create(publisher, "Sport");
        create(publisher, "Kultur");

        as(reader).get("/api/sections").then().body("sections.assignableRoles", everyItem(empty()));
        JsonPath json = as(publisher).get("/api/sections").then().extract().jsonPath();
        assertThat(json.getList("sections.slug", String.class)).containsExactly("sport", "kultur");
    }

    private static Handler collecting(List<String> messages) {
        return new Handler() {
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
    }

    private boolean marked(String accountId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT count(*) FROM sectionless_reporter WHERE account_id = ?")) {
            statement.setString(1, accountId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private long issueId(int number) {
        return single("SELECT id FROM issue WHERE number = " + number);
    }

    private long newIssue(int number) {
        return single("INSERT INTO issue (number) VALUES (%d) RETURNING id".formatted(number));
    }

    private long single(String sql) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
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

    private int sectionRoleCount(String accountId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT count(*) FROM section_role WHERE account_id = ?")) {
            statement.setString(1, accountId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
