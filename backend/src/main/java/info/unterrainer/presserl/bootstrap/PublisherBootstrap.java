package info.unterrainer.presserl.bootstrap;

import java.util.List;

import org.jboss.logging.Logger;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import info.unterrainer.presserl.auth.NewspaperRole;
import info.unterrainer.presserl.bootstrap.KeycloakAdminProducer.KeycloakRealm;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

/**
 * Makes sure the Keycloak group {@code publisher} has a member. One call is one attempt; the
 * retries live in {@link PublisherBootstrapRunner}. Blocking — call it on a worker thread.
 */
@ApplicationScoped
public class PublisherBootstrap {

    public enum Outcome {
        /** The group already had a member; nothing was changed. */
        ALREADY_PRESENT,
        /** The user did not exist and was created in the group. */
        CREATED,
        /** The user existed and was added to the group; its password was kept. */
        JOINED
    }

    private static final Logger LOG = Logger.getLogger(PublisherBootstrap.class);
    private static final String GROUP = NewspaperRole.PUBLISHER.group();

    @Inject
    Keycloak keycloak;

    @Inject
    KeycloakRealm realm;

    public Outcome attempt(PublisherCredentials credentials) {
        RealmResource realmResource = keycloak.realm(realm.name());
        GroupRepresentation group = realmResource.groups().groups(GROUP, true, 0, 10, true).stream()
                .filter(g -> GROUP.equals(g.getName()))
                .findFirst()
                .orElseThrow(() -> new BootstrapException("Keycloak realm '%s' has no group '%s' - import the realm template (deploy/keycloak/presserl-realm.json)"
                        .formatted(realm.name(), GROUP)));
        if (!realmResource.groups().group(group.getId()).members(0, 1, true).isEmpty()) {
            return Outcome.ALREADY_PRESENT;
        }

        List<UserRepresentation> existing = realmResource.users().searchByUsername(credentials.username(), true);
        Outcome outcome;
        String userId;
        if (existing.isEmpty()) {
            userId = create(realmResource, credentials);
            outcome = Outcome.CREATED;
        } else {
            userId = existing.getFirst().getId();
            outcome = Outcome.JOINED;
        }
        UserResource user = realmResource.users().get(userId);
        user.joinGroup(group.getId());
        LOG.infof("Publisher bootstrap: user '%s' %s group '%s'", credentials.username(),
                outcome == Outcome.CREATED ? "created in" : "added to", GROUP);
        return outcome;
    }

    private String create(RealmResource realmResource, PublisherCredentials credentials) {
        CredentialRepresentation password = new CredentialRepresentation();
        password.setType(CredentialRepresentation.PASSWORD);
        password.setValue(credentials.password());
        password.setTemporary(false);

        UserRepresentation user = new UserRepresentation();
        user.setUsername(credentials.username());
        user.setEnabled(true);
        user.setCredentials(List.of(password));

        try (Response response = realmResource.users().create(user)) {
            if (response.getStatus() != Response.Status.CREATED.getStatusCode()) {
                throw new BootstrapException("Creating user '%s' failed with HTTP %d: %s".formatted(
                        credentials.username(), response.getStatus(), response.readEntity(String.class)));
            }
            String location = response.getLocation().getPath();
            return location.substring(location.lastIndexOf('/') + 1);
        }
    }
}
