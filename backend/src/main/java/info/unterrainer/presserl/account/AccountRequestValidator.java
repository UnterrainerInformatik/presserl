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
import info.unterrainer.presserl.article.ApprovalLevel;
import info.unterrainer.presserl.auth.NewspaperRole;
import info.unterrainer.presserl.section.SectionRole;
import info.unterrainer.presserl.section.SectionRoleDto;
import info.unterrainer.presserl.trust.TrustScope;

/**
 * Reads a {@code POST /api/accounts}, {@code PUT /api/accounts/{id}/roles} or
 * {@code PUT /api/accounts/{id}/trust} body strictly and reports all violations together. Account
 * and role bodies: names (trimmed, at most {@value #NAME_MAX} characters, no
 * control characters, first name required), username (lower-case words joined by {@code -},
 * {@value UsernameDeriver#MIN_LENGTH} to {@value UsernameDeriver#MAX_LENGTH} characters, not a
 * service-account name), roles (known roles; duplicates collapsed), section roles (a list of
 * {@code sectionId} and a known {@code role}, each section once), an optional boolean
 * {@code sectionlessReporter}, at least one role of either kind or the marker, and no unknown fields.
 * Trust bodies: see {@link #validateTrust}.
 */
public final class AccountRequestValidator {

    public static final int NAME_MAX = 100;
    static final Pattern USERNAME = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    static final String SERVICE_ACCOUNT_PREFIX = "service-account-";
    public static final String SECTIONLESS_REPORTER = "sectionlessReporter";
    private static final Set<String> FIELDS = Set.of("firstName", "lastName", "username", "roles", "sectionRoles",
            SECTIONLESS_REPORTER);
    private static final Set<String> ROLE_FIELDS = Set.of("roles", "sectionRoles", SECTIONLESS_REPORTER);
    private static final Set<String> SECTION_ROLE_FIELDS = Set.of("sectionId", "role");
    private static final Set<String> TRUST_FIELDS = Set.of("level", "sectionId", "trusted");

    private AccountRequestValidator() {
    }

    /**
     * Whether {@code value} has the length and form of a username accepted by account creation.
     */
    public static boolean isUsername(String value) {
        return value != null && value.length() >= UsernameDeriver.MIN_LENGTH
                && value.length() <= UsernameDeriver.MAX_LENGTH && USERNAME.matcher(value).matches();
    }

    /**
     * A {@code POST /api/accounts} body; {@code sectionRoles} may be absent.
     *
     * @throws AccountException with status {@code 400} listing every violation
     */
    public static CreateAccountRequest validate(JsonNode json) {
        List<FieldError> errors = unknownFields(json, FIELDS);
        String firstName = name(json, "firstName", true, errors);
        String lastName = name(json, "lastName", false, errors);
        String username = username(json, errors);
        List<NewspaperRole> roles = roles(json, errors);
        List<SectionRoleDto> sectionRoles = sectionRoles(json, false, errors);
        Boolean marker = marker(json, errors);
        failOnViolations(roles, sectionRoles, Boolean.TRUE.equals(marker), true, errors);
        return new CreateAccountRequest(firstName, lastName, username, roles, sectionRoles,
                Boolean.TRUE.equals(marker));
    }

    /**
     * A {@code PUT /api/accounts/{id}/roles} body; {@code roles} and {@code sectionRoles} are
     * required, so a client forgetting {@code sectionRoles} cannot remove every section role by
     * accident. Without {@code sectionlessReporter}, whether a role remains depends on the account's
     * marker and is checked by the role edit.
     *
     * @throws AccountException with status {@code 400} listing every violation
     */
    public static EditRolesRequest validateRoles(JsonNode json) {
        List<FieldError> errors = unknownFields(json, ROLE_FIELDS);
        List<NewspaperRole> roles = roles(json, errors);
        List<SectionRoleDto> sectionRoles = sectionRoles(json, true, errors);
        Boolean marker = marker(json, errors);
        failOnViolations(roles, sectionRoles, Boolean.TRUE.equals(marker), marker != null, errors);
        return new EditRolesRequest(roles, sectionRoles, marker);
    }

