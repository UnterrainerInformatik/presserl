package info.unterrainer.presserl.article;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import info.unterrainer.presserl.api.FieldError;

/**
 * Reads the bodies of the review decisions strictly. A reject body is an object with the field
 * {@code note}, a string that is not blank after trimming, at most
 * {@value ArticleLimits#REVIEW_NOTE_MAX} characters, with line feeds but no other control characters,
 * and the optional field {@code version}. An approve body is optional and holds {@code version} only.
 * {@code version} is the article version the reviewer saw, a non-negative integer. All violations are
 * reported together.
 */
public final class RejectRequestValidator {

    public static final String NOTE = "note";
    public static final String VERSION = "version";

    /**
     * @param note    trimmed
     * @param version {@code null} when not sent
     */
    public record Request(String note, Long version) {
    }

    private RejectRequestValidator() {
    }

    /**
     * @throws ArticleException with status {@code 400} listing every violation
     */
    public static Request validate(JsonNode json) {
        requireObject(json);
        List<FieldError> errors = new ArrayList<>();
        unknownFields(json, Set.of(NOTE, VERSION), errors);
        JsonNode node = json == null || json.isNull() ? null : json.get(NOTE);
        String note = null;
        if (node == null || node.isNull()) {
            errors.add(new FieldError(NOTE, "is required"));
        } else if (!node.isTextual()) {
            errors.add(new FieldError(NOTE, "must be a string"));
        } else {
            String value = node.asText();
            if (TextRules.hasControlCharacter(value, true)) {
                errors.add(new FieldError(NOTE, "must not contain control characters other than line feed"));
            }
            note = value.strip();
            if (note.isEmpty()) {
                errors.add(new FieldError(NOTE, "must not be blank"));
            } else if (TextRules.length(note) > ArticleLimits.REVIEW_NOTE_MAX) {
                errors.add(new FieldError(NOTE, "must be at most " + ArticleLimits.REVIEW_NOTE_MAX + " characters"));
            }
        }
        Long version = version(json, errors);
        if (!errors.isEmpty()) {
            throw ArticleException.invalid(errors);
        }
        return new Request(note, version);
    }

    /**
     * Reads an approve body, which may be absent.
     *
     * @return the version, {@code null} when not sent
     * @throws ArticleException with status {@code 400} listing every violation
     */
    public static Long approveVersion(JsonNode json) {
        requireObject(json);
        List<FieldError> errors = new ArrayList<>();
        unknownFields(json, Set.of(VERSION), errors);
        Long version = version(json, errors);
        if (!errors.isEmpty()) {
            throw ArticleException.invalid(errors);
        }
        return version;
    }

    private static void requireObject(JsonNode json) {
        if (json != null && !json.isNull() && !json.isObject()) {
            throw ArticleException.invalid(null, "request body must be a JSON object");
        }
    }

    private static void unknownFields(JsonNode json, Set<String> known, List<FieldError> errors) {
        if (json == null || json.isNull()) {
            return;
        }
        for (Iterator<String> names = json.fieldNames(); names.hasNext();) {
            String name = names.next();
            if (!known.contains(name)) {
                errors.add(new FieldError(name, "unknown field"));
            }
        }
    }

    private static Long version(JsonNode json, List<FieldError> errors) {
        JsonNode node = json == null || json.isNull() ? null : json.get(VERSION);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isIntegralNumber() || !node.canConvertToLong() || node.longValue() < 0) {
            errors.add(new FieldError(VERSION, "must be a non-negative integer"));
            return null;
        }
        return node.longValue();
    }
}
