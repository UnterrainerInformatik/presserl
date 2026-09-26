package info.unterrainer.presserl.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/client-config")
public class ClientConfigResource {

    @Inject
    OidcConfig config;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public ClientConfigDto get() {
        return new ClientConfigDto(new ClientConfigDto.Oidc(config.issuer(), config.adminClientId(), config.scopes()));
    }
}
