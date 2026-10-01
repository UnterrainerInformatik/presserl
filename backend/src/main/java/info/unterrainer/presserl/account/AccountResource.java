package info.unterrainer.presserl.account;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestResponse;

import com.fasterxml.jackson.databind.JsonNode;

import info.unterrainer.presserl.auth.CurrentUser;
import info.unterrainer.presserl.auth.NewspaperRole;
import info.unterrainer.presserl.section.Newsroom;
import info.unterrainer.presserl.section.NewsroomService;
import info.unterrainer.presserl.section.SectionRoleDto;
import info.unterrainer.presserl.section.SectionRoleStore;
import info.unterrainer.presserl.section.SectionlessReporterStore;
import info.unterrainer.presserl.trust.TrustPolicy;
import info.unterrainer.presserl.trust.TrustScope;
import info.unterrainer.presserl.trust.TrustStore;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
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
 * {@link AccountPolicy} ({@code allowedActions}), which trust entries they may set or clear by
 * {@link TrustPolicy} ({@code trustScopes}). The Keycloak Admin client blocks, so Keycloak calls run
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
    TrustStore trustStore;

    @Inject
    SectionlessReporterStore markers;

    @Inject
    PassPhraseGenerator passPhrases;

    @Inject
    AccountDeletionRequestStore deletionRequests;

    @Inject
    AccountDeletion deletion;

    @GET
    public Uni<AccountListDto> list() {
        return newsroom().flatMap(newsroom -> keycloakCalls.call(service::list)
                .flatMap(accounts -> sectionRoles.byAccount().flatMap(roles -> trustStore.byAccount()
                        .flatMap(trusts -> markers.marked().flatMap(marked -> deletionRequests.byAccount()
                                .flatMap(requests -> sectionRoles.sectionIds().map(sectionIds -> {
                                    Set<String> enabledPublishers = accounts.stream()
                                            .filter(account -> account.enabled()
                                                    && account.roles().contains(NewspaperRole.PUBLISHER))
                                            .map(AccountDto::id)
                                            .collect(Collectors.toUnmodifiableSet());
                                    return new AccountListDto(
                                            RoleDelegation.assignableBy(newsroom.user()), newsroom.isAdministrator(),
                                            accounts.stream()
                                                    .map(account -> account
                                                            .withSectionRoles(roles.getOrDefault(account.id(), List.of()))
                                                            .withSectionlessReporter(marked.contains(account.id()))
                                                            .withDeletionRequestedAt(requests.get(account.id()))
                                                            .withTrusts(trusts.getOrDefault(account.id(), List.of()))
                                                            .withAllowedActionsFor(newsroom, sectionIds, enabledPublishers))
                                                    .toList());
                                })))))));
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
                // a new account has no deletion request, so the enabled publishers do not matter
                .flatMap(created -> sectionRoles.sectionIds().map(sectionIds -> new CreatedAccountDto(
                        created.account().withAllowedActionsFor(newsroom, sectionIds, Set.of()), created.password()))))
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
                .map(edited -> target.answer(edited)));
    }

    /**
     * Sets ({@code trusted: true}) or clears ({@code trusted: false}) a trust entry of the account;
     * idempotent, an existing entry keeps its setter and time. Articles are not touched: pending
     * submissions keep waiting, trust applies the next time a chain is computed.
     */
    @PUT
    @Path("/{id}/trust")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<AccountDto> setTrust(@PathParam("id") String id, JsonNode json) {
        return newsroom().flatMap(newsroom -> load(newsroom, id)).flatMap(target -> {
            SetTrustRequest request = AccountRequestValidator.validateTrust(json, target.sectionIds());
            TrustScope scope = request.scope();
            AccountDto account = target.account();
            Newsroom requester = target.requester();
            if (!TrustPolicy.scopes(requester, account, target.sectionIds()).contains(scope)) {
                throw AccountException.forbidden(null, "you may not change trust of account '" + account.username()
                        + "' at " + scope.level() + section(scope));
            }
            Uni<Boolean> write = request.trusted()
                    ? trustStore.set(id, scope, requester.user().sub())
                    : trustStore.clear(id, scope);
            return write.flatMap(changed -> {
                if (changed) {
                    LOG.infof("Trust of account '%s' at %s%s %s by '%s'", account.username(), scope.level(),
                            section(scope), request.trusted() ? "set" : "cleared", requester.user().username());
                }
                return trustStore.scopesOf(id);
            }).map(trusts -> target.answer(account.withTrusts(
                    trusts.stream().sorted(TrustScope.order(target.sectionIds())).toList())));
        });
    }

    private static String section(TrustScope scope) {
        return scope.sectionId() == null ? "" : " in section " + scope.sectionId();
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
                return new CreatedAccountDto(target.answer(target.account()), password);
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
            return target.answer(target.account().withEnabled(enabled));
        }));
    }

    /**
     * Deletes the account: its content stays with anonymised names, its section roles, marker, trust
     * entries and deletion request go, pending submissions of its articles are withdrawn, and the
     * Keycloak user is removed (which ends its sessions). See {@link AccountDeletion}.
     */
    @DELETE
    @Path("/{id}")
    public Uni<RestResponse<Void>> delete(@PathParam("id") String id) {
        return target(id, AccountAction.DELETE, "delete").flatMap(target -> deletion.delete(target.account())
                .map(done -> {
                    LOG.infof("Account '%s' deleted by '%s'", target.account().username(),
                            target.requester().user().username());
                    return RestResponse.<Void>noContent();
                }));
    }

    /**
     * The requesting user and the account {@code id} (see {@link #load}), once the access rule and
     * {@link AccountPolicy#permitted} allow {@code action}.
     *
     * @throws AccountException {@code 404} for an unknown id or a service account, {@code 403} when
     *                          the policy refuses
     */
    private Uni<Target> target(String id, AccountAction action, String verb) {
        return newsroom().flatMap(newsroom -> load(newsroom, id)).invoke(target -> {
            if (!AccountPolicy.permitted(action, target.requester(), target.account(), target.enabledPublishers())) {
                throw AccountException.forbidden(null, "you may not " + verb + " account '"
                        + target.account().username() + "'");
            }
        });
    }

    /**
     * The account {@code id} with newspaper roles, section roles, marker, deletion request and trust
     * entries, the ids of all sections by position and the ids of the enabled publishers.
     *
     * @throws AccountException {@code 404} for an unknown id or a service account
     */
    private Uni<Target> load(Newsroom newsroom, String id) {
        return keycloakCalls.call(() -> service.find(id))
                .flatMap(found -> {
                    AccountDto account = found.orElseThrow(() -> AccountException.notFound(id));
                    return sectionRoles.namedRolesOf(id).map(roles -> account.withSectionRoles(roles.stream()
                            .map(role -> new SectionRoleDto(role.sectionId(), role.role())).toList()));
                })
                .flatMap(account -> markers.isMarked(id).map(account::withSectionlessReporter))
                .flatMap(account -> deletionRequests.requestedAt(id).map(account::withDeletionRequestedAt))
                .flatMap(account -> keycloakCalls.call(service::enabledPublisherIds).flatMap(enabledPublishers -> sectionRoles
                        .sectionIds().flatMap(sectionIds -> trustStore.scopesOf(id)
                                .map(trusts -> new Target(newsroom, account.withTrusts(
                                        trusts.stream().sorted(TrustScope.order(sectionIds)).toList()), sectionIds,
                                        enabledPublishers)))));
    }

    /**
     * @param sectionIds        the ids of all sections by position
     * @param enabledPublishers the ids of the enabled accounts holding {@code PUBLISHER}
     */
    private record Target(Newsroom requester, AccountDto account, List<Long> sectionIds, Set<String> enabledPublishers) {

        /**
         * {@code account} (this target after a change) as answered to the requester.
         */
        AccountDto answer(AccountDto account) {
            return account.withAllowedActionsFor(requester, sectionIds, enabledPublishers);
        }
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
