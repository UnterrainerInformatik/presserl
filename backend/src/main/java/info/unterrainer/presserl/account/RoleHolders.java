package info.unterrainer.presserl.account;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import info.unterrainer.presserl.auth.NewspaperRole;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Who holds the newspaper-wide roles that are approval levels: the account ids of the
 * {@code editor-in-chief} and {@code publisher} groups, read from Keycloak on a worker thread on
 * every call (role changes act at once). Locked accounts are included.
 */
@ApplicationScoped
public class RoleHolders {

    @Inject
    AccountService accounts;

    @Inject
    KeycloakCalls keycloakCalls;

    /**
     * @return the holders of {@link NewspaperRole#EDITOR_IN_CHIEF} and {@link NewspaperRole#PUBLISHER}
     * @throws AccountException {@code 503} when Keycloak is unavailable
     */
    public Uni<Map<NewspaperRole, Set<String>>> approvers() {
        return keycloakCalls.call(() -> accounts.memberIds(
                EnumSet.of(NewspaperRole.EDITOR_IN_CHIEF, NewspaperRole.PUBLISHER)));
    }
}
