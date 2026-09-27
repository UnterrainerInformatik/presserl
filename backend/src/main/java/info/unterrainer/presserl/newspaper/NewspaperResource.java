package info.unterrainer.presserl.newspaper;

import org.eclipse.microprofile.jwt.JsonWebToken;

import com.fasterxml.jackson.databind.JsonNode;

import info.unterrainer.presserl.auth.CurrentUser;
import info.unterrainer.presserl.section.NewsroomService;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * The effective newspaper settings, public; publishers and editors-in-chief override writable settings
 * ({@link WritableSettings}). Refused access is answered with {@code 403} and an empty body.
 */
@Path("/api/newspaper")
public class NewspaperResource {

    @Inject
    NewspaperSettings settings;

    @Inject
    JsonWebToken token;

    @Inject
    NewsroomService newsrooms;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<NewspaperDto> get() {
        return settings.effective().map(NewspaperDto::of);
    }

    @PUT
    @Path("/settings")
    @Authenticated
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<NewspaperDto> update(JsonNode json) {
        return newsrooms.of(CurrentUser.of(token)).flatMap(newsroom -> {
            if (!newsroom.mayConfigureNewspaper()) {
                throw new ForbiddenException();
            }
            return settings.update(WritableSettings.changes(json));
        }).map(NewspaperDto::of);
    }
}
