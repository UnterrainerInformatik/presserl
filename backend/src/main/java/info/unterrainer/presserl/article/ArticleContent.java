package info.unterrainer.presserl.article;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Validated content of an article revision: trimmed plain-text fields, a body in format v1 and the
 * optional lead image ({@code null} for none).
 */
public record ArticleContent(String kicker, String headline, String subheadline, String lead, JsonNode body,
        LeadImage leadImage) {

    /**
     * The lead image: an existing media and its trimmed caption (empty for none).
     */
    public record LeadImage(long mediaId, String caption) {
    }
}
