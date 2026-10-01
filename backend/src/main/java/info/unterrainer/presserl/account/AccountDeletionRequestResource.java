package info.unterrainer.presserl.account;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import info.unterrainer.presserl.auth.CurrentUser;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * The requesting user's own deletion request, for every authenticated user whatever their roles;
 * service accounts get {@code 403} (empty body). A request deletes nothing: a publisher deletes the
 * account ({@code DELETE /api/accounts/{id}}). Both calls are idempotent.
 */
@Path("/api/me/deletion-request")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class AccountDeletionRequestResource {

    private static final Logger LOG = Logger.getLogger(AccountDeletionRequestResource.class);

    @Inject
    JsonWebToken token;

    @Inject
    AccountDeletionRequestStore requests;

    /**
     * Requests the deletion; a repeated request keeps the time of the first one.
     */
    @POST
    public Uni<DeletionRequestDto> request() {
        CurrentUser user = user();
        return requests.request(user.sub()).map(requestedAt -> {
            LOG.infof("Account '%s' requested its deletion", user.username());
            return new DeletionRequestDto(requestedAt);
        });
    }

    /**
     * Withdraws a pending request.
     */
    @DELETE
    public Uni<DeletionRequestDto> withdraw() {
        CurrentUser user = user();
        return requests.withdraw(user.sub()).map(deleted -> {
            LOG.infof("Account '%s' withdrew its deletion request", user.username());
            return new DeletionRequestDto(null);
        });
    }

    private CurrentUser user() {
        return requireNoServiceAccount(CurrentUser.of(token));
    }

    /**
     * Service-account tokens of the realm lack the backend's audience and are refused before; this
     * check keeps them out should one ever carry it.
     *
     * @throws ForbiddenException for a service account
     */
    static CurrentUser requireNoServiceAccount(CurrentUser user) {
        if (user.username().startsWith(AccountRequestValidator.SERVICE_ACCOUNT_PREFIX)) {
            throw new ForbiddenException();
        }
        return user;
    }
}
