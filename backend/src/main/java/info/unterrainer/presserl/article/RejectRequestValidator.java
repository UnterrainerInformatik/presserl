package info.unterrainer.presserl.article;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import info.unterrainer.presserl.api.FieldError;

/**
 * Reads a reject request body strictly: an object with exactly the field {@code note}, a string
 * that is not blank after trimming, at most {@value ArticleLimits#REVIEW_NOTE_MAX} characters, with
 * line feeds but no other control characters. All violations are reported together.
 */
public final class RejectRequestValidator {

    public static final String NOTE = "note";

    private RejectRequestValidator() {
    }

    /**
     * @return the trimmed note
     * @throws ArticleException with status {@code 400} listing every violation
     */
    public static String validate(JsonNode json) {
        if (json != null && !json.isNull() && !json.isObject()) {
            throw ArticleException.invalid(null, "request body must be a JSON object");
        }
        List<FieldError> errors = new ArrayList<>();
        JsonNode node = null;
        if (json != null && !json.isNull()) {
            for (Iterator<String> names = json.fieldNames(); names.hasNext();) {
                String name = names.next();
                if (!NOTE.equals(name)) {
                    errors.add(new FieldError(name, "unknown field"));
                }
            }
            node = json.get(NOTE);
        }
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
        if (!errors.isEmpty()) {
            throw ArticleException.invalid(errors);
        }
        return note;
    }
}
