package info.unterrainer.presserl.account;

import java.net.URI;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.RestResponse;

import com.fasterxml.jackson.databind.JsonNode;

import info.unterrainer.presserl.auth.CurrentUser;
import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * Account endpoints for publishers and editors-in-chief. Which roles a user may assign is decided
 * by {@link RoleDelegation} and reported as {@code assignableRoles}. The Keycloak Admin client
 * blocks, so every method runs on a worker thread.
 */
@Path("/api/accounts")
@RolesAllowed({ "PUBLISHER", "EDITOR_IN_CHIEF" })
@Produces(MediaType.APPLICATION_JSON)
@Blocking
public class AccountResource {

    @Inject
    JsonWebToken token;

    @Inject
    AccountService service;

    @GET
    public AccountListDto list() {
        return new AccountListDto(RoleDelegation.assignableBy(user()), service.list());
    }

    @GET
    @Path("/username-suggestion")
    public UsernameSuggestionDto usernameSuggestion(@QueryParam("firstName") String firstName) {
        if (firstName == null || firstName.isBlank()) {
            throw AccountException.invalid("firstName", "is required");
        }
        return new UsernameSuggestionDto(service.suggestUsername(firstName));
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public RestResponse<CreatedAccountDto> create(JsonNode json) {
        CreateAccountRequest request = AccountRequestValidator.validate(json);
        CreatedAccountDto created = service.create(user(), request);
        return RestResponse.ResponseBuilder
                .<CreatedAccountDto>created(URI.create("/api/accounts/" + created.account().id()))
                .entity(created)
                .build();
    }

    private CurrentUser user() {
        return CurrentUser.of(token);
    }
}
