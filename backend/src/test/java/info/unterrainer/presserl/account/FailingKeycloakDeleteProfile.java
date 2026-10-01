package info.unterrainer.presserl.account;

import java.util.Set;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Keycloak refuses to delete users ({@link FailingKeycloakDelete}); everything else works.
 */
public class FailingKeycloakDeleteProfile implements QuarkusTestProfile {

    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        return Set.of(FailingKeycloakDelete.class);
    }
}
