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
 * {@link ArticleBodyValidator}), the optional {@code leadImage} ({@code mediaId} and
 * {@code caption}), the optional {@code sectionId} and, for saves, the required {@code version}.
 * Unknown fields are rejected, also inside {@code leadImage}. All violations are reported together.
 * Whether the section and the media exist and the section may be written in is checked by
 * {@link ArticleService}.
 */
public final class ArticleContentValidator {

    public static final String VERSION = "version";
    public static final String SECTION_ID = "sectionId";
    public static final String LEAD_IMAGE = "leadImage";
    public static final String LEAD_IMAGE_MEDIA_ID = "leadImage.mediaId";
    public static final String LEAD_IMAGE_CAPTION = "leadImage.caption";
    private static final Set<String> CONTENT_FIELDS = Set.of("kicker", "headline", "subheadline", "lead", "body",
            LEAD_IMAGE);
    private static final Set<String> LEAD_IMAGE_FIELDS = Set.of("mediaId", "caption");

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
        ArticleContent.LeadImage leadImage = leadImage(json.get(LEAD_IMAGE), errors);
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
        return new Request(new ArticleContent(kicker, headline, subheadline, lead, body, leadImage), sectionId, version);
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

    /**
     * The lead image, {@code null} when absent or {@code null}; violations are named
     * {@code leadImage.<field>}.
     */
    private static ArticleContent.LeadImage leadImage(JsonNode node, List<FieldError> errors) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isObject()) {
            errors.add(new FieldError(LEAD_IMAGE, "must be an object or null"));
            return null;
        }
        for (Iterator<String> names = node.fieldNames(); names.hasNext();) {
            String name = names.next();
            if (!LEAD_IMAGE_FIELDS.contains(name)) {
                errors.add(new FieldError(LEAD_IMAGE + "." + name, "unknown field"));
            }
        }
        long mediaId = 0;
        JsonNode id = node.get("mediaId");
        if (id == null || id.isNull()) {
            errors.add(new FieldError(LEAD_IMAGE_MEDIA_ID, "is required"));
        } else if (!id.isIntegralNumber() || !id.canConvertToLong() || id.asLong() <= 0) {
            errors.add(new FieldError(LEAD_IMAGE_MEDIA_ID, "must be a positive integer"));
        } else {
            mediaId = id.asLong();
        }
        String caption = text(node, "caption", LEAD_IMAGE_CAPTION, ArticleLimits.CAPTION_MAX, errors);
        return mediaId > 0 ? new ArticleContent.LeadImage(mediaId, caption) : null;
    }

    private static String text(JsonNode json, String field, int max, List<FieldError> errors) {
        return text(json, field, field, max, errors);
    }

    /**
     * @param name the field name in error messages
     */
    private static String text(JsonNode json, String field, String name, int max, List<FieldError> errors) {
        JsonNode node = json.get(field);
        if (node == null || node.isNull()) {
            return "";
        }
        if (!node.isTextual()) {
            errors.add(new FieldError(name, "must be a string"));
            return "";
        }
        String value = node.asText();
        if (TextRules.hasControlCharacter(value, false)) {
            errors.add(new FieldError(name, "must not contain control characters or line breaks"));
        }
        value = value.strip();
        if (TextRules.length(value) > max) {
            errors.add(new FieldError(name, "must be at most " + max + " characters"));
        }
        return value;
    }
}
