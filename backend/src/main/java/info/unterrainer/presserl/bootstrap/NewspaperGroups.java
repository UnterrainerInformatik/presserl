package info.unterrainer.presserl.bootstrap;

import java.util.Optional;

import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.GroupRepresentation;

import info.unterrainer.presserl.auth.NewspaperRole;

/**
 * Looks up the Keycloak group of a newspaper role by its exact name. Blocking.
 */
public final class NewspaperGroups {

    private NewspaperGroups() {
    }

    /**
     * The id of the group of {@code role}, or empty when the realm has no such group.
     */
    public static Optional<String> id(RealmResource realm, NewspaperRole role) {
        String name = role.group();
        return realm.groups().groups(name, true, 0, 10, true).stream()
                .filter(g -> name.equals(g.getName()))
                .map(GroupRepresentation::getId)
                .findFirst();
    }
}
