package info.unterrainer.presserl.auth;

import info.unterrainer.presserl.spellcheck.SpellCheckConfig;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/client-config")
public class ClientConfigResource {

    @Inject
    OidcConfig config;

    @Inject
    SpellCheckConfig spellCheck;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public ClientConfigDto get() {
        return new ClientConfigDto(new ClientConfigDto.Oidc(config.issuer(), config.adminClientId(), config.scopes()),
                spellCheck.enabled());
    }
}
