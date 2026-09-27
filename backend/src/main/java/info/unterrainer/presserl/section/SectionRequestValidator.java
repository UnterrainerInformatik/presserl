package info.unterrainer.presserl.section;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import info.unterrainer.presserl.api.FieldError;

/**
 * Reads section request bodies strictly and reports all violations together: the section body
 * (name trimmed, 1 to {@value #NAME_MAX} characters, no control characters; colour from the
 * palette), the order body ({@code ids}) and the member body ({@code role}); unknown fields are
 * refused everywhere.
 */
public final class SectionRequestValidator {

    public static final int NAME_MAX = 40;
    private static final Set<String> SECTION_FIELDS = Set.of("name", "color");
    private static final Set<String> ORDER_FIELDS = Set.of("ids");
    private static final Set<String> MEMBER_FIELDS = Set.of("role");

    private SectionRequestValidator() {
    }

    /**
     * @param colorRequired whether {@code color} must be present (update) or may be left out
     *                      (creation)
     * @throws SectionException with status {@code 400} listing every violation
     */
    public static SectionInput section(JsonNode json, boolean colorRequired) {
        List<FieldError> errors = object(json, SECTION_FIELDS);
        String name = name(json, errors);
        SectionColor color = color(json, colorRequired, errors);
        if (!errors.isEmpty()) {
            throw SectionException.invalid(errors);
        }
        return new SectionInput(name, color);
    }

    /**
     * The section ids of {@code PUT /api/sections/order} in the requested order.
     */
    public static List<Long> order(JsonNode json) {
        List<FieldError> errors = object(json, ORDER_FIELDS);
        JsonNode node = json.get("ids");
        List<Long> ids = new ArrayList<>();
        if (node == null || !node.isArray()) {
            errors.add(new FieldError("ids", "must be an array of section ids"));
        } else {
            for (JsonNode id : node) {
                if (id.isIntegralNumber() && id.canConvertToLong()) {
                    ids.add(id.asLong());
                } else {
                    errors.add(new FieldError("ids", "must contain section ids only, not " + id));
                }
            }
        }
        if (!errors.isEmpty()) {
            throw SectionException.invalid(errors);
        }
        return List.copyOf(ids);
    }

    /**
     * The role of {@code PUT /api/sections/{id}/members/{accountId}}.
     */
    public static SectionRole member(JsonNode json) {
        List<FieldError> errors = object(json, MEMBER_FIELDS);
        JsonNode node = json.get("role");
        SectionRole role = node != null && node.isTextual() ? Arrays.stream(SectionRole.values())
                .filter(r -> r.name().equals(node.asText())).findFirst().orElse(null) : null;
        if (role == null) {
            errors.add(new FieldError("role", "must be one of " + Arrays.toString(SectionRole.values())));
        }
        if (!errors.isEmpty()) {
            throw SectionException.invalid(errors);
        }
        return role;
    }

    private static List<FieldError> object(JsonNode json, Set<String> fields) {
        if (json == null || !json.isObject()) {
            throw SectionException.invalid(null, "request body must be a JSON object");
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

    private static String name(JsonNode json, List<FieldError> errors) {
        JsonNode node = json.get("name");
        if (node == null || node.isNull()) {
            errors.add(new FieldError("name", "is required"));
            return "";
        }
        if (!node.isTextual()) {
            errors.add(new FieldError("name", "must be a string"));
            return "";
        }
        String value = node.asText();
        if (value.codePoints().anyMatch(c -> Character.getType(c) == Character.CONTROL)) {
            errors.add(new FieldError("name", "must not contain control characters or line breaks"));
        }
        value = value.strip();
        if (value.isEmpty()) {
            errors.add(new FieldError("name", "must not be blank"));
        } else if (value.codePointCount(0, value.length()) > NAME_MAX) {
            errors.add(new FieldError("name", "must be at most " + NAME_MAX + " characters"));
        }
        return value;
    }

    private static SectionColor color(JsonNode json, boolean required, List<FieldError> errors) {
        JsonNode node = json.get("color");
        if (node == null || node.isNull()) {
            if (required) {
                errors.add(new FieldError("color", "is required"));
            }
            return null;
        }
        SectionColor color = node.isTextual() ? SectionColor.ofKey(node.asText()).orElse(null) : null;
        if (color == null) {
            errors.add(new FieldError("color", "must be one of "
                    + Arrays.stream(SectionColor.values()).map(SectionColor::key).toList()));
        }
        return color;
    }
}
