package info.unterrainer.presserl.article;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import info.unterrainer.presserl.TestSupport;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;

@QuarkusTest
class ArticleResourceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String STANDARD_BODY = """
            {"version": 1, "blocks": [
              {"type": "paragraph", "content": [{"text": "It started "}, {"text": "in May", "bold": true}, {"text": "."}]},
              {"type": "subhead", "text": "Watering"},
              {"type": "quote", "content": [{"text": "Every day!"}]},
              {"type": "list", "items": [[{"text": "Water"}], [{"text": "Sun"}]]}]}""";

    private String publisher;
    private String chief;

    @BeforeEach
    void ready() {
        TestSupport.awaitReady();
        publisher = TestSupport.token("publisher", "publisher");
        chief = TestSupport.token("chief", "chief");
    }

    private static RequestSpecification as(String token) {
        return given().auth().oauth2(token).contentType(ContentType.JSON);
    }

    private static ObjectNode content(String headline) {
        return MAPPER.createObjectNode().put("headline", headline);
    }

    /**
     * Creates an article as {@code token} and returns its id.
     */
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

    private static ValidatableResponse save(String token, long id, ObjectNode content, long version) {
        return as(token).body(content.deepCopy().put("version", version).toString()).put("/api/articles/" + id).then();
    }

    private static ValidatableResponse save(String token, long id, ObjectNode content) {
        return save(token, id, content, version(token, id));
    }

    private static ValidatableResponse publish(String token, long id) {
        return as(token).post("/api/articles/" + id + "/publish").then();
    }

    private static ValidatableResponse offline(String token, long id) {
        return as(token).post("/api/articles/" + id + "/offline").then();
    }

    private static List<String> errorFields(ValidatableResponse response) {
        return response.extract().jsonPath().getList("errors.field");
    }

    private static JsonNode json(String json) throws Exception {
        return MAPPER.readTree(json);
    }

    // --- roles

    @Test
    void publisherCreatesAnArticle() {
        as(publisher).body(content("Hello").toString()).post("/api/articles").then()
                .statusCode(201)
                .header("Location", containsString("/api/articles/"))
                .body("status", equalTo("DRAFT"))
                .body("revision", equalTo(1))
                .body("headline", equalTo("Hello"))
                .body("author.username", equalTo("publisher"))
                .body("author.displayName", equalTo("publisher"))
                .body("liveRevision", nullValue())
                .body("publishedAt", nullValue())
                .body("hasUnpublishedChanges", equalTo(false));
    }

    @Test
    void locationPointsToTheArticle() {
        String location = as(publisher).body("{}").post("/api/articles").then().statusCode(201)
                .extract().header("Location");
        given().auth().oauth2(publisher).get(location).then().statusCode(200).body("status", equalTo("DRAFT"));
    }

    @Test
    void readerIsRefused() {
        String reader = TestSupport.token("reader", "reader");
        as(reader).get("/api/articles").then().statusCode(403);
        as(reader).body("{}").post("/api/articles").then().statusCode(403);
    }

    @Test
    void userWithoutNewspaperRoleIsRefused() {
        as(TestSupport.token("nogroups", "nogroups")).get("/api/articles").then().statusCode(403);
    }

    @Test
    void noToken() {
        given().get("/api/articles").then().statusCode(401);
    }

    @Test
    void editorInChiefMayWrite() {
        long id = create(chief, content("Chief writes"));
        get(chief, id).statusCode(200).body("author.username", equalTo("chief"))
                .body("author.displayName", equalTo("Chief Editor"));
    }

    // --- content fields

    @Test
    void createWithoutContent() throws Exception {
        String response = as(publisher).body("{}").post("/api/articles").then().statusCode(201)
                .body("kicker", equalTo("")).body("headline", equalTo("")).body("subheadline", equalTo(""))
                .body("lead", equalTo(""))
                .extract().asString();
        assertThat(json(response).get("body")).isEqualTo(json("{\"version\":1,\"blocks\":[]}"));
    }

    @Test
    void textFieldsAreTrimmed() {
        long id = create(publisher, content("  Trim me  ").put("lead", " lead "));
        get(publisher, id).body("headline", equalTo("Trim me")).body("lead", equalTo("lead"));
    }

    @Test
    void headlineTooLong() {
        long id = create(publisher, content("Short"));
        ValidatableResponse response = save(publisher, id, content("h".repeat(201))).statusCode(400);
        assertThat(errorFields(response)).containsExactly("headline");
        get(publisher, id).body("headline", equalTo("Short"));
    }

    @Test
    void leadLimit() {
        long id = create(publisher, content("Lead").put("lead", "l".repeat(1000)));
        assertThat(errorFields(save(publisher, id, content("Lead").put("lead", "l".repeat(1001))).statusCode(400)))
                .containsExactly("lead");
    }

    @Test
    void lineBreakInAHeadline() {
        long id = create(publisher, content("Fine"));
        assertThat(errorFields(save(publisher, id, content("Hello\nWorld")).statusCode(400)))
                .containsExactly("headline");
    }

    @Test
    void invalidContentOnCreateIsRejected() {
        ValidatableResponse response = as(publisher).body(content("x").put("title", "y").toString())
                .post("/api/articles").then().statusCode(400);
        assertThat(errorFields(response)).containsExactly("title");
    }

    // --- body format

    @Test
    void validStandardBodyRoundTrips() throws Exception {
        long id = create(publisher, content("Body"));
        ObjectNode content = content("Body");
        content.set("body", json(STANDARD_BODY));
        save(publisher, id, content).statusCode(200);
        String response = get(publisher, id).statusCode(200).extract().asString();
        assertThat(json(response).get("body")).isEqualTo(json(STANDARD_BODY));
    }

    @Test
    void unknownBlockTypeIsRejectedAndNothingStored() throws Exception {
        long id = create(publisher, content("Before"));
        ObjectNode content = content("After");
        content.set("body", json("""
                {"version": 1, "blocks": [{"type": "html", "html": "<script>alert(1)</script>"}]}"""));
        ValidatableResponse response = save(publisher, id, content).statusCode(400)
                .body("errors[0].message", equalTo("unknown block type 'html'"));
        assertThat(errorFields(response)).containsExactly("body.blocks[0].type");
        get(publisher, id).body("headline", equalTo("Before")).body("body.blocks", empty());
    }

    @Test
    void unknownMarkIsRejected() throws Exception {
        long id = create(publisher, content("Marks"));
        ObjectNode content = content("Marks");
        content.set("body", json("""
                {"version": 1, "blocks": [{"type": "paragraph", "content": [{"text": "x", "italic": true}]}]}"""));
        assertThat(errorFields(save(publisher, id, content).statusCode(400)))
                .containsExactly("body.blocks[0].content[0].italic");
    }

    @Test
    void markupInTextIsKeptAsText() throws Exception {
        long id = create(publisher, content("Markup"));
        ObjectNode content = content("Markup");
        content.set("body", json("""
                {"version": 1, "blocks": [{"type": "paragraph", "content": [{"text": "<b>hi</b>"}]}]}"""));
        save(publisher, id, content).statusCode(200).body("body.blocks[0].content[0].text", equalTo("<b>hi</b>"));
        get(publisher, id).body("body.blocks[0].content[0].text", equalTo("<b>hi</b>"));
    }

    @Test
    void twoViolationsAreReportedTogether() throws Exception {
        long id = create(publisher, content("Two"));
        ObjectNode content = content("Two").put("kicker", "k".repeat(201));
        content.set("body", json("{\"version\": 1, \"blocks\": [{\"type\": \"video\"}]}"));
        assertThat(errorFields(save(publisher, id, content).statusCode(400)))
                .containsExactlyInAnyOrder("kicker", "body.blocks[0].type");
    }

    // --- working revision and live revision

    @Test
    void autosaveOnADraftKeepsOneRevision() {
        long id = create(publisher, content("v1"));
        save(publisher, id, content("v2")).statusCode(200);
        save(publisher, id, content("v3")).statusCode(200);
        save(publisher, id, content("v4")).statusCode(200).body("revision", equalTo(1));
        get(publisher, id).body("revision", equalTo(1)).body("headline", equalTo("v4"));
        as(publisher).get("/api/articles/" + id + "/revisions").then().statusCode(200)
                .body("number", contains(1)).body("headline", contains("v4"));
    }

    @Test
    void editingAPublishedArticleStartsANewRevision() {
        long id = create(publisher, content("First"));
        publish(publisher, id).statusCode(200);
        save(publisher, id, content("Second")).statusCode(200)
                .body("revision", equalTo(2))
                .body("liveRevision", equalTo(1))
                .body("hasUnpublishedChanges", equalTo(true))
                .body("headline", equalTo("Second"));
        save(publisher, id, content("Second again")).statusCode(200).body("revision", equalTo(2));
        as(publisher).get("/api/articles/" + id + "/revisions/1").then().statusCode(200)
                .body("headline", equalTo("First"));
    }

    @Test
    void republishing() {
        long id = create(publisher, content("First"));
        publish(publisher, id).statusCode(200);
        save(publisher, id, content("Second")).statusCode(200);
        publish(publisher, id).statusCode(200)
                .body("liveRevision", equalTo(2))
                .body("hasUnpublishedChanges", equalTo(false))
                .body("status", equalTo("PUBLISHED"));
    }

    @Test
    void historyAfterRepublishing() {
        long id = create(publisher, content("First"));
        publish(publisher, id).statusCode(200);
        save(publisher, id, content("Second")).statusCode(200);
        publish(publisher, id).statusCode(200);
        as(publisher).get("/api/articles/" + id + "/revisions").then().statusCode(200)
                .body("number", contains(2, 1))
                .body("live", contains(true, false))
                .body("headline", contains("Second", "First"))
                .body("[0].publishedAt", notNullValue())
                .body("[1].publishedAt", notNullValue())
                .body("[0].createdAt", notNullValue())
                .body("[0].updatedAt", notNullValue());
        as(publisher).get("/api/articles/" + id + "/revisions/1").then().statusCode(200)
                .body("number", equalTo(1)).body("live", equalTo(false)).body("headline", equalTo("First"))
                .body("kicker", equalTo("")).body("body.version", equalTo(1));
    }

    @Test
    void unknownRevision() {
        long id = create(publisher, content("Rev"));
        as(publisher).get("/api/articles/" + id + "/revisions/7").then().statusCode(404)
                .body("errors[0].field", nullValue());
        as(publisher).get("/api/articles/999999/revisions").then().statusCode(404);
    }

    // --- optimistic concurrency

    @Test
    void staleSaveFromASecondTab() {
        long id = create(publisher, content("Tabs"));
        long version = version(publisher, id);
        long next = save(publisher, id, content("First tab"), version).statusCode(200)
                .extract().jsonPath().getLong("version");
        assertThat(next).isGreaterThan(version);
        save(publisher, id, content("Second tab"), version).statusCode(409).body("errors[0].message", notNullValue());
        get(publisher, id).body("headline", equalTo("First tab"));
    }

    @Test
    void publishAndOfflineIncreaseTheVersion() {
        long id = create(publisher, content("Versions"));
        long v0 = version(publisher, id);
        long v1 = publish(publisher, id).statusCode(200).extract().jsonPath().getLong("version");
        long v2 = offline(publisher, id).statusCode(200).extract().jsonPath().getLong("version");
        assertThat(v1).isGreaterThan(v0);
        assertThat(v2).isGreaterThan(v1);
    }

    @Test
    void saveWithoutVersion() {
        long id = create(publisher, content("No version"));
        ValidatableResponse response = as(publisher).body(content("x").toString()).put("/api/articles/" + id).then()
                .statusCode(400);
        assertThat(errorFields(response)).containsExactly("version");
    }

    // --- ownership

    @Test
    void editorInChiefCannotEditThePublishersArticle() {
        long id = create(publisher, content("Mine"));
        save(chief, id, content("Theirs"), version(publisher, id)).statusCode(403)
                .body("errors[0].field", nullValue()).body("errors[0].message", notNullValue());
        get(publisher, id).body("headline", equalTo("Mine"));
    }

    @Test
    void editorInChiefCannotDeleteThePublishersArticle() {
        long id = create(publisher, content("Mine"));
        as(chief).delete("/api/articles/" + id).then().statusCode(403);
        get(publisher, id).statusCode(200);
    }

    @Test
    void deleteADraft() {
        long id = create(publisher, content("Delete me"));
        as(publisher).delete("/api/articles/" + id).then().statusCode(204);
        get(publisher, id).statusCode(404);
        as(publisher).get("/api/articles/" + id + "/revisions").then().statusCode(404);
    }

    @Test
    void deleteAnOfflineArticle() {
        long id = create(publisher, content("Was online"));
        publish(publisher, id).statusCode(200);
        offline(publisher, id).statusCode(200);
        as(publisher).delete("/api/articles/" + id).then().statusCode(409);
        get(publisher, id).statusCode(200).body("status", equalTo("OFFLINE"));
    }

    @Test
    void deleteAPublishedArticle() {
        long id = create(publisher, content("Online"));
        publish(publisher, id).statusCode(200);
        as(publisher).delete("/api/articles/" + id).then().statusCode(409);
    }

    // --- publishing

    @Test
    void soloPublisherPublishes() {
        long id = create(publisher, content("Hello"));
        publish(publisher, id).statusCode(200)
                .body("status", equalTo("PUBLISHED"))
                .body("liveRevision", equalTo(1))
                .body("publishedAt", notNullValue())
                .body("hasUnpublishedChanges", equalTo(false));
    }

    @Test
    void editorInChiefCannotPublishYet() {
        long id = create(chief, content("Chief's draft"));
        publish(chief, id).statusCode(403);
        get(chief, id).body("status", equalTo("DRAFT"));
    }

    @Test
    void publisherCannotPublishSomeoneElsesArticle() {
        long id = create(chief, content("Chief's draft"));
        publish(publisher, id).statusCode(403);
    }

    @Test
    void publishWithoutHeadline() {
        long id = create(publisher, content(""));
        assertThat(errorFields(publish(publisher, id).statusCode(400))).containsExactly("headline");
        get(publisher, id).body("status", equalTo("DRAFT"));
    }

    @Test
    void publishWithoutChangesIsAConflict() {
        long id = create(publisher, content("Once"));
        publish(publisher, id).statusCode(200);
        publish(publisher, id).statusCode(409);
    }

    @Test
    void backOnline() {
        long id = create(publisher, content("Back"));
        String firstPublishedAt = publish(publisher, id).statusCode(200).extract().jsonPath().getString("publishedAt");
        offline(publisher, id).statusCode(200).body("liveRevision", equalTo(1));
        publish(publisher, id).statusCode(200)
                .body("status", equalTo("PUBLISHED"))
                .body("liveRevision", equalTo(1))
                .body("revision", equalTo(1))
                .body("publishedAt", equalTo(firstPublishedAt));
    }

    @Test
    void backOnlineWithChanges() {
        long id = create(publisher, content("Back"));
        publish(publisher, id).statusCode(200);
        offline(publisher, id).statusCode(200);
        save(publisher, id, content("Back again")).statusCode(200).body("revision", equalTo(2))
                .body("hasUnpublishedChanges", equalTo(true));
        publish(publisher, id).statusCode(200).body("liveRevision", equalTo(2)).body("headline", equalTo("Back again"));
    }

    // --- taking offline

    @Test
    void editorInChiefTakesThePublishersArticleOffline() {
        long id = create(publisher, content("Too hot"));
        publish(publisher, id).statusCode(200);
        offline(chief, id).statusCode(200).body("status", equalTo("OFFLINE")).body("liveRevision", equalTo(1));
    }

    @Test
    void draftCannotGoOffline() {
        long id = create(publisher, content("Draft"));
        offline(publisher, id).statusCode(409);
    }

    @Test
    void offlineArticleCannotGoOfflineAgain() {
        long id = create(publisher, content("Twice"));
        publish(publisher, id).statusCode(200);
        offline(publisher, id).statusCode(200);
        offline(publisher, id).statusCode(409);
    }

    // --- allowedActions

    @Test
    void soloPublisherOnOwnDraft() {
        long id = create(publisher, content("Draft"));
        get(publisher, id).body("allowedActions", contains("EDIT", "PUBLISH", "DELETE"));
    }

    @Test
    void editorInChiefOnThePublishersPublishedArticle() {
        long id = create(publisher, content("Published"));
        publish(publisher, id).statusCode(200);
        get(chief, id).body("allowedActions", contains("TAKE_OFFLINE"));
    }

    @Test
    void publishedWithoutChanges() {
        long id = create(publisher, content("Published"));
        publish(publisher, id).statusCode(200).body("allowedActions", contains("EDIT", "TAKE_OFFLINE"));
        get(publisher, id).body("allowedActions", contains("EDIT", "TAKE_OFFLINE"));
    }

    @Test
    void editorInChiefOnOwnDraft() {
        long id = create(chief, content("Chief"));
        get(chief, id).body("allowedActions", contains("EDIT", "DELETE"));
    }

    // --- listing and reading

    @Test
    void filterOwnDrafts() {
        long publishersDraft = create(publisher, content("Publisher's draft"));
        long chiefsDraft = create(chief, content("Chief's draft"));
        long chiefsOther = create(chief, content("Chief's other draft"));
        long published = create(publisher, content("Published"));
        publish(publisher, published).statusCode(200);

        List<Map<String, Object>> list = as(chief).get("/api/articles?status=DRAFT&mine=true").then().statusCode(200)
                .extract().jsonPath().getList("$");
        List<Long> ids = list.stream().map(a -> ((Number) a.get("id")).longValue()).toList();
        assertThat(ids).contains(chiefsDraft, chiefsOther).doesNotContain(publishersDraft, published);
        assertThat(list).allSatisfy(a -> {
            assertThat(a.get("status")).isEqualTo("DRAFT");
            assertThat(a).extractingByKey("author").extracting("username").isEqualTo("chief");
        });
    }

    @Test
    void listShowsNewestChangeFirstWithSummaryFields() {
        long older = create(publisher, content("Older"));
        long newer = create(publisher, content("Newer"));
        save(publisher, older, content("Older, edited").put("kicker", "K")).statusCode(200);

        List<Map<String, Object>> list = as(publisher).get("/api/articles").then().statusCode(200)
                .extract().jsonPath().getList("$");
        List<Long> ids = list.stream().map(a -> ((Number) a.get("id")).longValue()).toList();
        assertThat(ids.indexOf(older)).isLessThan(ids.indexOf(newer));
        Map<String, Object> summary = list.get(ids.indexOf(older));
        assertThat(summary).containsEntry("headline", "Older, edited").containsEntry("kicker", "K")
                .containsEntry("status", "DRAFT").containsEntry("revision", 1)
                .containsEntry("hasUnpublishedChanges", false)
                .containsKeys("updatedAt", "publishedAt", "liveRevision", "author")
                .doesNotContainKey("body");
        assertThat((List<Object>) summary.get("allowedActions")).containsExactly("EDIT", "PUBLISH", "DELETE");
    }

    @Test
    void listFilteredByStatus() {
        long published = create(publisher, content("Published"));
        publish(publisher, published).statusCode(200);
        as(chief).get("/api/articles?status=PUBLISHED").then().statusCode(200)
                .body("id", hasItem((int) published))
                .body("status", everyItem(equalTo("PUBLISHED")));
    }

    @Test
    void unknownStatus() {
        ValidatableResponse response = as(publisher).get("/api/articles?status=ARCHIVED").then().statusCode(400);
        assertThat(errorFields(response)).containsExactly("status");
    }

    @Test
    void unknownArticle() {
        get(publisher, 999999).statusCode(404).body("errors[0].message", notNullValue());
        save(publisher, 999999, content("x"), 0).statusCode(404);
        as(publisher).delete("/api/articles/999999").then().statusCode(404);
        publish(publisher, 999999).statusCode(404);
        offline(publisher, 999999).statusCode(404);
    }
}
