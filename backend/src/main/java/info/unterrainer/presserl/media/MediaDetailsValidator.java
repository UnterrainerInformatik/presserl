package info.unterrainer.presserl.media;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

import info.unterrainer.presserl.api.FieldError;

/**
 * Reads and normalises the description and tags of a media, for {@code PUT /api/media/{id}/details}
 * ({@link #parse}) and the text parts of an upload ({@link #of}); each reports all violations together.
 * A tag is normalised (outer whitespace removed, inner runs collapsed to one space) before it is checked;
 * tags differing only in case are one tag, compared by {@link #key}.
 */
public final class MediaDetailsValidator {

    public static final String DESCRIPTION = "description";
    public static final String TAGS = "tags";
    public static final int MAX_DESCRIPTION_LENGTH = 1000;
    public static final int MAX_TAGS = 20;
    public static final int MAX_TAG_LENGTH = 40;

    /**
     * Case-insensitive order of stored tags.
     */
    public static final Comparator<String> TAG_ORDER = Comparator.comparing(MediaDetailsValidator::key)
            .thenComparing(Comparator.naturalOrder());

    private static final Set<String> FIELDS = Set.of(DESCRIPTION, TAGS);
    private static final Pattern WHITESPACE = Pattern.compile("(?U)\\s+");

    private MediaDetailsValidator() {
    }

    /**
     * Normalised details: {@code description} trimmed or {@code null}, {@code tags} normalised and free
     * of case-insensitive duplicates (the first spelling wins), in request order.
     */
    public record Details(String description, List<String> tags) {
    }

    /**
     * Reads the JSON body of {@code PUT /api/media/{id}/details}: both fields required, no others.
     *
     * @throws MediaException {@code 400} listing every violation
     */
    public static Details parse(JsonNode json) {
        if (json == null || !json.isObject()) {
            throw MediaException.invalid(List.of(new FieldError(null, "request body must be a JSON object")));
        }
        List<FieldError> errors = new ArrayList<>();
        for (Iterator<String> names = json.fieldNames(); names.hasNext();) {
            String name = names.next();
            if (!FIELDS.contains(name)) {
                errors.add(new FieldError(name, "unknown field"));
            }
        }
        String description = null;
        JsonNode descriptionNode = json.get(DESCRIPTION);
        if (descriptionNode == null) {
            errors.add(new FieldError(DESCRIPTION, "is required (null for none)"));
        } else if (!descriptionNode.isNull() && !descriptionNode.isTextual()) {
            errors.add(new FieldError(DESCRIPTION, "must be a text or null"));
        } else if (descriptionNode.isTextual()) {
            description = descriptionNode.textValue();
        }
        List<String> tags = null;
        JsonNode tagsNode = json.get(TAGS);
        if (tagsNode == null || tagsNode.isNull()) {
            errors.add(new FieldError(TAGS, "is required (an empty list for none)"));
        } else if (!tagsNode.isArray()) {
            errors.add(new FieldError(TAGS, "must be a list of texts"));
        } else {
            tags = new ArrayList<>();
            for (int i = 0; i < tagsNode.size(); i++) {
                JsonNode tag = tagsNode.get(i);
                if (!tag.isTextual()) {
                    errors.add(new FieldError(tag(i), "must be a text"));
                    tags.add(null);
                } else {
                    tags.add(tag.textValue());
                }
            }
        }
        return check(description, tags, errors);
    }

    /**
     * Reads the text parts of an upload: at most one {@code description}, any number of {@code tag}
     * parts ({@code tags[i]} counts them from 0).
     *
     * @throws MediaException {@code 400} listing every violation
     */
    public static Details of(List<String> descriptions, List<String> tags) {
        List<FieldError> errors = new ArrayList<>();
        String description = null;
        if (descriptions != null && descriptions.size() > 1) {
            errors.add(new FieldError(DESCRIPTION, "must be given at most once"));
        } else if (descriptions != null && !descriptions.isEmpty()) {
            description = descriptions.getFirst();
        }
        return check(description, tags == null ? List.of() : tags, errors);
    }

    /**
     * Normalises and checks; {@code tags} may be {@code null} (already reported) or hold {@code null}
     * entries (already reported).
     */
    private static Details check(String rawDescription, List<String> rawTags, List<FieldError> errors) {
        String description = null;
        if (rawDescription != null) {
            String trimmed = rawDescription.strip();
            if (trimmed.codePointCount(0, trimmed.length()) > MAX_DESCRIPTION_LENGTH) {
                errors.add(new FieldError(DESCRIPTION, "must be at most " + MAX_DESCRIPTION_LENGTH + " characters"));
            } else if (!trimmed.isEmpty()) {
                description = trimmed;
            }
        }
        Map<String, String> tags = new LinkedHashMap<>();
        if (rawTags != null) {
            for (int i = 0; i < rawTags.size(); i++) {
                String raw = rawTags.get(i);
                if (raw == null) {
                    continue;
                }
                String tag = normalizeTag(raw);
                String problem = tagProblem(tag);
                if (problem != null) {
                    errors.add(new FieldError(tag(i), problem));
                } else {
                    tags.putIfAbsent(key(tag), tag);
                }
            }
            if (tags.size() > MAX_TAGS) {
                errors.add(new FieldError(TAGS, "must hold at most " + MAX_TAGS + " different tags"));
            }
        }
        if (!errors.isEmpty()) {
            throw MediaException.invalid(errors);
        }
        return new Details(description, List.copyOf(tags.values()));
    }

    /**
     * Outer whitespace removed and every inner run of whitespace replaced by one space.
     */
    public static String normalizeTag(String tag) {
        return WHITESPACE.matcher(tag).replaceAll(" ").strip();
    }

    /**
     * Why a normalised tag is invalid, {@code null} when it is valid.
     */
    public static String tagProblem(String tag) {
        int length = tag.codePointCount(0, tag.length());
        if (length == 0) {
            return "must not be empty";
        }
        if (length > MAX_TAG_LENGTH) {
            return "must be at most " + MAX_TAG_LENGTH + " characters";
        }
        if (tag.indexOf(',') >= 0) {
            return "must not contain a comma";
        }
        if (tag.codePoints().anyMatch(Character::isISOControl)) {
            return "must not contain control characters";
        }
        return null;
    }

    /**
     * The comparison key of a normalised tag.
     */
    public static String key(String tag) {
        return tag.toLowerCase(Locale.ROOT);
    }

    private static String tag(int index) {
        return TAGS + "[" + index + "]";
    }
}
