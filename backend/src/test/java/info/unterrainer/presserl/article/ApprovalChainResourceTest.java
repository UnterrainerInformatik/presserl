package info.unterrainer.presserl.article;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import info.unterrainer.presserl.TestSupport;
import info.unterrainer.presserl.auth.NewspaperRole;
import info.unterrainer.presserl.bootstrap.KeycloakAdminProducer.KeycloakRealm;
import info.unterrainer.presserl.bootstrap.NewspaperGroups;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;

/**
 * The approval chain over the article endpoints. The dev realm staffs the levels with
 * {@code chief} ({@code EDITOR_IN_CHIEF}) and {@code publisher}; {@code reader} writes as reporter
 * and {@code nogroups} edits sections. Articles and sections (with all section roles) are deleted
 * before and after every test.
 */
@QuarkusTest
class ApprovalChainResourceTest {

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
    private String nogroups;

    @BeforeEach
    void ready() {
        TestSupport.awaitReady();
        TestSupport.deleteSections(dataSource);
        publisher = TestSupport.token("publisher", "publisher");
        chief = TestSupport.token("chief", "chief");
        reader = TestSupport.token("reader", "reader");
        nogroups = TestSupport.token("nogroups", "nogroups");
    }

    @AfterEach
    void cleanUp() {
        TestSupport.deleteSections(dataSource);
    }

    // --- helpers

    private RealmResource realm() {
        return keycloak.realm(keycloakRealm.name());
    }

