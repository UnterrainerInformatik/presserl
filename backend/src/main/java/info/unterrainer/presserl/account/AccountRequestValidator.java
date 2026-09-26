package info.unterrainer.presserl.account;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

import info.unterrainer.presserl.api.FieldError;
import info.unterrainer.presserl.auth.NewspaperRole;

/**
 * Reads a {@code POST /api/accounts} body strictly and reports all violations together: names
 * (trimmed, at most {@value #NAME_MAX} characters, no control characters, first name required),
 * username (lower-case words joined by {@code -}, {@value UsernameDeriver#MIN_LENGTH} to
 * {@value UsernameDeriver#MAX_LENGTH} characters, not a service-account name), roles (at least one known role; duplicates collapsed)
 * and no unknown fields.
 */
public final class AccountRequestValidator {

    public static final int NAME_MAX = 100;
    static final Pattern USERNAME = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    static final String SERVICE_ACCOUNT_PREFIX = "service-account-";
    private static final Set<String> FIELDS = Set.of("firstName", "lastName", "username", "roles");

    private AccountRequestValidator() {
    }

    /**
     * @throws AccountException with status {@code 400} listing every violation
     */
    public static CreateAccountRequest validate(JsonNode json) {
        if (json == null || !json.isObject()) {
            throw AccountException.invalid(null, "request body must be a JSON object");
        }
        List<FieldError> errors = new ArrayList<>();
        for (Iterator<String> names = json.fieldNames(); names.hasNext();) {
            String name = names.next();
            if (!FIELDS.contains(name)) {
                errors.add(new FieldError(name, "unknown field"));
            }
        }
        String firstName = name(json, "firstName", true, errors);
        String lastName = name(json, "lastName", false, errors);
        String username = username(json, errors);
        List<NewspaperRole> roles = roles(json, errors);
        if (!errors.isEmpty()) {
            throw AccountException.invalid(errors);
        }
        return new CreateAccountRequest(firstName, lastName, username, roles);
    }

    private static String name(JsonNode json, String field, boolean required, List<FieldError> errors) {
        JsonNode node = json.get(field);
        if (node == null || node.isNull()) {
            if (required) {
                errors.add(new FieldError(field, "is required"));
            }
            return "";
        }
        if (!node.isTextual()) {
            errors.add(new FieldError(field, "must be a string"));
            return "";
        }
        String value = node.asText();
        if (value.codePoints().anyMatch(c -> Character.getType(c) == Character.CONTROL)) {
            errors.add(new FieldError(field, "must not contain control characters or line breaks"));
        }
        value = value.strip();
        if (required && value.isEmpty()) {
            errors.add(new FieldError(field, "must not be blank"));
        } else if (value.codePointCount(0, value.length()) > NAME_MAX) {
            errors.add(new FieldError(field, "must be at most " + NAME_MAX + " characters"));
        }
        return value;
    }

    private static String username(JsonNode json, List<FieldError> errors) {
        JsonNode node = json.get("username");
        if (node == null || node.isNull()) {
            errors.add(new FieldError("username", "is required"));
            return null;
        }
        if (!node.isTextual()) {
            errors.add(new FieldError("username", "must be a string"));
            return null;
        }
        String value = node.asText();
        if (value.length() < UsernameDeriver.MIN_LENGTH) {
            errors.add(new FieldError("username", "must be at least " + UsernameDeriver.MIN_LENGTH + " characters"));
        } else if (value.length() > UsernameDeriver.MAX_LENGTH) {
            errors.add(new FieldError("username", "must be at most " + UsernameDeriver.MAX_LENGTH + " characters"));
        } else if (!USERNAME.matcher(value).matches()) {
            errors.add(new FieldError("username",
                    "must consist of lower-case letters a-z and digits, optionally joined by single '-'"));
        } else if (value.startsWith(SERVICE_ACCOUNT_PREFIX)) {
            errors.add(new FieldError("username", "must not start with '" + SERVICE_ACCOUNT_PREFIX + "'"));
        }
        return value;
    }

    private static List<NewspaperRole> roles(JsonNode json, List<FieldError> errors) {
        JsonNode node = json.get("roles");
        if (node == null || node.isNull()) {
            errors.add(new FieldError("roles", "is required"));
            return List.of();
        }
        if (!node.isArray()) {
            errors.add(new FieldError("roles", "must be an array"));
            return List.of();
        }
        if (node.isEmpty()) {
            errors.add(new FieldError("roles", "must contain at least one role"));
            return List.of();
        }
        EnumSet<NewspaperRole> roles = EnumSet.noneOf(NewspaperRole.class);
        for (JsonNode role : node) {
            NewspaperRole value = role.isTextual() ? role(role.asText()) : null;
            if (value == null) {
                errors.add(new FieldError("roles", "unknown role " + role + "; allowed: "
                        + Arrays.toString(NewspaperRole.values())));
            } else {
                roles.add(value);
            }
        }
        return List.copyOf(roles);
    }

    private static NewspaperRole role(String name) {
        return Arrays.stream(NewspaperRole.values()).filter(r -> r.name().equals(name)).findFirst().orElse(null);
    }
}
