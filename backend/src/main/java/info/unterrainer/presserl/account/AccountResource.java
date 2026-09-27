package info.unterrainer.presserl.account;

import java.net.URI;
import java.util.List;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.RestResponse;

import com.fasterxml.jackson.databind.JsonNode;

import info.unterrainer.presserl.auth.CurrentUser;
import info.unterrainer.presserl.section.Newsroom;
import info.unterrainer.presserl.section.NewsroomService;
import info.unterrainer.presserl.section.SectionRoleStore;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * Account endpoints for publishers, editors-in-chief and section editors (of any section); others
 * get {@code 403}. Which newspaper roles a user may assign is decided by {@link RoleDelegation} and
 * reported as {@code assignableRoles}. The Keycloak Admin client blocks, so Keycloak calls run
 * through {@link KeycloakCalls}.
 */
@Path("/api/accounts")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class AccountResource {

    @Inject
    JsonWebToken token;

    @Inject
    AccountService service;

    @Inject
    AccountCreation creation;

    @Inject
    KeycloakCalls keycloakCalls;

    @Inject
    NewsroomService newsrooms;

    @Inject
    SectionRoleStore sectionRoles;

    @GET
    public Uni<AccountListDto> list() {
        return newsroom().flatMap(newsroom -> keycloakCalls.call(service::list)
                .flatMap(accounts -> sectionRoles.byAccount().map(roles -> new AccountListDto(
                        RoleDelegation.assignableBy(newsroom.user()),
                        accounts.stream()
                                .map(account -> account.withSectionRoles(roles.getOrDefault(account.id(), List.of())))
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
        return newsroom().flatMap(newsroom -> creation.create(newsroom, AccountRequestValidator.validate(json)))
                .map(created -> RestResponse.ResponseBuilder
                        .<CreatedAccountDto>created(URI.create("/api/accounts/" + created.account().id()))
                        .entity(created)
                        .build());
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
