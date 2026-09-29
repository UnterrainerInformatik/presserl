package info.unterrainer.presserl.spellcheck;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * LanguageTool's HTTP API; base URL and the 5 s timeouts come from {@code quarkus.rest-client.languagetool.*}.
 */
@RegisterRestClient(configKey = "languagetool")
@Path("/v2")
public interface LanguageToolClient {

    @POST
    @Path("/check")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<LanguageToolResponse> check(@FormParam("text") String text, @FormParam("language") String language,
            @FormParam("level") String level);
}
