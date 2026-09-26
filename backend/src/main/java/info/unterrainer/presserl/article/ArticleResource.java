package info.unterrainer.presserl.article;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.RestResponse;

import com.fasterxml.jackson.databind.JsonNode;

import info.unterrainer.presserl.auth.CurrentUser;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * Article endpoints for writers (publishers and editors-in-chief). Which actions a user may
 * perform on an article is decided by {@link ArticlePolicy} and reported as {@code allowedActions}.
 */
@Path("/api/articles")
@RolesAllowed({ "PUBLISHER", "EDITOR_IN_CHIEF" })
@Produces(MediaType.APPLICATION_JSON)
public class ArticleResource {

    @Inject
    JsonWebToken token;

    @Inject
    ArticleService service;

    @GET
    public Uni<List<ArticleSummaryDto>> list(@QueryParam("status") String status, @QueryParam("mine") boolean mine) {
        CurrentUser user = user();
        return service.list(user, status(status), mine)
                .map(views -> views.stream().map(view -> ArticleSummaryDto.of(view, user)).toList());
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<RestResponse<ArticleDto>> create(JsonNode json) {
        CurrentUser user = user();
        ArticleContent content = ArticleContentValidator.validate(json, false).content();
        return service.create(user, content).map(view -> RestResponse.ResponseBuilder
                .<ArticleDto>created(URI.create("/api/articles/" + view.article().id))
                .entity(ArticleDto.of(view, user))
                .build());
    }

    @GET
    @Path("/{id}")
    public Uni<ArticleDto> get(@PathParam("id") long id) {
        CurrentUser user = user();
        return service.get(id).map(view -> ArticleDto.of(view, user));
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<ArticleDto> save(@PathParam("id") long id, JsonNode json) {
        CurrentUser user = user();
        ArticleContentValidator.Request request = ArticleContentValidator.validate(json, true);
        return service.save(user, id, request.content(), request.version()).map(view -> ArticleDto.of(view, user));
    }

    @DELETE
    @Path("/{id}")
    public Uni<Void> delete(@PathParam("id") long id) {
        return service.delete(user(), id);
    }

    @POST
    @Path("/{id}/publish")
    public Uni<ArticleDto> publish(@PathParam("id") long id) {
        CurrentUser user = user();
        return service.publish(user, id).map(view -> ArticleDto.of(view, user));
    }

    @POST
    @Path("/{id}/offline")
    public Uni<ArticleDto> takeOffline(@PathParam("id") long id) {
        CurrentUser user = user();
        return service.takeOffline(user, id).map(view -> ArticleDto.of(view, user));
    }

    @GET
    @Path("/{id}/revisions")
    public Uni<List<RevisionSummaryDto>> revisions(@PathParam("id") long id) {
        return service.revisions(id).map(result -> result.revisions().stream()
                .map(revision -> RevisionSummaryDto.of(result.article(), revision))
                .toList());
    }

    @GET
    @Path("/{id}/revisions/{number}")
    public Uni<RevisionDto> revision(@PathParam("id") long id, @PathParam("number") int number) {
        return service.revision(id, number).map(RevisionDto::of);
    }

    private CurrentUser user() {
        return CurrentUser.of(token);
    }

    private static ArticleStatus status(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return Arrays.stream(ArticleStatus.values()).filter(s -> s.name().equals(value)).findFirst()
                .orElseThrow(() -> ArticleException.invalid("status", "unknown status '" + value + "'; allowed: "
                        + Arrays.toString(ArticleStatus.values())));
    }
}
