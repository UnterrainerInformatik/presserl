package info.unterrainer.presserl.auth;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Newspaper-wide roles; each is a Keycloak group of the same (lower-case) name.
 */
public enum NewspaperRole {
    PUBLISHER("publisher"),
    EDITOR_IN_CHIEF("editor-in-chief"),
    READER("reader");

    private final String group;

    NewspaperRole(String group) {
        this.group = group;
    }

    public String group() {
        return group;
    }

    /**
     * Maps group names to roles in declaration order; groups outside the newspaper roles are ignored.
     */
    public static List<NewspaperRole> fromGroups(Collection<String> groups) {
        return Arrays.stream(values()).filter(role -> groups.contains(role.group)).toList();
    }
}
