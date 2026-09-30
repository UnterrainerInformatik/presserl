package info.unterrainer.presserl.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import info.unterrainer.presserl.api.FieldError;

class RejectRequestValidatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode json(String json) throws Exception {
        return MAPPER.readTree(json);
    }

    private static JsonNode note(String note) {
        return MAPPER.createObjectNode().put("note", note);
    }

    private static void assertInvalid(JsonNode json, String... fields) {
        assertThatThrownBy(() -> RejectRequestValidator.validate(json))
                .isInstanceOfSatisfying(ArticleException.class, e -> {
                    assertThat(e.status().getStatusCode()).isEqualTo(400);
                    assertThat(e.errors()).extracting(FieldError::field).containsExactlyInAnyOrder(fields);
                });
    }

    @Test
    void trimsTheNote() {
        assertThat(RejectRequestValidator.validate(note("  Please add who scored.\n")).note()).isEqualTo("Please add who scored.");
    }

    @Test
    void keepsLineFeedsInside() {
        assertThat(RejectRequestValidator.validate(note("First\nSecond")).note()).isEqualTo("First\nSecond");
    }

    @Test
    void acceptsTheMaximumLengthInCodePoints() {
        assertThat(RejectRequestValidator.validate(note("😀".repeat(ArticleLimits.REVIEW_NOTE_MAX))).note()).hasSize(2000);
    }

    @Test
    void rejectsTooLongNotes() {
        assertInvalid(note("x".repeat(ArticleLimits.REVIEW_NOTE_MAX + 1)), "note");
    }

    @Test
    void rejectsBlankAndMissingNotes() throws Exception {
        assertInvalid(note("   \n "), "note");
        assertInvalid(note(""), "note");
        assertInvalid(json("{}"), "note");
        assertInvalid(json("{\"note\": null}"), "note");
        assertInvalid(null, "note");
    }

    @Test
    void rejectsNonStrings() throws Exception {
        assertInvalid(json("{\"note\": 3}"), "note");
        assertInvalid(json("{\"note\": [\"a\"]}"), "note");
    }

    @Test
    void rejectsControlCharactersOtherThanLineFeed() {
        assertInvalid(note("Tab\there"), "note");
        assertInvalid(note("Bell\u0007"), "note");
        assertInvalid(note("CR\r\n"), "note");
    }

    @Test
    void rejectsUnknownFieldsTogetherWithOtherErrors() throws Exception {
        assertInvalid(json("{\"note\": \"ok\", \"level\": \"PUBLISHER\"}"), "level");
        assertInvalid(json("{\"reason\": \"x\"}"), "reason", "note");
    }

    @Test
    void rejectsNonObjects() throws Exception {
        assertInvalid(json("[\"note\"]"), (String) null);
        assertInvalid(json("\"note\""), (String) null);
    }

    @Test
    void readsTheOptionalVersion() throws Exception {
        assertThat(RejectRequestValidator.validate(json("{\"note\": \"Too short\", \"version\": 7}")))
                .isEqualTo(new RejectRequestValidator.Request("Too short", 7L));
        assertThat(RejectRequestValidator.validate(note("Too short")).version()).isNull();
    }

    @Test
    void rejectsInvalidVersions() throws Exception {
        assertInvalid(json("{\"note\": \"ok\", \"version\": \"7\"}"), "version");
        assertInvalid(json("{\"note\": \"ok\", \"version\": -1}"), "version");
        assertInvalid(json("{\"note\": \"ok\", \"version\": 1.5}"), "version");
        assertInvalid(json("{\"version\": 1.5}"), "version", "note");
    }

    @Test
    void approveBodyIsOptionalAndHoldsTheVersionOnly() throws Exception {
        assertThat(RejectRequestValidator.approveVersion(null)).isNull();
        assertThat(RejectRequestValidator.approveVersion(json("{}"))).isNull();
        assertThat(RejectRequestValidator.approveVersion(json("{\"version\": 5}"))).isEqualTo(5L);
        assertThatThrownBy(() -> RejectRequestValidator.approveVersion(json("{\"note\": \"x\"}")))
                .isInstanceOfSatisfying(ArticleException.class, e -> assertThat(e.errors())
                        .extracting(FieldError::field).containsExactly("note"));
        assertThatThrownBy(() -> RejectRequestValidator.approveVersion(json("{\"version\": true}")))
                .isInstanceOfSatisfying(ArticleException.class, e -> assertThat(e.errors())
                        .extracting(FieldError::field).containsExactly("version"));
    }
}
