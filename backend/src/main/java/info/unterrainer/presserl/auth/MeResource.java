package info.unterrainer.presserl.auth;

import org.eclipse.microprofile.jwt.JsonWebToken;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/me")
@Authenticated
public class MeResource {

    @Inject
    JsonWebToken token;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public MeDto get() {
        CurrentUser user = CurrentUser.of(token);
        return new MeDto(user.username(), user.displayName(), user.roles());
    }
}
