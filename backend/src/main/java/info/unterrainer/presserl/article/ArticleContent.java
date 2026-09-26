package info.unterrainer.presserl.article;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Validated content of an article revision: trimmed plain-text fields and a body in format v1.
 */
public record ArticleContent(String kicker, String headline, String subheadline, String lead, JsonNode body) {
}
