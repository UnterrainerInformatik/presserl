package info.unterrainer.presserl.issue;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import info.unterrainer.presserl.api.FieldError;

/**
 * Reads issue request bodies strictly and reports all violations together: the issue body
 * ({@code publicationDate}, an ISO date {@code yyyy-mm-dd} or {@code null}) and the articles body
 * ({@code articleIds}, article ids without repetition); unknown fields are refused everywhere.
 */
public final class IssueRequestValidator {

    public static final String PUBLICATION_DATE = "publicationDate";
    public static final String ARTICLE_IDS = "articleIds";

    private static final Set<String> ISSUE_FIELDS = Set.of(PUBLICATION_DATE);
    private static final Set<String> ARTICLES_FIELDS = Set.of(ARTICLE_IDS);

    private IssueRequestValidator() {
    }

    /**
     * @param dateRequired whether {@code publicationDate} must be present (update) or may be left out
     *                     (creation)
     * @throws IssueException with status {@code 400} listing every violation
     */
    public static IssueDateInput issue(JsonNode json, boolean dateRequired) {
        List<FieldError> errors = object(json, ISSUE_FIELDS);
        JsonNode node = json.get(PUBLICATION_DATE);
        LocalDate date = null;
        if (node == null) {
            if (dateRequired) {
                errors.add(new FieldError(PUBLICATION_DATE, "is required; send null to clear it"));
            }
        } else if (!node.isNull()) {
            date = node.isTextual() ? date(node.asText()) : null;
            if (date == null) {
                errors.add(new FieldError(PUBLICATION_DATE, "must be a date yyyy-mm-dd or null, not " + node));
            }
        }
        if (!errors.isEmpty()) {
            throw IssueException.invalid(errors);
        }
        return new IssueDateInput(date);
    }

    /**
     * The article ids of {@code PUT /api/issues/{id}/articles} in the requested order; whether they
     * exist is checked by {@link IssueService#setArticles}.
     */
    public static List<Long> articleIds(JsonNode json) {
        List<FieldError> errors = object(json, ARTICLES_FIELDS);
        JsonNode node = json.get(ARTICLE_IDS);
        List<Long> ids = new ArrayList<>();
        if (node == null || !node.isArray()) {
            errors.add(new FieldError(ARTICLE_IDS, "must be an array of article ids"));
        } else {
            Set<Long> seen = new HashSet<>();
            for (JsonNode id : node) {
                if (!id.isIntegralNumber() || !id.canConvertToLong()) {
                    errors.add(new FieldError(ARTICLE_IDS, "must contain article ids only, not " + id));
                } else if (!seen.add(id.asLong())) {
                    errors.add(new FieldError(ARTICLE_IDS, "contains article " + id.asLong() + " more than once"));
                } else {
                    ids.add(id.asLong());
                }
            }
        }
        if (!errors.isEmpty()) {
            throw IssueException.invalid(errors);
        }
        return List.copyOf(ids);
    }

    private static LocalDate date(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static List<FieldError> object(JsonNode json, Set<String> fields) {
        if (json == null || !json.isObject()) {
            throw IssueException.invalid(null, "request body must be a JSON object");
        }
        List<FieldError> errors = new ArrayList<>();
        for (Iterator<String> names = json.fieldNames(); names.hasNext();) {
            String name = names.next();
            if (!fields.contains(name)) {
                errors.add(new FieldError(name, "unknown field"));
            }
        }
        return errors;
    }
}
