package info.unterrainer.presserl.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import info.unterrainer.presserl.api.FieldError;

class ArticleBodyValidatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode json(String json) {
        try {
            return MAPPER.readTree(json.replace('\'', '"'));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static JsonNode body(String blocks) {
        return json("{'version': 1, 'blocks': [" + blocks + "]}");
    }

    private static List<String> fields(JsonNode body) {
        return ArticleBodyValidator.validate(body, "body").stream().map(FieldError::field).toList();
    }

    @Test
    void emptyDocumentIsValid() {
        assertThat(fields(ArticleContentValidator.emptyBody())).isEmpty();
    }

    @Test
    void paragraphWithBoldRunIsValid() {
        assertThat(fields(body("{'type': 'paragraph', 'content': [{'text': 'It '}, {'text': 'grew', 'bold': true}, "
                + "{'text': 'x', 'bold': false}]}"))).isEmpty();
    }

    @Test
    void subheadIsValid() {
        assertThat(fields(body("{'type': 'subhead', 'text': 'Watering'}"))).isEmpty();
    }

    @Test
    void quoteIsValid() {
        assertThat(fields(body("{'type': 'quote', 'content': [{'text': 'Every day!'}]}"))).isEmpty();
    }

    @Test
    void listIsValid() {
        assertThat(fields(body("{'type': 'list', 'items': [[{'text': 'Water'}], [{'text': 'Sun', 'bold': true}]]}")))
                .isEmpty();
    }

    @Test
    void imageBlockIsValid() {
        assertThat(fields(body("{'type': 'paragraph', 'content': [{'text': 'x'}]}, "
                + "{'type': 'image', 'mediaId': 17, 'caption': 'The finish line'}, "
                + "{'type': 'paragraph', 'content': [{'text': 'y'}]}"))).isEmpty();
    }

    @Test
    void imageBlockWithoutCaptionIsValid() {
        assertThat(fields(body("{'type': 'image', 'mediaId': 17}"))).isEmpty();
        assertThat(fields(body("{'type': 'image', 'mediaId': 17, 'caption': ''}"))).isEmpty();
    }

    @Test
    void imageBlockWithoutMediaIdIsNamed() {
        assertThat(fields(body("{'type': 'subhead', 'text': 'a'}, {'type': 'subhead', 'text': 'b'}, "
                + "{'type': 'image', 'caption': 'x'}"))).containsExactly("body.blocks[2].mediaId");
    }

    @Test
    void invalidImageMediaIdIsNamed() {
        assertThat(fields(body("{'type': 'image', 'mediaId': 0}, {'type': 'image', 'mediaId': -3}, "
                + "{'type': 'image', 'mediaId': '17'}, {'type': 'image', 'mediaId': 1.5}")))
                .containsExactly("body.blocks[0].mediaId", "body.blocks[1].mediaId", "body.blocks[2].mediaId",
                        "body.blocks[3].mediaId");
    }

    @Test
    void imageCaptionRules() {
        ObjectNode body = (ObjectNode) body("{'type': 'image', 'mediaId': 17}, {'type': 'image', 'mediaId': 17}, "
                + "{'type': 'image', 'mediaId': 17}, {'type': 'image', 'mediaId': 17, 'caption': 5}");
        ((ObjectNode) body.at("/blocks/0")).put("caption", "one\ntwo");
        ((ObjectNode) body.at("/blocks/1")).put("caption", "c".repeat(ArticleLimits.CAPTION_MAX + 1));
        ((ObjectNode) body.at("/blocks/2")).put("caption", "c".repeat(ArticleLimits.CAPTION_MAX));
        assertThat(fields(body)).containsExactly("body.blocks[0].caption", "body.blocks[1].caption",
                "body.blocks[3].caption");
    }

    @Test
    void unknownImageFieldIsNamed() {
        assertThat(fields(body("{'type': 'image', 'mediaId': 17, 'src': 'http://evil'}")))
                .containsExactly("body.blocks[0].src");
    }

    @Test
    void imageCaptionCountsTowardsText() {
        ObjectNode body = ArticleContentValidator.emptyBody();
        ArrayNode blocks = (ArrayNode) body.get("blocks");
        blocks.addObject().put("type", "subhead").put("text", "a".repeat(ArticleLimits.BODY_TEXT_MAX - 10));
        blocks.addObject().put("type", "image").put("mediaId", 17).put("caption", "c".repeat(10));
        assertThat(fields(body)).isEmpty();

        ((ObjectNode) blocks.get(1)).put("caption", "c".repeat(11));
        assertThat(fields(body)).containsExactly("body");
    }

    @Test
    void lineFeedInRunIsValid() {
        ObjectNode body = (ObjectNode) body("{'type': 'paragraph', 'content': [{'text': 'x'}]}");
        ((ObjectNode) body.at("/blocks/0/content/0")).put("text", "line one\nline two");
        assertThat(fields(body)).isEmpty();
    }

    @Test
    void unknownBlockTypeIsNamed() {
        List<FieldError> errors = ArticleBodyValidator.validate(
                body("{'type': 'html', 'html': '<script>alert(1)</script>'}"), "body");
        assertThat(errors).containsExactly(new FieldError("body.blocks[0].type", "unknown block type 'html'"));
    }

    @Test
    void unknownBlockFieldIsNamed() {
        assertThat(fields(body("{'type': 'subhead', 'text': 'x', 'level': 2}"))).containsExactly("body.blocks[0].level");
    }

    @Test
    void unknownMarkIsNamed() {
        assertThat(fields(body("{'type': 'paragraph', 'content': [{'text': 'x', 'italic': true}]}")))
                .containsExactly("body.blocks[0].content[0].italic");
    }

    @Test
    void unknownTopLevelFieldIsNamed() {
        assertThat(fields(json("{'version': 1, 'blocks': [], 'meta': {}}"))).containsExactly("body.meta");
    }

    @Test
    void missingFieldsAreNamed() {
        assertThat(fields(body("{'type': 'paragraph'}, {'type': 'subhead'}, {'type': 'list'}, {'content': []}, "
                + "{'type': 'quote', 'content': [{'bold': true}]}")))
                .containsExactly("body.blocks[0].content", "body.blocks[1].text", "body.blocks[2].items",
                        "body.blocks[3].type", "body.blocks[4].content[0].text");
        assertThat(fields(json("{}"))).containsExactly("body.version", "body.blocks");
    }

    @Test
    void emptyRunTextIsRejected() {
        assertThat(fields(body("{'type': 'paragraph', 'content': [{'text': ''}]}")))
                .containsExactly("body.blocks[0].content[0].text");
    }

    @Test
    void emptyListIsRejected() {
        assertThat(fields(body("{'type': 'list', 'items': []}"))).containsExactly("body.blocks[0].items");
    }

    @Test
    void wrongTypesAreRejected() {
        assertThat(fields(body("{'type': 'paragraph', 'content': [{'text': 5, 'bold': 'yes'}]}, "
                + "{'type': 'quote', 'content': 'x'}, {'type': 7}, 'x'")))
                .containsExactly("body.blocks[0].content[0].text", "body.blocks[0].content[0].bold",
                        "body.blocks[1].content", "body.blocks[2].type", "body.blocks[3]");
        assertThat(fields(json("[]"))).containsExactly("body");
        assertThat(fields(json("{'version': 1, 'blocks': {}}"))).containsExactly("body.blocks");
    }

    @Test
    void wrongVersionIsRejected() {
        assertThat(fields(json("{'version': 2, 'blocks': []}"))).containsExactly("body.version");
        assertThat(fields(json("{'version': '1', 'blocks': []}"))).containsExactly("body.version");
    }

    @Test
    void controlCharactersAreRejected() {
        ObjectNode body = (ObjectNode) body("{'type': 'paragraph', 'content': [{'text': 'x'}]}, "
                + "{'type': 'subhead', 'text': 'x'}");
        ((ObjectNode) body.at("/blocks/0/content/0")).put("text", "bell\u0007");
        ((ObjectNode) body.at("/blocks/1")).put("text", "two\nlines");
        assertThat(fields(body)).containsExactly("body.blocks[0].content[0].text", "body.blocks[1].text");
    }

    @Test
    void blockLimitIsEnforced() {
        ObjectNode body = ArticleContentValidator.emptyBody();
        ArrayNode blocks = (ArrayNode) body.get("blocks");
        for (int i = 0; i < ArticleLimits.BODY_BLOCKS_MAX; i++) {
            blocks.addObject().put("type", "subhead").put("text", "x");
        }
        assertThat(fields(body)).isEmpty();

        blocks.addObject().put("type", "subhead").put("text", "x");
        assertThat(fields(body)).containsExactly("body.blocks");
    }

    @Test
    void textLimitIsEnforced() {
        ObjectNode body = ArticleContentValidator.emptyBody();
        ArrayNode blocks = (ArrayNode) body.get("blocks");
        String half = "a".repeat(ArticleLimits.BODY_TEXT_MAX / 2);
        blocks.addObject().put("type", "subhead").put("text", half);
        blocks.addObject().put("type", "paragraph").putArray("content").addObject().put("text", half);
        assertThat(fields(body)).isEmpty();

        blocks.addObject().put("type", "subhead").put("text", "a");
        assertThat(fields(body)).containsExactly("body");
    }

    @Test
    void multipleErrorsAreReportedTogether() {
        assertThat(fields(json("{'version': 3, 'blocks': [{'type': 'html'}, "
                + "{'type': 'paragraph', 'content': [{'text': ''}]}]}")))
                .containsExactly("body.version", "body.blocks[0].type", "body.blocks[1].content[0].text");
    }

    @Test
    void markupIsKeptVerbatim() {
        JsonNode body = body("{'type': 'paragraph', 'content': [{'text': '<b>hi</b>'}]}");
        ArticleContentValidator.Request request = ArticleContentValidator.validate(
                MAPPER.createObjectNode().set("body", body), false);
        assertThat(request.content().body().at("/blocks/0/content/0/text").asText()).isEqualTo("<b>hi</b>");
        assertThat(request.content().body()).isEqualTo(body);
    }

    @Test
    void textFieldsDefaultToEmptyAndAreTrimmed() {
        ArticleContentValidator.Request request = ArticleContentValidator.validate(
                json("{'headline': '  Hello  ', 'lead': null}"), false);
        assertThat(request.content()).isEqualTo(new ArticleContent("", "Hello", "", "", ArticleContentValidator.emptyBody(), null));
        assertThat(request.version()).isNull();
    }

    @Test
    void textFieldLimitsAndControlCharacters() {
        ObjectNode json = MAPPER.createObjectNode()
                .put("kicker", "k".repeat(ArticleLimits.TEXT_FIELD_MAX))
                .put("headline", "Hello\nWorld")
                .put("subheadline", "s".repeat(ArticleLimits.TEXT_FIELD_MAX + 1))
                .put("lead", "l".repeat(ArticleLimits.LEAD_MAX + 1));
        assertThatThrownBy(() -> ArticleContentValidator.validate(json, false))
                .isInstanceOfSatisfying(ArticleException.class, e -> assertThat(e.errors())
                        .extracting(FieldError::field).containsExactly("headline", "subheadline", "lead"));
    }

    @Test
    void unknownFieldsAndMissingVersionAreRejected() {
        assertThatThrownBy(() -> ArticleContentValidator.validate(json("{'title': 'x', 'version': 1}"), false))
                .isInstanceOfSatisfying(ArticleException.class, e -> assertThat(e.errors())
                        .extracting(FieldError::field).containsExactly("title", "version"));
        assertThatThrownBy(() -> ArticleContentValidator.validate(json("{'headline': 7}"), true))
                .isInstanceOfSatisfying(ArticleException.class, e -> assertThat(e.errors())
                        .extracting(FieldError::field).containsExactly("headline", "version"));
        assertThat(ArticleContentValidator.validate(json("{'version': 4}"), true).version()).isEqualTo(4L);
    }

    @Test
    void combinedTextAndBodyErrors() {
        ObjectNode json = MAPPER.createObjectNode().put("kicker", "k".repeat(ArticleLimits.TEXT_FIELD_MAX + 1));
        json.set("body", body("{'type': 'html'}"));
        assertThatThrownBy(() -> ArticleContentValidator.validate(json, false))
                .isInstanceOfSatisfying(ArticleException.class, e -> assertThat(e.errors())
                        .extracting(FieldError::field).containsExactly("kicker", "body.blocks[0].type"));
    }
}
