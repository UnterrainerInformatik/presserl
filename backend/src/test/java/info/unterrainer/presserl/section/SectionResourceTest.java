package info.unterrainer.presserl.section;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItems;

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
                .body("sections[0].assignableRoles", empty());
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
                .body("allowedActions", contains("WRITE_ARTICLES", "ASSIGN_SECTION_ROLES", "ADMINISTER_ACCOUNTS"));
    }

    @Test
    void reporterMayOnlyWriteAndSeesPromotionAtTheNextCall() {
        long sport = create(publisher, "Sport");
        assign(publisher, sport, "nogroups", "REPORTER").statusCode(200);

        as(nogroups).get("/api/me").then().statusCode(200)
                .body("allowedActions", contains("WRITE_ARTICLES"));

        assign(publisher, sport, "nogroups", "SECTION_EDITOR").statusCode(200);

        as(nogroups).get("/api/me").then().statusCode(200)
                .body("allowedActions", contains("WRITE_ARTICLES", "ASSIGN_SECTION_ROLES", "ADMINISTER_ACCOUNTS"));
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
}
