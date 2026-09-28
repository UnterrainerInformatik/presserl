package info.unterrainer.presserl.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import info.unterrainer.presserl.api.FieldError;

/**
 * The {@code leadImage} part of {@link ArticleContentValidator}.
 */
class ArticleContentValidatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode json(String json) {
        try {
            return MAPPER.readTree(json.replace('\'', '"'));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static ArticleContent.LeadImage leadImage(String json) {
        return ArticleContentValidator.validate(json(json), false).content().leadImage();
    }

    private static List<String> errors(JsonNode json) {
        try {
            ArticleContentValidator.validate(json, false);
        } catch (ArticleException e) {
            return e.errors().stream().map(FieldError::field).toList();
        }
        throw new AssertionError("expected a validation error");
    }

    private static List<String> errors(String json) {
        return errors(json(json));
    }

    @Test
    void absentOrNullMeansNoLeadImage() {
        assertThat(leadImage("{}")).isNull();
        assertThat(leadImage("{'leadImage': null}")).isNull();
    }

    @Test
    void onlyMediaIdGivesAnEmptyCaption() {
        assertThat(leadImage("{'leadImage': {'mediaId': 17}}")).isEqualTo(new ArticleContent.LeadImage(17, ""));
        assertThat(leadImage("{'leadImage': {'mediaId': 17, 'caption': null}}"))
                .isEqualTo(new ArticleContent.LeadImage(17, ""));
    }

    @Test
    void captionIsTrimmed() {
        assertThat(leadImage("{'leadImage': {'mediaId': 17, 'caption': '  Our cat Minka  '}}"))
                .isEqualTo(new ArticleContent.LeadImage(17, "Our cat Minka"));
    }

    @Test
    void captionLimit() {
        ObjectNode ok = MAPPER.createObjectNode();
        ok.putObject("leadImage").put("mediaId", 17).put("caption", "c".repeat(ArticleLimits.CAPTION_MAX));
        assertThat(ArticleContentValidator.validate(ok, false).content().leadImage().caption())
                .hasSize(ArticleLimits.CAPTION_MAX);

        ObjectNode tooLong = MAPPER.createObjectNode();
        tooLong.putObject("leadImage").put("mediaId", 17).put("caption", "c".repeat(ArticleLimits.CAPTION_MAX + 1));
        assertThat(errors(tooLong)).containsExactly("leadImage.caption");
    }

    @Test
    void lineBreakInCaptionIsInvalid() {
        assertThat(errors("{'leadImage': {'mediaId': 17, 'caption': 'Our\\ncat'}}")).containsExactly("leadImage.caption");
    }

    @Test
    void captionMustBeAString() {
        assertThat(errors("{'leadImage': {'mediaId': 17, 'caption': 5}}")).containsExactly("leadImage.caption");
    }

    @Test
    void mediaIdMustBeAPositiveInteger() {
        assertThat(errors("{'leadImage': {'mediaId': 0}}")).containsExactly("leadImage.mediaId");
        assertThat(errors("{'leadImage': {'mediaId': -3}}")).containsExactly("leadImage.mediaId");
        assertThat(errors("{'leadImage': {'mediaId': '17'}}")).containsExactly("leadImage.mediaId");
        assertThat(errors("{'leadImage': {'mediaId': 1.5}}")).containsExactly("leadImage.mediaId");
        assertThat(errors("{'leadImage': {'caption': 'x'}}")).containsExactly("leadImage.mediaId");
        assertThat(errors("{'leadImage': {'mediaId': null}}")).containsExactly("leadImage.mediaId");
    }

    @Test
    void unknownNestedFieldIsNamed() {
        assertThat(errors("{'leadImage': {'mediaId': 17, 'alt': 'x'}}")).containsExactly("leadImage.alt");
    }

    @Test
    void leadImageMustBeAnObject() {
        assertThat(errors("{'leadImage': 17}")).containsExactly("leadImage");
        assertThat(errors("{'leadImage': [17]}")).containsExactly("leadImage");
        assertThat(errors("{'leadImage': 'cat.jpg'}")).containsExactly("leadImage");
    }

    @Test
    void errorsAreReportedTogetherWithOtherFields() {
        assertThatThrownBy(() -> ArticleContentValidator.validate(json(
                "{'headline': 'a\\nb', 'leadImage': {'mediaId': 'x', 'caption': 'a\\tb', 'credit': 'me'}, 'foo': 1}"),
                false))
                .isInstanceOfSatisfying(ArticleException.class, e -> assertThat(e.errors())
                        .extracting(FieldError::field)
                        .containsExactlyInAnyOrder("foo", "headline", "leadImage.credit", "leadImage.mediaId",
                                "leadImage.caption"));
    }
}