    /**
     * A {@code PUT /api/accounts/{id}/trust} body: a known {@code level}, a {@code sectionId} of an
     * existing section for {@code SECTION_EDITOR} and {@code null} or absent otherwise, a boolean
     * {@code trusted} and no unknown fields.
     *
     * @param sectionIds the ids of all sections
     * @throws AccountException with status {@code 400} listing every violation
     */
    public static SetTrustRequest validateTrust(JsonNode json, List<Long> sectionIds) {
        List<FieldError> errors = unknownFields(json, TRUST_FIELDS);
        ApprovalLevel level = level(json, errors);
        Long sectionId = trustSection(json, level, sectionIds, errors);
        JsonNode trusted = json.get("trusted");
        if (trusted == null || trusted.isNull()) {
            errors.add(new FieldError("trusted", "is required"));
        } else if (!trusted.isBoolean()) {
            errors.add(new FieldError("trusted", "must be a boolean"));
        }
        if (!errors.isEmpty()) {
            throw AccountException.invalid(errors);
        }
        return new SetTrustRequest(new TrustScope(level, sectionId), trusted.asBoolean());
    }

    private static ApprovalLevel level(JsonNode json, List<FieldError> errors) {
        JsonNode node = json.get("level");
        if (node == null || node.isNull()) {
            errors.add(new FieldError("level", "is required"));
            return null;
        }
        ApprovalLevel level = node.isTextual() ? Arrays.stream(ApprovalLevel.values())
                .filter(l -> l.name().equals(node.asText())).findFirst().orElse(null) : null;
        if (level == null) {
            errors.add(new FieldError("level", "unknown level " + node + "; allowed: "
                    + Arrays.toString(ApprovalLevel.values())));
        }
        return level;
    }

    /**
     * The section of a trust body; {@code null} when absent, invalid or not allowed for
     * {@code level}.
     */
    private static Long trustSection(JsonNode json, ApprovalLevel level, List<Long> sectionIds,
            List<FieldError> errors) {
        JsonNode node = json.get("sectionId");
        boolean absent = node == null || node.isNull();
        if (level == ApprovalLevel.SECTION_EDITOR && absent) {
            errors.add(new FieldError("sectionId", "is required for " + level));
            return null;
        }
        if (absent) {
            return null;
        }
        if (!node.isIntegralNumber() || !node.canConvertToLong()) {
            errors.add(new FieldError("sectionId", "must be a number"));
            return null;
        }
        if (level == null) {
            return null;
        }
        if (level != ApprovalLevel.SECTION_EDITOR) {
            errors.add(new FieldError("sectionId", "must be null for " + level));
            return null;
        }
        if (!sectionIds.contains(node.asLong())) {
            errors.add(new FieldError("sectionId", "unknown section " + node.asLong()));
            return null;
        }
        return node.asLong();
    }

    /**
     * An error per field not in {@code allowed}.
     *
     * @throws AccountException {@code 400} when {@code json} is not an object
     */
    private static List<FieldError> unknownFields(JsonNode json, Set<String> allowed) {
        if (json == null || !json.isObject()) {
            throw AccountException.invalid(null, "request body must be a JSON object");
        }
        List<FieldError> errors = new ArrayList<>();
        for (Iterator<String> names = json.fieldNames(); names.hasNext();) {
            String name = names.next();
            if (!allowed.contains(name)) {
                errors.add(new FieldError(name, "unknown field"));
            }
        }
        return errors;
    }

    /**
     * Adds the "at least one role" violation when both role fields are valid but empty and the marker
     * is not set, then throws when there is any violation.
     *
     * @param checkEmpty whether the marker is known, so an empty role set can be judged here
     */
    private static void failOnViolations(List<NewspaperRole> roles, List<SectionRoleDto> sectionRoles, boolean marker,
            boolean checkEmpty, List<FieldError> errors) {
        boolean rolesValid = errors.stream().noneMatch(e -> "roles".equals(e.field())
                || "sectionRoles".equals(e.field()) || SECTIONLESS_REPORTER.equals(e.field()));
        if (checkEmpty && rolesValid && roles.isEmpty() && sectionRoles.isEmpty() && !marker) {
            errors.add(noRole());
        }
        if (!errors.isEmpty()) {
            throw AccountException.invalid(errors);
        }
    }

    static FieldError noRole() {
        return new FieldError("roles",
                "must contain at least one role, or sectionRoles one section role, or sectionlessReporter be true");
    }

    /**
     * The marker; {@code null} when absent.
     */
    private static Boolean marker(JsonNode json, List<FieldError> errors) {
        JsonNode node = json.get(SECTIONLESS_REPORTER);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isBoolean()) {
            errors.add(new FieldError(SECTIONLESS_REPORTER, "must be a boolean"));
            return null;
        }
        return node.booleanValue();
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
     * The section roles; empty when absent and not {@code required}.
     */
    private static List<SectionRoleDto> sectionRoles(JsonNode json, boolean required, List<FieldError> errors) {
        JsonNode node = json.get("sectionRoles");
        if (node == null || node.isNull()) {
            if (required) {
                errors.add(new FieldError("sectionRoles", "is required"));
            }
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
