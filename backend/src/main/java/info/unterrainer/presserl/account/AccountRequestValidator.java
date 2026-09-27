package info.unterrainer.presserl.account;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

import info.unterrainer.presserl.api.FieldError;
import info.unterrainer.presserl.auth.NewspaperRole;
import info.unterrainer.presserl.section.SectionRole;
import info.unterrainer.presserl.section.SectionRoleDto;

/**
 * Reads a {@code POST /api/accounts} body strictly and reports all violations together: names
 * (trimmed, at most {@value #NAME_MAX} characters, no control characters, first name required),
 * username (lower-case words joined by {@code -}, {@value UsernameDeriver#MIN_LENGTH} to
 * {@value UsernameDeriver#MAX_LENGTH} characters, not a service-account name), roles (known roles;
 * duplicates collapsed), section roles (a list of {@code sectionId} and a known {@code role}, each
 * section once), at least one role of either kind and no unknown fields.
 */
public final class AccountRequestValidator {

    public static final int NAME_MAX = 100;
    static final Pattern USERNAME = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    static final String SERVICE_ACCOUNT_PREFIX = "service-account-";
    private static final Set<String> FIELDS = Set.of("firstName", "lastName", "username", "roles", "sectionRoles");
    private static final Set<String> SECTION_ROLE_FIELDS = Set.of("sectionId", "role");

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
        List<SectionRoleDto> sectionRoles = sectionRoles(json, errors);
        boolean rolesValid = errors.stream()
                .noneMatch(e -> "roles".equals(e.field()) || "sectionRoles".equals(e.field()));
        if (rolesValid && roles.isEmpty() && sectionRoles.isEmpty()) {
            errors.add(new FieldError("roles", "must contain at least one role, or sectionRoles one section role"));
        }
        if (!errors.isEmpty()) {
            throw AccountException.invalid(errors);
        }
        return new CreateAccountRequest(firstName, lastName, username, roles, sectionRoles);
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

    /**
     * The section roles, empty when absent.
     */
    private static List<SectionRoleDto> sectionRoles(JsonNode json, List<FieldError> errors) {
        JsonNode node = json.get("sectionRoles");
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            errors.add(new FieldError("sectionRoles", "must be an array"));
            return List.of();
        }
        List<SectionRoleDto> sectionRoles = new ArrayList<>();
        Set<Long> sections = new HashSet<>();
        for (JsonNode item : node) {
            JsonNode sectionId = item.get("sectionId");
            JsonNode role = item.get("role");
            SectionRole value = role != null && role.isTextual() ? sectionRole(role.asText()) : null;
            if (sectionId == null || !sectionId.isIntegralNumber() || !sectionId.canConvertToLong() || role == null) {
                errors.add(new FieldError("sectionRoles",
                        "each entry must have a numeric sectionId and a role: " + item));
            } else if (!SECTION_ROLE_FIELDS.containsAll(iterable(item.fieldNames()))) {
                errors.add(new FieldError("sectionRoles",
                        "unknown field in " + item + "; allowed: " + SECTION_ROLE_FIELDS));
            } else if (value == null) {
                errors.add(new FieldError("sectionRoles", "unknown role " + role + "; allowed: "
                        + Arrays.toString(SectionRole.values())));
            } else if (!sections.add(sectionId.asLong())) {
                errors.add(new FieldError("sectionRoles", "section " + sectionId.asLong() + " is named twice"));
            } else {
                sectionRoles.add(new SectionRoleDto(sectionId.asLong(), value));
            }
        }
        return List.copyOf(sectionRoles);
    }

    private static List<String> iterable(Iterator<String> names) {
        List<String> list = new ArrayList<>();
        names.forEachRemaining(list::add);
        return list;
    }

    private static SectionRole sectionRole(String name) {
        return Arrays.stream(SectionRole.values()).filter(r -> r.name().equals(name)).findFirst().orElse(null);
    }

    private static NewspaperRole role(String name) {
        return Arrays.stream(NewspaperRole.values()).filter(r -> r.name().equals(name)).findFirst().orElse(null);
    }
}
