package info.unterrainer.presserl.spellcheck;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

import java.time.Duration;
import java.util.Map;

import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import info.unterrainer.presserl.TestSupport;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;

/**
 * {@code POST /api/spell-check} against {@link LanguageToolStub}. {@code publisher} is a writer,
 * {@code reader} holds only {@code READER}.
 */
@QuarkusTest
class SpellCheckResourceTest {

    private String publisher;

    @BeforeEach
    void ready() {
        TestSupport.awaitReady();
        publisher = TestSupport.token("publisher", "publisher");
    }

    @AfterEach
    void defaultHelp() {
        help(null);
    }

    /**
     * Sets the newspaper's {@code spell-check.help}; {@code null} removes the override.
     */
    private void help(String level) {
        given().auth().oauth2(publisher).contentType(ContentType.JSON)
                .body(level == null ? "{\"spell-check.help\": null}" : "{\"spell-check.help\": \"" + level + "\"}")
                .put("/api/newspaper/settings").then().statusCode(200);
    }

    static int stubCalls() {
        return Integer.parseInt(given().get(stubUrl() + "/stub/calls").asString());
    }

    static String stubUrl() {
        return ConfigProvider.getConfig().getValue(LanguageToolStub.URL, String.class);
    }

    static ValidatableResponse check(String token, Object body) {
        return given().auth().oauth2(token).contentType(ContentType.JSON).body(body).post("/api/spell-check").then();
    }

    @Test
    void misspellingIsReportedWithAtMostFiveReplacements() {
        check(publisher, Map.of("text", "Der Hund ist gros.")).statusCode(200)
                .body("matches", hasSize(1))
                .body("matches[0].offset", equalTo(13))
                .body("matches[0].length", equalTo(4))
                .body("matches[0].message", equalTo("Möglicher Tippfehler gefunden."))
                .body("matches[0].replacements", contains("groß", "Gros", "grob", "gro", "ros"));
        assertThat(given().get(stubUrl() + "/stub/language").asString()).isEqualTo("de-DE");
    }

    @Test
    void messagesLevelDropsTheReplacements() {
        help("messages");
        check(publisher, Map.of("text", "Der Hund ist gros.")).statusCode(200)
                .body("matches", hasSize(1))
                .body("matches[0].offset", equalTo(13))
                .body("matches[0].length", equalTo(4))
                .body("matches[0].message", equalTo("Möglicher Tippfehler gefunden."))
                .body("matches[0].replacements", empty());
    }

    @Test
    void marksLevelDropsMessageAndReplacements() {
        help("marks");
        check(publisher, Map.of("text", "Der Hund ist gros. Wir haben einen hund.")).statusCode(200)
                .body("matches", hasSize(2))
                .body("matches.offset", contains(13, 35))
                .body("matches.length", contains(4, 4))
                .body("matches.message", contains("", ""))
                .body("matches[0].replacements", empty())
                .body("matches[1].replacements", empty());
    }

    @Test
    void changedLevelAppliesToTheNextCheck() {
        check(publisher, Map.of("text", "Der Hund ist gros.")).statusCode(200)
                .body("matches[0].message", equalTo("Möglicher Tippfehler gefunden."))
                .body("matches[0].replacements", hasSize(5));
        help("marks");
        check(publisher, Map.of("text", "Der Hund ist gros.")).statusCode(200)
                .body("matches[0].offset", equalTo(13))
                .body("matches[0].message", equalTo(""))
                .body("matches[0].replacements", empty());
    }

    @Test
    void blankTextAnswersWithoutCallingLanguageToolOnEveryLevel() {
        help("marks");
        int before = stubCalls();
        check(publisher, Map.of("text", " ")).statusCode(200).body("matches", hasSize(0));
        assertThat(stubCalls()).isEqualTo(before);
    }

    @Test
    void capitalisedNounIsReported() {
        check(publisher, Map.of("text", "Wir haben einen hund.")).statusCode(200)
                .body("matches", hasSize(1))
                .body("matches[0].offset", equalTo(16))
                .body("matches[0].length", equalTo(4))
                .body("matches[0].replacements", hasItem("Hund"));
    }

    @Test
    void styleAdviceIsDropped() {
        int before = stubCalls();
        check(publisher, Map.of("text", "Das ist sehr sehr schön.")).statusCode(200).body("matches", hasSize(0));
        assertThat(stubCalls()).isEqualTo(before + 1);
    }

    @Test
    void offsetsAreUtf16CodeUnits() {
        check(publisher, Map.of("text", "😀 gros")).statusCode(200)
                .body("matches[0].offset", equalTo(3))
                .body("matches[0].length", equalTo(4));
    }

    @Test
    void blankTextAnswersWithoutCallingLanguageTool() {
        int before = stubCalls();
        check(publisher, Map.of("text", " \n\t ")).statusCode(200).body("matches", hasSize(0));
        check(publisher, Map.of("text", "")).statusCode(200).body("matches", hasSize(0));
        assertThat(stubCalls()).isEqualTo(before);
    }

    @Test
    void invalidTextIsRefusedWithoutCallingLanguageTool() {
        int before = stubCalls();
        check(publisher, Map.of()).statusCode(400).body("errors[0].field", equalTo("text"));
        check(publisher, Map.of("text", 5)).statusCode(400).body("errors[0].field", equalTo("text"));
        check(publisher, "[]").statusCode(400).body("errors[0].field", equalTo("text"));
        check(publisher, Map.of("text", "a".repeat(10_001))).statusCode(400)
                .body("errors[0].field", equalTo("text"))
                .body("errors[0].message", startsWith("text must be a string of at most 10000"));
        assertThat(stubCalls()).isEqualTo(before);
    }

    @Test
    void lengthLimitCountsCodePoints() {
        check(publisher, Map.of("text", "😀".repeat(10_000))).statusCode(200);
    }

    @Test
    void readerIsForbiddenWithEmptyBody() {
        check(TestSupport.token("reader", "reader"), Map.of("text", "gros")).statusCode(403)
                .body(emptyString());
    }

    @Test
    void missingTokenIsUnauthorized() {
        given().contentType(ContentType.JSON).body(Map.of("text", "gros")).post("/api/spell-check").then()
                .statusCode(401);
    }

    @Test
    void slowLanguageToolAnswersUnavailableAfterTheTimeout() {
        long start = System.nanoTime();
        check(publisher, Map.of("text", "SLOW gros")).statusCode(503)
                .body("errors", hasSize(1))
                .body("errors[0].field", nullValue())
                .body("errors[0].message", equalTo("the spell check is currently unavailable"));
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(5_900));
    }

    @Test
    void failingLanguageToolAnswersUnavailable() {
        check(publisher, Map.of("text", "FAIL")).statusCode(503).body("errors[0].field", nullValue());
        check(publisher, Map.of("text", "gros")).statusCode(200).body("matches", hasSize(1));
    }

    @Test
    void clientConfigAnnouncesTheSpellCheck() {
        given().get("/api/client-config").then().statusCode(200).body("spellCheck", equalTo(true));
    }
}
