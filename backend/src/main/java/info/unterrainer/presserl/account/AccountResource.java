package info.unterrainer.presserl.account;

import java.net.URI;
import java.util.List;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestResponse;

import com.fasterxml.jackson.databind.JsonNode;

import info.unterrainer.presserl.auth.CurrentUser;
import info.unterrainer.presserl.section.Newsroom;
import info.unterrainer.presserl.section.NewsroomService;
import info.unterrainer.presserl.section.SectionRoleDto;
import info.unterrainer.presserl.section.SectionRoleStore;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * Account endpoints for publishers, editors-in-chief and section editors (of any section); others
 * get {@code 403}. Which newspaper roles a user may assign is decided by {@link RoleDelegation} and
 * reported as {@code assignableRoles}, which actions a user may perform on an account by
 * {@link AccountPolicy} ({@code allowedActions}). The Keycloak Admin client blocks, so Keycloak calls run
 * through {@link KeycloakCalls}.
 */
@Path("/api/accounts")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class AccountResource {

    private static final Logger LOG = Logger.getLogger(AccountResource.class);

    @Inject
    JsonWebToken token;

    @Inject
    AccountService service;

    @Inject
    AccountCreation creation;

    @Inject
    AccountRoleEdit roleEdit;

    @Inject
    KeycloakCalls keycloakCalls;

    @Inject
    NewsroomService newsrooms;

    @Inject
    SectionRoleStore sectionRoles;

    @Inject
    PassPhraseGenerator passPhrases;

    @GET
    public Uni<AccountListDto> list() {
        return newsroom().flatMap(newsroom -> keycloakCalls.call(service::list)
                .flatMap(accounts -> sectionRoles.byAccount().map(roles -> new AccountListDto(
                        RoleDelegation.assignableBy(newsroom.user()),
                        accounts.stream()
                                .map(account -> account.withSectionRoles(roles.getOrDefault(account.id(), List.of()))
                                        .withAllowedActionsFor(newsroom))
                                .toList()))));
    }

    @GET
    @Path("/username-suggestion")
    public Uni<UsernameSuggestionDto> usernameSuggestion(@QueryParam("firstName") String firstName) {
        return newsroom().flatMap(newsroom -> {
            if (firstName == null || firstName.isBlank()) {
                throw AccountException.invalid("firstName", "is required");
            }
            return keycloakCalls.call(() -> new UsernameSuggestionDto(service.suggestUsername(firstName)));
        });
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<RestResponse<CreatedAccountDto>> create(JsonNode json) {
        return newsroom().flatMap(newsroom -> creation.create(newsroom, AccountRequestValidator.validate(json))
                .map(created -> new CreatedAccountDto(created.account().withAllowedActionsFor(newsroom),
                        created.password())))
                .map(created -> RestResponse.ResponseBuilder
                        .<CreatedAccountDto>created(URI.create("/api/accounts/" + created.account().id()))
                        .entity(created)
                        .build());
    }

    /**
     * Replaces the account's newspaper and section roles; no session is ended, so removed newspaper
     * roles take effect with the person's next token refresh.
     */
    @PUT
    @Path("/{id}/roles")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<AccountDto> editRoles(@PathParam("id") String id, JsonNode json) {
        return target(id, AccountAction.EDIT_ROLES, "edit the roles of").flatMap(target -> roleEdit
                .edit(target.requester(), target.account(), AccountRequestValidator.validateRoles(json))
                .map(edited -> edited.withAllowedActionsFor(target.requester())));
    }

    /**
     * Sets a new generated password and ends the account's sessions; a locked account stays locked.
     */
    @POST
    @Path("/{id}/password-reset")
    public Uni<CreatedAccountDto> resetPassword(@PathParam("id") String id) {
        return target(id, AccountAction.RESET_PASSWORD, "reset the password of").flatMap(target -> {
            String password = passPhrases.generate();
            return keycloakCalls.run(() -> {
                service.resetPassword(id, password);
                service.logout(id);
            }).map(done -> {
                LOG.infof("Password of account '%s' reset by '%s'", target.account().username(),
                        target.requester().user().username());
                return new CreatedAccountDto(target.account().withAllowedActionsFor(target.requester()), password);
            });
        });
    }

    /**
     * Disables the account and ends its sessions; idempotent.
     */
    @POST
    @Path("/{id}/lock")
    public Uni<AccountDto> lock(@PathParam("id") String id) {
        return setEnabled(id, false);
    }

    /**
     * Enables the account again; idempotent.
     */
    @POST
    @Path("/{id}/unlock")
    public Uni<AccountDto> unlock(@PathParam("id") String id) {
        return setEnabled(id, true);
    }

    private Uni<AccountDto> setEnabled(String id, boolean enabled) {
        AccountAction action = enabled ? AccountAction.UNLOCK : AccountAction.LOCK;
        return target(id, action, enabled ? "unlock" : "lock").flatMap(target -> keycloakCalls.call(() -> {
            boolean changed = service.setEnabled(id, enabled);
            if (changed && !enabled) {
                service.logout(id);
            }
            return changed;
        }).map(changed -> {
            if (changed) {
                LOG.infof("Account '%s' %s by '%s'", target.account().username(), enabled ? "unlocked" : "locked",
                        target.requester().user().username());
            }
            return target.account().withEnabled(enabled).withAllowedActionsFor(target.requester());
        }));
    }

    /**
     * The requesting user and the account {@code id} (with newspaper and section roles), once the
     * access rule and {@link AccountPolicy#permitted} allow {@code action}.
     *
     * @throws AccountException {@code 404} for an unknown id or a service account, {@code 403} when
     *                          the policy refuses
     */
    private Uni<Target> target(String id, AccountAction action, String verb) {
        return newsroom().flatMap(newsroom -> keycloakCalls.call(() -> service.find(id))
                .flatMap(found -> {
                    AccountDto account = found.orElseThrow(() -> AccountException.notFound(id));
                    return sectionRoles.namedRolesOf(id).map(roles -> account.withSectionRoles(roles.stream()
                            .map(role -> new SectionRoleDto(role.sectionId(), role.role())).toList()));
                })
                .map(account -> {
                    if (!AccountPolicy.permitted(action, newsroom, account)) {
                        throw AccountException.forbidden(null, "you may not " + verb + " account '"
                                + account.username() + "'");
                    }
                    return new Target(newsroom, account);
                }));
    }

    private record Target(Newsroom requester, AccountDto account) {
    }

    /**
     * The requesting user's newsroom; fails with {@code 403} (empty body) unless they may administer
     * accounts.
     */
    private Uni<Newsroom> newsroom() {
        return newsrooms.of(CurrentUser.of(token)).invoke(newsroom -> {
            if (!newsroom.mayAdministerAccounts()) {
                throw new ForbiddenException();
            }
        });
    }
}
