package info.unterrainer.presserl.issue;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import info.unterrainer.presserl.TestSupport;
import info.unterrainer.presserl.bootstrap.KeycloakAdminProducer.KeycloakRealm;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;

/**
 * Issue endpoints and the rule that newly published articles join the newest issue. Articles,
 * sections and issues are reset to a fresh installation (only issue 1) before and after every test.
 */
@QuarkusTest
class IssueResourceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    DataSource dataSource;

    @Inject
    Keycloak keycloak;

    @Inject
    KeycloakRealm keycloakRealm;

    private String publisher;
    private String chief;
    private String nogroups;
    private long sport;

    @BeforeEach
    void ready() {
        TestSupport.awaitReady();
        reset();
        publisher = TestSupport.token("publisher", "publisher");
        chief = TestSupport.token("chief", "chief");
        nogroups = TestSupport.token("nogroups", "nogroups");
        sport = section("Sport");
    }

    @AfterEach
    void reset() {
        TestSupport.deleteSections(dataSource);
        TestSupport.resetIssues(dataSource);
    }

    // --- helpers

    private static RequestSpecification as(String token) {
        return given().auth().oauth2(token).contentType(ContentType.JSON);
    }

    private long section(String name) {
        return as(publisher).body("{\"name\": \"%s\"}".formatted(name)).post("/api/sections").then().statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    private void assign(long section, String username, String role) {
        String accountId = keycloak.realm(keycloakRealm.name()).users().searchByUsername(username, true).getFirst()
                .getId();
        as(publisher).body("{\"role\": \"%s\"}".formatted(role))
                .put("/api/sections/%d/members/%s".formatted(section, accountId)).then().statusCode(200);
    }

    private static ObjectNode in(long section, String headline) {
        return MAPPER.createObjectNode().put("headline", headline).put("sectionId", section);
    }

    private long draft(String headline) {
        return as(publisher).body(in(sport, headline).toString()).post("/api/articles").then().statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    private ValidatableResponse publish(long article) {
        return as(publisher).post("/api/articles/" + article + "/publish").then();
    }

    /**
     * A draft of the publisher, published at once (the publisher's chain is empty).
     */
    private long published(String headline) {
        long id = draft(headline);
        publish(id).statusCode(200);
        return id;
    }

    private ValidatableResponse article(long id) {
        return as(publisher).get("/api/articles/" + id).then().statusCode(200);
    }

    private static ValidatableResponse list(String token) {
        return as(token).get("/api/issues").then();
    }

    private long issueId(int number) {
        return list(publisher).statusCode(200).extract().jsonPath()
                .getLong("issues.find { it.number == %d }.id".formatted(number));
    }

    private ValidatableResponse create(String body) {
        return as(chief).body(body).post("/api/issues").then();
    }

    private long newIssue() {
        return create("{}").statusCode(201).extract().jsonPath().getLong("id");
    }

    private ValidatableResponse issue(long id) {
        return as(chief).get("/api/issues/" + id).then();
    }

    private ValidatableResponse setArticles(long issue, long... articles) {
        List<Long> ids = new ArrayList<>();
        for (long article : articles) {
            ids.add(article);
        }
        return as(chief).body("{\"articleIds\": " + ids + "}").put("/api/issues/" + issue + "/articles").then();
    }

    private ValidatableResponse switchLive(long issue, boolean live) {
        return as(chief).post("/api/issues/" + issue + (live ? "/publish" : "/unpublish")).then();
    }

    private static List<Long> articleIds(ValidatableResponse issue) {
        return issue.statusCode(200).extract().jsonPath().getList("articles.id", Long.class);
    }

    // --- access

    @Test
    void withoutTokenIsUnauthorized() {
        given().get("/api/issues").then().statusCode(401);
    }

    @Test
    void reporterMayNotListIssues() {
        assign(sport, "nogroups", "REPORTER");

        list(nogroups).statusCode(403);
        as(nogroups).body("{}").post("/api/issues").then().statusCode(403);
        as(nogroups).post("/api/issues/" + issueId(1) + "/publish").then().statusCode(403);
    }

    @Test
    void editorInChiefManagesIssues() {
        list(chief).statusCode(200);
    }

    // --- list and create

    @Test
    void freshInstallationHasIssueOne() {
        list(publisher).statusCode(200)
                .body("issues.number", contains(1))
                .body("issues[0].published", equalTo(false))
                .body("issues[0].publishedAt", nullValue())
                .body("issues[0].publicationDate", nullValue())
                .body("issues[0].articleCount", equalTo(0))
                .body("issues[0].newest", equalTo(true));
    }

    @Test
    void twoIssuesHighestFirstWithCountsAndFlags() {
        long one = issueId(1);
        setArticles(one, published("A"), published("B"), published("C")).statusCode(200);
        switchLive(one, true).statusCode(200);
        long two = newIssue();
        setArticles(two, draft("D"), draft("E")).statusCode(200);

        list(chief).statusCode(200)
                .body("issues.number", contains(2, 1))
                .body("issues.newest", contains(true, false))
                .body("issues.published", contains(false, true))
                .body("issues.articleCount", contains(2, 3))
                .body("issues[0].publishedAt", nullValue())
                .body("issues[1].publishedAt", notNullValue());
    }

    @Test
    void createGetsTheNextNumber() {
        newIssue();

        JsonPath created = create("{\"publicationDate\": null}").statusCode(201)
                .header("Location", org.hamcrest.Matchers.endsWith("/api/issues/" + issueId(3)))
                .body("number", equalTo(3))
                .body("published", equalTo(false))
                .body("publicationDate", nullValue())
                .body("newest", equalTo(true))
                .body("articles", empty())
                .extract().jsonPath();
        assertThat(created.getLong("id")).isEqualTo(issueId(3));
        list(chief).body("issues.newest", contains(true, false, false));
    }

    @Test
    void createWithDate() {
        create("{\"publicationDate\": \"2026-10-12\"}").statusCode(201)
                .body("number", equalTo(2))
                .body("publicationDate", equalTo("2026-10-12"));
    }

    @Test
    void invalidDateIsRefused() {
        create("{\"publicationDate\": \"2026-13-40\"}").statusCode(400)
                .body("errors.field", contains("publicationDate"));
        create("{\"publicationDate\": 20261012}").statusCode(400)
                .body("errors.field", contains("publicationDate"));

        list(chief).body("issues.number", contains(1));
    }

    @Test
    void unknownFieldIsRefused() {
        create("{\"title\": \"Summer\"}").statusCode(400).body("errors.field", contains("title"));
        create("[]").statusCode(400);

        list(chief).body("issues.number", contains(1));
    }

    @Test
    void numberAfterTheHighestRemaining() {
        newIssue();
        long three = newIssue();
        as(chief).delete("/api/issues/" + three).then().statusCode(204);

        create("{}").statusCode(201).body("number", equalTo(3));
    }

    // --- read

    @Test
    void readListsArticlesInIssueOrder() {
        long nine = published("Nine");
        long four = draft("Four");
        long six = published("Six");
        long two = newIssue();
        setArticles(two, nine, four, six).statusCode(200);

        issue(two).statusCode(200)
                .body("number", equalTo(2))
                .body("articleCount", equalTo(3))
                .body("articles.id", contains((int) nine, (int) four, (int) six))
                .body("articles.headline", contains("Nine", "Four", "Six"))
                .body("articles.status", contains("PUBLISHED", "DRAFT", "PUBLISHED"))
                .body("articles.section.name", everyItem(equalTo("Sport")))
                .body("articles.issue.number", everyItem(equalTo(2)));
    }

    @Test
    void unknownOrMalformedIdIsNotFound() {
        issue(999_999).statusCode(404);
        as(chief).get("/api/issues/abc").then().statusCode(404);
        as(chief).body("{\"publicationDate\": null}").put("/api/issues/999999").then().statusCode(404);
        as(chief).post("/api/issues/999999/publish").then().statusCode(404);
        setArticles(999_999).statusCode(404);
        as(chief).delete("/api/issues/999999").then().statusCode(404);
    }

    // --- date

    @Test
    void dateIsSetAndCleared() {
        long two = newIssue();

        as(chief).body("{\"publicationDate\": \"2026-10-12\"}").put("/api/issues/" + two).then().statusCode(200)
                .body("publicationDate", equalTo("2026-10-12"))
                .body("number", equalTo(2));
        switchLive(two, true).statusCode(200);
        as(chief).body("{\"publicationDate\": null}").put("/api/issues/" + two).then().statusCode(200)
                .body("publicationDate", nullValue())
                .body("published", equalTo(true));
    }

    @Test
    void dateFieldIsRequiredOnUpdate() {
        long two = newIssue();

        as(chief).body("{}").put("/api/issues/" + two).then().statusCode(400)
                .body("errors.field", contains("publicationDate"));
        as(chief).body("{\"publicationDate\": \"12.10.2026\"}").put("/api/issues/" + two).then().statusCode(400)
                .body("errors.field", contains("publicationDate"));
    }

    // --- publish and unpublish

    @Test
    void publishAndUnpublishAreIdempotentAndLeaveArticlesAlone() {
        long one = issueId(1);
        long article = published("Live");
        List<String> messages = new ArrayList<>();
        Handler handler = collecting(messages);
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(IssueService.class.getName());
        logger.addHandler(handler);
        String publishedAt;
        try {
            publishedAt = switchLive(one, true).statusCode(200)
                    .body("published", equalTo(true))
                    .body("publishedAt", notNullValue())
                    .extract().path("publishedAt");
            switchLive(one, true).statusCode(200).body("publishedAt", equalTo(publishedAt));

            switchLive(one, false).statusCode(200)
                    .body("published", equalTo(false))
                    .body("publishedAt", nullValue());
            switchLive(one, false).statusCode(200).body("published", equalTo(false));
        } finally {
            logger.removeHandler(handler);
        }

        article(article).body("status", equalTo("PUBLISHED"));
        issue(one).body("articles.id", contains((int) article));
        assertThat(messages).anySatisfy(message -> assertThat(message).contains("Issue 1", "published", "'chief'"));
        assertThat(messages).anySatisfy(message -> assertThat(message).contains("Issue 1", "unpublished", "'chief'"));
    }

    @Test
    void severalIssuesMayBeLive() {
        long one = issueId(1);
        long two = newIssue();
        switchLive(one, true).statusCode(200);
        switchLive(two, true).statusCode(200);

        list(chief).body("issues.published", contains(true, true));
    }

    // --- set articles

    @Test
    void reorderMakesAnotherArticleTheLeadStory() {
        long nine = published("Nine");
        long four = published("Four");
        long six = published("Six");
        long two = newIssue();
        setArticles(two, nine, four, six).statusCode(200);

        setArticles(two, six, nine, four).statusCode(200)
                .body("articles.id", contains((int) six, (int) nine, (int) four));
        assertThat(articleIds(issue(two))).containsExactly(six, nine, four);
    }

    @Test
    void moveFromAnotherIssueAndDropOne() {
        long five = published("Five");
        long nine = published("Nine");
        long four = published("Four");
        long one = issueId(1);
        long two = newIssue();
        setArticles(two, nine, four).statusCode(200);
        assertThat(articleIds(issue(one))).containsExactly(five);
        long fourVersion = article(four).extract().jsonPath().getLong("version");

        setArticles(two, nine, five).statusCode(200).body("articles.id", contains((int) nine, (int) five));

        assertThat(articleIds(issue(one))).isEmpty();
        article(five).body("issue.number", equalTo(2));
        article(four).body("issue", nullValue())
                .body("status", equalTo("PUBLISHED"))
                .body("version", equalTo((int) fourVersion));
    }

    @Test
    void articleInTwoIssuesIsImpossible() {
        long seven = published("Seven");
        long two = newIssue();

        setArticles(two, seven).statusCode(200);

        assertThat(articleIds(issue(issueId(1)))).isEmpty();
        assertThat(articleIds(issue(two))).containsExactly(seven);
    }

    @Test
    void emptyListEmptiesTheIssue() {
        long one = issueId(1);
        long article = published("Gone");

        setArticles(one).statusCode(200).body("articles", empty()).body("articleCount", equalTo(0));

        article(article).body("issue", nullValue());
    }

    @Test
    void unknownArticleChangesNothing() {
        long nine = published("Nine");
        long four = published("Four");
        long two = newIssue();
        setArticles(two, nine, four).statusCode(200);

        setArticles(two, nine, 999_999).statusCode(400)
                .body("errors.field", contains("articleIds"))
                .body("errors[0].message", org.hamcrest.Matchers.containsString("999999"));

        assertThat(articleIds(issue(two))).containsExactly(nine, four);
    }

    @Test
    void repeatedArticleChangesNothing() {
        long nine = published("Nine");
        long four = published("Four");
        long two = newIssue();
        setArticles(two, nine, four).statusCode(200);

        setArticles(two, four, nine, four).statusCode(400).body("errors.field", contains("articleIds"));
        as(chief).body("{\"articleIds\": [1], \"lead\": 1}").put("/api/issues/" + two + "/articles").then()
                .statusCode(400).body("errors.field", contains("lead"));

        assertThat(articleIds(issue(two))).containsExactly(nine, four);
    }

    // --- delete

    @Test
    void deletePlannedIssueFreesItsArticles() {
        long eleven = published("Eleven");
        long twelve = draft("Twelve");
        long four = newIssue();
        setArticles(four, eleven, twelve).statusCode(200);
        List<String> messages = new ArrayList<>();
        Handler handler = collecting(messages);
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(IssueService.class.getName());
        logger.addHandler(handler);
        try {
            as(chief).delete("/api/issues/" + four).then().statusCode(204);
        } finally {
            logger.removeHandler(handler);
        }

        issue(four).statusCode(404);
        article(eleven).body("issue", nullValue()).body("status", equalTo("PUBLISHED"));
        article(twelve).body("issue", nullValue()).body("status", equalTo("DRAFT"));
        assertThat(messages).anySatisfy(message -> assertThat(message).contains("Issue 2", "deleted", "'chief'"));
    }

    @Test
    void publishedIssueCannotBeDeleted() {
        long one = issueId(1);
        switchLive(one, true).statusCode(200);

        as(chief).delete("/api/issues/" + one).then().statusCode(409)
                .body("errors[0].field", nullValue());

        issue(one).statusCode(200).body("published", equalTo(true));
    }

    // --- newest issue collects newly published articles

    @Test
    void blogModeAppendsToTheOnlyIssue() {
        long one = issueId(1);
        switchLive(one, true).statusCode(200);
        long first = published("First");

        long second = draft("Second");
        publish(second).statusCode(200)
                .body("issue.id", equalTo((int) one))
                .body("issue.number", equalTo(1));

        assertThat(articleIds(issue(one))).containsExactly(first, second);
    }

    @Test
    void plannedIssueCollects() {
        long one = issueId(1);
        switchLive(one, true).statusCode(200);
        long two = newIssue();

        long article = published("Planned");

        assertThat(articleIds(issue(one))).isEmpty();
        assertThat(articleIds(issue(two))).containsExactly(article);
    }

    @Test
    void approvalAppendsToo() {
        long id = as(chief).body(in(sport, "Approved").toString()).post("/api/articles").then().statusCode(201)
                .extract().jsonPath().getLong("id");
        as(chief).post("/api/articles/" + id + "/submit").then().statusCode(200)
                .body("pendingLevel", equalTo("PUBLISHER"));

        as(publisher).post("/api/articles/" + id + "/approve").then().statusCode(200)
                .body("status", equalTo("PUBLISHED"))
                .body("issue.number", equalTo(1));

        assertThat(articleIds(issue(issueId(1)))).containsExactly(id);
    }

    @Test
    void republicationKeepsIssueAndPosition() {
        long first = published("First");
        long second = published("Second");
        long one = issueId(1);
        newIssue();
        long version = article(first).extract().jsonPath().getLong("version");
        as(publisher).body(in(sport, "First, revised").put("version", version).toString())
                .put("/api/articles/" + first).then().statusCode(200);

        publish(first).statusCode(200).body("issue.number", equalTo(1));

        assertThat(articleIds(issue(one))).containsExactly(first, second);
    }

    @Test
    void assignedBeforeFirstPublicationStays() {
        long draft = draft("Early");
        long two = newIssue();
        setArticles(two, draft).statusCode(200);
        newIssue();

        publish(draft).statusCode(200).body("issue.number", equalTo(2));

        assertThat(articleIds(issue(two))).containsExactly(draft);
    }

    @Test
    void withoutIssuesArticlesStayWithoutIssue() {
        as(chief).delete("/api/issues/" + issueId(1)).then().statusCode(204);

        long id = draft("Lonely");
        publish(id).statusCode(200).body("issue", nullValue());
    }

    // --- articles carry their issue

    @Test
    void articlesCarryTheirIssue() {
        long nine = published("Nine");
        long draft = draft("Draft");

        article(nine).body("issue.id", equalTo((int) issueId(1))).body("issue.number", equalTo(1));
        article(draft).body("issue", nullValue());
        JsonPath summaries = as(publisher).get("/api/articles").then().statusCode(200).extract().jsonPath();
        assertThat(summaries.getInt("find { it.id == %d }.issue.number".formatted(nine))).isEqualTo(1);
        assertThat(summaries.<Object>get("find { it.id == %d }.issue".formatted(draft))).isNull();
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
}
