package info.unterrainer.presserl.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import info.unterrainer.presserl.TestSupport;
import info.unterrainer.presserl.bootstrap.KeycloakAdminProducer.KeycloakRealm;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

/**
 * Runs bootstrap attempts against the Dev Services Keycloak. Each test restores the realm to
 * "user publisher is the only member of group publisher".
 */
@QuarkusTest
class PublisherBootstrapTest {

    @Inject
    PublisherBootstrap bootstrap;

    @Inject
    Keycloak keycloak;

    @Inject
    KeycloakRealm keycloakRealm;

    private RealmResource realm;

    @BeforeEach
    void setUp() {
        TestSupport.awaitReady();
        realm = keycloak.realm(keycloakRealm.name());
    }

    @Test
    void bootstrapOnStartupCreatedThePublisher() {
        assertThat(members()).extracting(UserRepresentation::getUsername).containsExactly("publisher");
        TestSupport.token("publisher", "publisher");
    }

    @Test
    void secondRunChangesNothing() {
        assertThat(bootstrap.attempt(new PublisherCredentials("publisher", "changed")))
                .isEqualTo(PublisherBootstrap.Outcome.ALREADY_PRESENT);

        TestSupport.token("publisher", "publisher");
        TestSupport.passwordGrant(TestSupport.HTTP_CLIENT, "publisher", "changed").then().statusCode(401);
    }

    @Test
    void freshRealmCreatesEnabledUserInPublisherGroup() {
        withEmptyPublisherGroup(() -> {
            assertThat(bootstrap.attempt(new PublisherCredentials("papa", "papa-secret")))
                    .isEqualTo(PublisherBootstrap.Outcome.CREATED);

            UserRepresentation papa = user("papa");
            assertThat(papa.isEnabled()).isTrue();
            assertThat(members()).extracting(UserRepresentation::getUsername).containsExactly("papa");
            TestSupport.token("papa", "papa-secret");
        }, "papa");
    }

    @Test
    void existingUserJoinsGroupAndKeepsPassword() {
        withEmptyPublisherGroup(() -> {
            createUser("mama", "original");

            assertThat(bootstrap.attempt(new PublisherCredentials("mama", "from-env")))
                    .isEqualTo(PublisherBootstrap.Outcome.JOINED);

            assertThat(members()).extracting(UserRepresentation::getUsername).containsExactly("mama");
            TestSupport.token("mama", "original");
            TestSupport.passwordGrant(TestSupport.HTTP_CLIENT, "mama", "from-env").then().statusCode(401);
        }, "mama");
    }

    @Test
    void missingPublisherGroupFailsNamingTheGroup() {
        GroupRepresentation group = publisherGroup();
        group.setName("publisher-renamed");
        realm.groups().group(group.getId()).update(group);
        try {
            assertThatThrownBy(() -> bootstrap.attempt(new PublisherCredentials("papa", "papa-secret")))
                    .isInstanceOf(BootstrapException.class)
                    .hasMessageContaining("no group 'publisher'");
        } finally {
            group.setName("publisher");
            realm.groups().group(group.getId()).update(group);
        }
    }

    private void withEmptyPublisherGroup(Runnable test, String createdUser) {
        String groupId = publisherGroup().getId();
        String publisherId = user("publisher").getId();
        realm.users().get(publisherId).leaveGroup(groupId);
        try {
            test.run();
        } finally {
            realm.users().searchByUsername(createdUser, true)
                    .forEach(u -> realm.users().delete(u.getId()).close());
            realm.users().get(publisherId).joinGroup(groupId);
        }
    }

    private GroupRepresentation publisherGroup() {
        return realm.groups().groups("publisher", true, 0, 10, true).stream()
                .filter(g -> g.getName().equals("publisher")).findFirst().orElseThrow();
    }

    private List<UserRepresentation> members() {
        return realm.groups().group(publisherGroup().getId()).members();
    }

    private UserRepresentation user(String username) {
        return realm.users().searchByUsername(username, true).getFirst();
    }

    private void createUser(String username, String password) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEnabled(true);
        user.setCredentials(List.of(credential));
        try (Response response = realm.users().create(user)) {
            assertThat(response.getStatus()).isEqualTo(201);
        }
    }
}
