package info.unterrainer.presserl.spellcheck;

import org.eclipse.microprofile.jwt.JsonWebToken;

import com.fasterxml.jackson.databind.JsonNode;

import info.unterrainer.presserl.auth.CurrentUser;
import info.unterrainer.presserl.section.Newsroom;
import info.unterrainer.presserl.section.NewsroomService;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Spell check for writers ({@link Newsroom#isWriter()}, {@code WRITE_ARTICLES}); everyone else gets
 * {@code 403} with an empty body. The text goes to LanguageTool inside the installation.
 */
@Path("/api/spell-check")
@Authenticated
public class SpellCheckResource {

    @Inject
    JsonWebToken token;

    @Inject
    NewsroomService newsrooms;

    @Inject
    SpellCheckService service;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<SpellCheckResponseDto> check(JsonNode json) {
        return newsrooms.of(CurrentUser.of(token))
                .invoke(newsroom -> {
                    if (!newsroom.isWriter()) {
                        throw new ForbiddenException();
                    }
                })
                .flatMap(newsroom -> service.check(SpellCheckRequestValidator.text(json)))
                .map(SpellCheckResponseDto::new);
    }
}
