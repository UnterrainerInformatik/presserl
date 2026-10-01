package info.unterrainer.presserl.account;

import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.logging.Logger;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Deletes an account in one database transaction: first {@link AccountDataEraser#erase} (withdraw,
 * anonymise, clean up), then the Keycloak user. A Keycloak failure rolls the database back, so either
 * the account and its names stay or the account is gone and its names are anonymised. A commit that
 * fails after the Keycloak user was deleted is logged at ERROR with the sub; the operator then runs
 * the recovery SQL of {@code deploy/INSTALL.md} for it.
 */
@ApplicationScoped
public class AccountDeletion {

    private static final Logger LOG = Logger.getLogger(AccountDeletion.class);

    @Inject
    AccountService accounts;

    @Inject
    KeycloakCalls keycloakCalls;

    @Inject
    AccountDataEraser eraser;

    /**
     * Deletes {@code account}; the caller has checked {@link AccountPolicy}.
     *
     * @throws AccountException {@code 503} when Keycloak is unavailable (nothing changed)
     */
    public Uni<Void> delete(AccountDto account) {
        AtomicBoolean keycloakDeleted = new AtomicBoolean();
        return Panache.withTransaction(() -> eraser.erase(account.id())
                .call(() -> keycloakCalls.run(() -> accounts.delete(account.id())))
                .invoke(() -> keycloakDeleted.set(true)))
                .onFailure().invoke(e -> {
                    if (keycloakDeleted.get()) {
                        LOG.errorf(e, "Account '%s' (%s) was deleted in Keycloak, but anonymising its data failed; "
                                + "run the recovery SQL of deploy/INSTALL.md for sub %s", account.username(), account.id(),
                                account.id());
                    }
                });
    }
}