    private String accountId(String username) {
        return realm().users().searchByUsername(username, true).getFirst().getId();
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

    private static RequestSpecification as(String token) {
        return given().auth().oauth2(token).contentType(ContentType.JSON);
    }

    private static ObjectNode in(long section, String headline) {
        return MAPPER.createObjectNode().put("headline", headline).put("sectionId", section);
    }

    private static long create(String token, ObjectNode content) {
        return as(token).body(content.toString()).post("/api/articles").then().statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    private static ValidatableResponse get(String token, long id) {
        return as(token).get("/api/articles/" + id).then();
    }

    private static long version(String token, long id) {
        return get(token, id).statusCode(200).extract().jsonPath().getLong("version");
    }

    private static ValidatableResponse save(String token, long id, ObjectNode content) {
        return as(token).body(content.deepCopy().put("version", version(token, id)).toString())
                .put("/api/articles/" + id).then();
    }

    private static ValidatableResponse action(String token, long id, String action) {
        return as(token).post("/api/articles/" + id + "/" + action).then();
    }

    private static ValidatableResponse submit(String token, long id) {
        return action(token, id, "submit");
    }

    private static ValidatableResponse approve(String token, long id) {
        return action(token, id, "approve");
    }

    private static ValidatableResponse withdraw(String token, long id) {
        return action(token, id, "withdraw");
    }

    private static ValidatableResponse reject(String token, long id, String note) {
        return as(token).body(MAPPER.createObjectNode().put("note", note).toString())
                .post("/api/articles/" + id + "/reject").then();
    }

    private static ValidatableResponse reviews(String token, long id) {
        return as(token).get("/api/articles/" + id + "/reviews").then();
    }

    private static String readerPage(long id, int status) {
        return given().get("/articles/" + id).then().statusCode(status).extract().asString();
    }

    /**
     * A published article of {@code chief} in {@code section} (submitted and approved by the
     * publisher), live revision 1.
     */
    private long publishedByChief(long section, String headline) {
        long id = create(chief, in(section, headline));
        submit(chief, id).statusCode(200).body("pendingLevel", equalTo("PUBLISHER"));
        approve(publisher, id).statusCode(200).body("status", equalTo("PUBLISHED"));
        return id;
    }

    // --- the chain

    @Test
    void reporterToSectionEditorToEditorInChiefToPublisher() {
        long sport = staffedSport();
        long id = create(reader, in(sport, "Goal"));

        submit(reader, id).statusCode(200)
                .body("status", equalTo("SUBMITTED"))
                .body("pendingLevel", equalTo("SECTION_EDITOR"))
                .body("allowedActions", contains("WITHDRAW", "DELETE"));
        readerPage(id, 404);
        get(nogroups, id).body("allowedActions", contains("APPROVE", "REJECT"));

        approve(nogroups, id).statusCode(200)
                .body("status", equalTo("SUBMITTED"))
                .body("pendingLevel", equalTo("EDITOR_IN_CHIEF"))
                .body("allowedActions", empty());
        approve(chief, id).statusCode(200).body("pendingLevel", equalTo("PUBLISHER"));
        approve(publisher, id).statusCode(200)
                .body("status", equalTo("PUBLISHED"))
                .body("pendingLevel", nullValue())
                .body("liveRevision", equalTo(1))
                .body("publishedAt", notNullValue());

        assertThat(readerPage(id, 200)).contains("Goal");
        reviews(reader, id).statusCode(200)
                .body("decision", contains("APPROVED", "APPROVED", "APPROVED"))
                .body("level", contains("PUBLISHER", "EDITOR_IN_CHIEF", "SECTION_EDITOR"))
                .body("reviewer.username", contains("publisher", "chief", "nogroups"))
                .body("revision", contains(1, 1, 1))
                .body("note", contains(nullValue(), nullValue(), nullValue()));
    }

    @Test
    void sectionWithoutSectionEditorWaitsForTheEditorInChief() {
        long kultur = section("Kultur");
        assign(kultur, "reader", "REPORTER");
        long id = create(reader, in(kultur, "Theatre"));
        submit(reader, id).statusCode(200).body("pendingLevel", equalTo("EDITOR_IN_CHIEF"));
    }

    @Test
    void lockedHolderStillStaffsTheLevel() {
        long kultur = section("Kultur");
        assign(kultur, "reader", "REPORTER");
        String chiefId = accountId("chief");
        as(publisher).post("/api/accounts/" + chiefId + "/lock").then().statusCode(200);
        try {
            long id = create(reader, in(kultur, "Theatre"));
            submit(reader, id).statusCode(200).body("pendingLevel", equalTo("EDITOR_IN_CHIEF"));
        } finally {
            as(publisher).post("/api/accounts/" + chiefId + "/unlock").then().statusCode(200);
        }
    }

    @Test
    void editorInChiefWaitsForThePublisher() {
        long sport = section("Sport");
        long id = create(chief, in(sport, "Editorial"));
        get(chief, id).body("allowedActions", contains("EDIT", "SUBMIT", "DELETE"));
        submit(chief, id).statusCode(200).body("pendingLevel", equalTo("PUBLISHER"));
    }

    @Test
    void higherRoleApprovesALowerLevel() {
        long sport = staffedSport();
        long id = create(reader, in(sport, "Goal"));
        submit(reader, id).statusCode(200).body("pendingLevel", equalTo("SECTION_EDITOR"));
        approve(publisher, id).statusCode(200).body("status", equalTo("PUBLISHED"));
        reviews(reader, id).body("level", contains("SECTION_EDITOR"));
    }

    @Test
    void changesToAPublishedArticleWaitWhileTheReaderKeepsTheLiveRevision() {
        long sport = section("Sport");
        long id = publishedByChief(sport, "First");
        save(chief, id, in(sport, "Second")).statusCode(200).body("revision", equalTo(2));

        submit(chief, id).statusCode(200)
                .body("status", equalTo("PUBLISHED"))
                .body("liveRevision", equalTo(1))
                .body("pendingLevel", equalTo("PUBLISHER"));
        assertThat(readerPage(id, 200)).contains("First").doesNotContain("Second");

        approve(publisher, id).statusCode(200)
                .body("status", equalTo("PUBLISHED"))
                .body("liveRevision", equalTo(2))
                .body("hasUnpublishedChanges", equalTo(false))
                .body("pendingLevel", nullValue());
        assertThat(readerPage(id, 200)).contains("Second");
    }

    @Test
    void offlineArticleGoesBackOnlineThroughTheChain() {
        long sport = section("Sport");
        long id = publishedByChief(sport, "Back");
        action(chief, id, "offline").statusCode(200).body("status", equalTo("OFFLINE"));

        submit(chief, id).statusCode(200)
                .body("status", equalTo("OFFLINE"))
                .body("pendingLevel", equalTo("PUBLISHER"));
        readerPage(id, 404);
        approve(publisher, id).statusCode(200).body("status", equalTo("PUBLISHED"));
        readerPage(id, 200);
    }

    @Test
    void takingOfflineKeepsThePendingSubmission() {
        long sport = section("Sport");
        long id = publishedByChief(sport, "First");
        save(chief, id, in(sport, "Second")).statusCode(200);
        submit(chief, id).statusCode(200);

        action(publisher, id, "offline").statusCode(200)
                .body("status", equalTo("OFFLINE"))
                .body("pendingLevel", equalTo("PUBLISHER"));
        approve(publisher, id).statusCode(200)
                .body("status", equalTo("PUBLISHED"))
                .body("liveRevision", equalTo(2));
    }

    // --- submitting

    @Test
    void publisherCannotSubmit() {
        long id = create(publisher, in(section("Sport"), "Mine"));
        submit(publisher, id).statusCode(403);
        get(publisher, id).body("status", equalTo("DRAFT")).body("pendingLevel", nullValue());
    }

    @Test
    void editorInChiefCannotPublishWhileAPublisherExists() {
        long id = create(chief, in(section("Sport"), "Mine"));
        action(chief, id, "publish").statusCode(403);
    }

    @Test
    void submitTwiceIsAConflict() {
        long id = create(reader, in(staffedSport(), "Goal"));
        submit(reader, id).statusCode(200);
        submit(reader, id).statusCode(409);
    }

    @Test
    void submitNeedsAHeadline() {
        long id = create(reader, in(staffedSport(), ""));
        submit(reader, id).statusCode(400).body("errors.field", contains("headline"));
        get(reader, id).body("status", equalTo("DRAFT")).body("pendingLevel", nullValue());
    }

    @Test
    void onlyTheAuthorSubmits() {
        long id = create(reader, in(staffedSport(), "Goal"));
        submit(nogroups, id).statusCode(403);
        submit(publisher, id).statusCode(403);
    }

    @Test
    void submitIncreasesTheVersion() {
        long id = create(reader, in(staffedSport(), "Goal"));
        long version = version(reader, id);
        submit(reader, id).statusCode(200).body("version", greaterThan((int) version));
        long submitted = version(reader, id);
        approve(nogroups, id).statusCode(200).body("version", greaterThan((int) submitted));
    }

    @Test
    void saveWhilePendingIsAConflict() {
        long sport = staffedSport();
        long id = create(reader, in(sport, "Goal"));
        submit(reader, id).statusCode(200);
        save(reader, id, in(sport, "Changed")).statusCode(409);
        get(reader, id).body("headline", equalTo("Goal")).body("revision", equalTo(1));
    }

    @Test
    void staffingChangesTakeEffectAtOnce() {
        long kultur = section("Kultur");
        assign(kultur, "reader", "REPORTER");
        long id = create(reader, in(kultur, "Theatre"));
        get(reader, id).body("allowedActions", hasItem("SUBMIT"));
        assign(kultur, "nogroups", "SECTION_EDITOR");
        submit(reader, id).statusCode(200).body("pendingLevel", equalTo("SECTION_EDITOR"));
    }

    // --- approving

    @Test
    void authorPromotedToPublisherCannotApproveOwnArticle() {
        long id = create(chief, in(section("Sport"), "Editorial"));
        submit(chief, id).statusCode(200).body("pendingLevel", equalTo("PUBLISHER"));
        String chiefId = accountId("chief");
        String publisherGroup = NewspaperGroups.id(realm(), NewspaperRole.PUBLISHER).orElseThrow();
        realm().users().get(chiefId).joinGroup(publisherGroup);
        try {
            String promoted = TestSupport.token("chief", "chief");
            get(promoted, id).body("allowedActions", not(hasItem("APPROVE")));
            approve(promoted, id).statusCode(403);
        } finally {
            realm().users().get(chiefId).leaveGroup(publisherGroup);
        }
        get(chief, id).body("pendingLevel", equalTo("PUBLISHER"));
    }

    @Test
    void levelTooLowIsForbidden() {
        long id = create(reader, in(staffedSport(), "Goal"));
        submit(reader, id).statusCode(200);
        approve(nogroups, id).statusCode(200);
        approve(nogroups, id).statusCode(403);
        approve(chief, id).statusCode(200).body("pendingLevel", equalTo("PUBLISHER"));
        approve(chief, id).statusCode(403);
        reject(chief, id, "No").statusCode(403);
        get(reader, id).body("pendingLevel", equalTo("PUBLISHER"));
    }

    @Test
    void sectionEditorOfAnotherSectionDoesNotSeeTheArticle() {
        staffedSport();
        long kultur = section("Kultur");
        assign(kultur, "reader", "REPORTER");
        assign(kultur, "chief", "SECTION_EDITOR");
        long id = create(reader, in(kultur, "Theatre"));
        submit(reader, id).statusCode(200).body("pendingLevel", equalTo("SECTION_EDITOR"));
        approve(nogroups, id).statusCode(404);
        reject(nogroups, id, "No").statusCode(404);
        withdraw(nogroups, id).statusCode(404);
        submit(nogroups, id).statusCode(404);
    }

    @Test
    void reporterCannotApproveAnotherReportersArticle() {
        long sport = section("Sport");
        assign(sport, "reader", "REPORTER");
        assign(sport, "nogroups", "REPORTER");
        long id = create(reader, in(sport, "Goal"));
        submit(reader, id).statusCode(200).body("pendingLevel", equalTo("EDITOR_IN_CHIEF"));
        approve(nogroups, id).statusCode(404);
        reviews(nogroups, id).statusCode(404);
    }

    @Test
    void nothingToApprove() {
        long id = create(publisher, in(section("Sport"), "Draft"));
        long foreign = create(chief, in(section("Kultur"), "Chief's draft"));
        approve(publisher, foreign).statusCode(409);
        reject(publisher, foreign, "No").statusCode(409);
        approve(chief, id).statusCode(409);
    }

    // --- rejecting

    @Test
    void rejectADraft() {
        long id = create(reader, in(staffedSport(), "Goal"));
        submit(reader, id).statusCode(200);
        reject(nogroups, id, "  Please add who scored.  ").statusCode(200)
                .body("status", equalTo("DRAFT"))
                .body("pendingLevel", nullValue())
                .body("revision", equalTo(1));
        get(reader, id).body("allowedActions", contains("EDIT", "SUBMIT", "DELETE"));
        reviews(reader, id).body("decision", contains("REJECTED"))
                .body("note", contains("Please add who scored."))
                .body("level", contains("SECTION_EDITOR"));
    }

    @Test
    void rejectChangesToAPublishedArticle() {
        long sport = section("Sport");
        long id = publishedByChief(sport, "First");
        save(chief, id, in(sport, "Second")).statusCode(200);
        submit(chief, id).statusCode(200);
        reject(publisher, id, "Keep the first").statusCode(200)
                .body("status", equalTo("PUBLISHED"))
                .body("liveRevision", equalTo(1))
                .body("hasUnpublishedChanges", equalTo(true))
                .body("pendingLevel", nullValue());
        assertThat(readerPage(id, 200)).contains("First");
    }

    @Test
    void rejectNeedsANote() {
        long id = create(reader, in(staffedSport(), "Goal"));
        submit(reader, id).statusCode(200);
        reject(nogroups, id, "   ").statusCode(400).body("errors.field", contains("note"));
        as(nogroups).body("{}").post("/api/articles/" + id + "/reject").then().statusCode(400)
                .body("errors.field", contains("note"));
        as(nogroups).body("{\"note\": \"ok\", \"extra\": 1}").post("/api/articles/" + id + "/reject").then()
                .statusCode(400).body("errors.field", contains("extra"));
        get(reader, id).body("pendingLevel", equalTo("SECTION_EDITOR"));
        reviews(reader, id).body("$", empty());
    }

    // --- withdrawing

    @Test
    void authorWithdraws() {
        long kultur = section("Kultur");
        assign(kultur, "reader", "REPORTER");
        long id = create(reader, in(kultur, "Theatre"));
        submit(reader, id).statusCode(200).body("pendingLevel", equalTo("EDITOR_IN_CHIEF"));
        withdraw(reader, id).statusCode(200)
                .body("status", equalTo("DRAFT"))
                .body("pendingLevel", nullValue());
        withdraw(reader, id).statusCode(409);
        reviews(reader, id).body("$", empty());
    }

    @Test
    void approverCannotWithdraw() {
        long id = create(reader, in(staffedSport(), "Goal"));
        submit(reader, id).statusCode(200);
        withdraw(nogroups, id).statusCode(403);
        withdraw(publisher, id).statusCode(403);
    }

    @Test
    void withdrawChangesToAPublishedArticle() {
        long sport = section("Sport");
        long id = publishedByChief(sport, "First");
        save(chief, id, in(sport, "Second")).statusCode(200);
        submit(chief, id).statusCode(200);
        withdraw(chief, id).statusCode(200).body("status", equalTo("PUBLISHED")).body("pendingLevel", nullValue());
    }

    // --- reviews, deleting, listing

    @Test
    void reviewsNewestFirst() {
        long id = create(reader, in(staffedSport(), "Goal"));
        submit(reader, id).statusCode(200);
        approve(nogroups, id).statusCode(200);
        reject(chief, id, "Too short").statusCode(200);
        reviews(reader, id).statusCode(200)
                .body("decision", contains("REJECTED", "APPROVED"))
                .body("reviewer.username", contains("chief", "nogroups"))
                .body("reviewer.displayName", contains(notNullValue(), notNullValue()))
                .body("level", contains("EDITOR_IN_CHIEF", "SECTION_EDITOR"))
                .body("note", contains(equalTo("Too short"), nullValue()))
                .body("createdAt", contains(notNullValue(), notNullValue()));
    }

    @Test
    void reviewsOfAnUnknownArticle() {
        reviews(publisher, 999_999).statusCode(404);
    }

    @Test
    void deleteASubmittedArticle() {
        long id = create(reader, in(staffedSport(), "Goal"));
        submit(reader, id).statusCode(200);
        approve(nogroups, id).statusCode(200);
        as(reader).delete("/api/articles/" + id).then().statusCode(204);
        get(reader, id).statusCode(404);
    }

    @Test
    void pendingFilter() {
        long sport = staffedSport();
        long waitingDraft = create(reader, in(sport, "Waiting"));
        submit(reader, waitingDraft).statusCode(200);
        long published = publishedByChief(sport, "First");
        save(chief, published, in(sport, "Second")).statusCode(200);
        submit(chief, published).statusCode(200);
        create(reader, in(sport, "Not submitted"));

        as(publisher).get("/api/articles?pending=true").then().statusCode(200)
                .body("id", containsInAnyOrder((int) waitingDraft, (int) published))
                .body("pendingLevel", containsInAnyOrder("SECTION_EDITOR", "PUBLISHER"));
        as(publisher).get("/api/articles?pending=true&status=PUBLISHED").then().statusCode(200)
                .body("id", contains((int) published));
        as(reader).get("/api/articles?pending=true&mine=true").then().statusCode(200)
                .body("id", contains((int) waitingDraft));
        as(publisher).get("/api/articles").then().statusCode(200).body("id.size()", equalTo(3));
    }

    @Test
    void listEntriesCarryTheChainActions() {
        long id = create(reader, in(staffedSport(), "Goal"));
        as(reader).get("/api/articles").then().statusCode(200)
                .body("allowedActions[0]", contains("EDIT", "SUBMIT", "DELETE"))
                .body("pendingLevel[0]", nullValue());
        submit(reader, id).statusCode(200);
        as(nogroups).get("/api/articles").then().statusCode(200)
                .body("allowedActions[0]", contains("APPROVE", "REJECT"))
                .body("pendingLevel[0]", equalTo("SECTION_EDITOR"));
    }
}
