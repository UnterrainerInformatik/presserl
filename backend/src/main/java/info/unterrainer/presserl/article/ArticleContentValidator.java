package info.unterrainer.presserl.article;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import info.unterrainer.presserl.api.FieldError;

/**
 * Reads an article request body strictly: the four text fields (default empty, trimmed, length
 * limited, no control characters), the body (default empty document, validated by
 * {@link ArticleBodyValidator}), the optional {@code sectionId} and, for saves, the required
 * {@code version}. Unknown fields are rejected. All violations are reported together. Whether the
 * section exists and may be written in is checked by {@link ArticleService}.
 */
public final class ArticleContentValidator {

    public static final String VERSION = "version";
    public static final String SECTION_ID = "sectionId";
    private static final Set<String> CONTENT_FIELDS = Set.of("kicker", "headline", "subheadline", "lead", "body");

    private ArticleContentValidator() {
    }

    /**
     * A validated request: the content, the requested section ({@code null} when not given) and, if
     * requested, the client's article version.
     */
    public record Request(ArticleContent content, Long sectionId, Long version) {
    }

    /**
     * Validates {@code json}; {@code withVersion} requires a {@code version} field (saves).
     *
     * @throws ArticleException with status {@code 400} listing every violation
     */
    public static Request validate(JsonNode json, boolean withVersion) {
        List<FieldError> errors = new ArrayList<>();
        if (json == null || json.isNull()) {
            json = JsonNodeFactory.instance.objectNode();
        }
        if (!json.isObject()) {
            throw ArticleException.invalid(null, "request body must be a JSON object");
        }
        for (Iterator<String> names = json.fieldNames(); names.hasNext();) {
            String name = names.next();
            if (!CONTENT_FIELDS.contains(name) && !SECTION_ID.equals(name) && !(withVersion && VERSION.equals(name))) {
                errors.add(new FieldError(name, "unknown field"));
            }
        }
        String kicker = text(json, "kicker", ArticleLimits.TEXT_FIELD_MAX, errors);
        String headline = text(json, "headline", ArticleLimits.TEXT_FIELD_MAX, errors);
        String subheadline = text(json, "subheadline", ArticleLimits.TEXT_FIELD_MAX, errors);
        String lead = text(json, "lead", ArticleLimits.LEAD_MAX, errors);
        JsonNode body = json.get("body");
        if (body == null || body.isNull()) {
            body = emptyBody();
        } else {
            errors.addAll(ArticleBodyValidator.validate(body, "body"));
        }
        Long sectionId = null;
        JsonNode section = json.get(SECTION_ID);
        if (section != null && !section.isNull()) {
            if (!section.isIntegralNumber() || !section.canConvertToLong() || section.asLong() <= 0) {
                errors.add(new FieldError(SECTION_ID, "must be a positive integer"));
            } else {
                sectionId = section.asLong();
            }
        }
        Long version = null;
        if (withVersion) {
            JsonNode v = json.get(VERSION);
            if (v == null || v.isNull()) {
                errors.add(new FieldError(VERSION, "is required"));
            } else if (!v.isIntegralNumber() || !v.canConvertToLong()) {
                errors.add(new FieldError(VERSION, "must be an integer"));
            } else {
                version = v.asLong();
            }
        }
        if (!errors.isEmpty()) {
            throw ArticleException.invalid(errors);
        }
        return new Request(new ArticleContent(kicker, headline, subheadline, lead, body), sectionId, version);
    }

    /**
     * The empty document {@code {"version":1,"blocks":[]}}.
     */
    public static ObjectNode emptyBody() {
        ObjectNode body = JsonNodeFactory.instance.objectNode();
        body.put("version", ArticleLimits.BODY_FORMAT_VERSION);
        body.putArray("blocks");
        return body;
    }

    private static String text(JsonNode json, String field, int max, List<FieldError> errors) {
        JsonNode node = json.get(field);
        if (node == null || node.isNull()) {
            return "";
        }
        if (!node.isTextual()) {
            errors.add(new FieldError(field, "must be a string"));
            return "";
        }
        String value = node.asText();
        if (TextRules.hasControlCharacter(value, false)) {
            errors.add(new FieldError(field, "must not contain control characters or line breaks"));
        }
        value = value.strip();
        if (TextRules.length(value) > max) {
            errors.add(new FieldError(field, "must be at most " + max + " characters"));
        }
        return value;
    }
}
