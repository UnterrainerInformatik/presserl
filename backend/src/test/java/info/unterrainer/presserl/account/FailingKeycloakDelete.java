package info.unterrainer.presserl.account;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

/**
 * An {@link AccountService} whose user deletion fails as if Keycloak were unreachable; enabled by
 * {@link FailingKeycloakDeleteProfile} only.
 */
@Alternative
@ApplicationScoped
public class FailingKeycloakDelete extends AccountService {

    @Override
    public void delete(String id) {
        throw AccountException.unavailable(new IllegalStateException("Keycloak is down (test)"));
    }
}
