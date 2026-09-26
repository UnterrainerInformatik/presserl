package info.unterrainer.presserl.auth;

import java.util.Set;

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
        String username = token.getClaim("preferred_username");
        if (username == null) {
            username = token.getSubject();
        }
        String name = token.getClaim("name");
        Set<String> groups = token.getGroups();
        return new MeDto(username, name == null || name.isBlank() ? username : name,
                NewspaperRole.fromGroups(groups == null ? Set.of() : groups));
    }
}
