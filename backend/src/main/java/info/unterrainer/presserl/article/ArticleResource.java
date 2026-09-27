package info.unterrainer.presserl.article;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.RestResponse;

import com.fasterxml.jackson.databind.JsonNode;

import info.unterrainer.presserl.auth.CurrentUser;
import info.unterrainer.presserl.section.Newsroom;
import info.unterrainer.presserl.section.NewsroomService;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * Article endpoints for writers: publishers, editors-in-chief and holders of a section role
 * ({@link Newsroom#isWriter()}); everyone else gets {@code 403} with an empty body. Articles a
 * writer does not see answer {@code 404}. Which actions a user may perform on an article is
 * decided by {@link ArticlePolicy} and reported as {@code allowedActions}.
 */
@Path("/api/articles")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class ArticleResource {

    @Inject
    JsonWebToken token;

    @Inject
    NewsroomService newsrooms;

    @Inject
    ArticleService service;

    @Inject
    StaffingService staffing;

    @GET
    public Uni<List<ArticleSummaryDto>> list(@QueryParam("status") String status, @QueryParam("mine") boolean mine,
            @QueryParam("pending") boolean pending) {
        return writer().flatMap(newsroom -> service.list(newsroom, status(status), mine, pending)
                .flatMap(views -> staffing.forArticles(newsroom, views.stream().map(ArticleView::article).toList())
                        .map(staffed -> views.stream()
                                .map(view -> ArticleSummaryDto.of(view, newsroom, staffed))
                                .toList())));
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<RestResponse<ArticleDto>> create(JsonNode json) {
        return writer().flatMap(newsroom -> {
            ArticleContentValidator.Request request = ArticleContentValidator.validate(json, false);
            return dto(newsroom, service.create(newsroom, request.content(), request.sectionId()))
                    .map(dto -> RestResponse.ResponseBuilder
                            .<ArticleDto>created(URI.create("/api/articles/" + dto.id()))
                            .entity(dto)
                            .build());
        });
    }

    @GET
    @Path("/{id}")
    public Uni<ArticleDto> get(@PathParam("id") long id) {
        return writer().flatMap(newsroom -> dto(newsroom, service.get(newsroom, id)));
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<ArticleDto> save(@PathParam("id") long id, JsonNode json) {
        return writer().flatMap(newsroom -> {
            ArticleContentValidator.Request request = ArticleContentValidator.validate(json, true);
            return dto(newsroom, service.save(newsroom, id, request.content(), request.sectionId(), request.version()));
        });
    }

    @DELETE
    @Path("/{id}")
    public Uni<Void> delete(@PathParam("id") long id) {
        return writer().flatMap(newsroom -> service.delete(newsroom, id));
    }

    @POST
    @Path("/{id}/publish")
    public Uni<ArticleDto> publish(@PathParam("id") long id) {
        return writer().flatMap(newsroom -> dto(newsroom, service.publish(newsroom, id)));
    }

    @POST
    @Path("/{id}/submit")
    public Uni<ArticleDto> submit(@PathParam("id") long id) {
        return writer().flatMap(newsroom -> dto(newsroom, service.submit(newsroom, id)));
    }

    @POST
    @Path("/{id}/approve")
    public Uni<ArticleDto> approve(@PathParam("id") long id) {
        return writer().flatMap(newsroom -> dto(newsroom, service.approve(newsroom, id)));
    }

    /**
     * The body ({@code {"note": "..."}}) is validated before the policy, so a malformed body is
     * {@code 400} for everyone.
     */
    @POST
    @Path("/{id}/reject")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<ArticleDto> reject(@PathParam("id") long id, JsonNode json) {
        return writer().flatMap(newsroom -> {
            String note = RejectRequestValidator.validate(json);
            return dto(newsroom, service.reject(newsroom, id, note));
        });
    }

    @POST
    @Path("/{id}/withdraw")
    public Uni<ArticleDto> withdraw(@PathParam("id") long id) {
        return writer().flatMap(newsroom -> dto(newsroom, service.withdraw(newsroom, id)));
    }

    @POST
    @Path("/{id}/offline")
    public Uni<ArticleDto> takeOffline(@PathParam("id") long id) {
        return writer().flatMap(newsroom -> dto(newsroom, service.takeOffline(newsroom, id)));
    }

    @GET
    @Path("/{id}/reviews")
    public Uni<List<ReviewDto>> reviews(@PathParam("id") long id) {
        return writer().flatMap(newsroom -> service.reviews(newsroom, id)
                .map(reviews -> reviews.stream().map(ReviewDto::of).toList()));
    }

    @GET
    @Path("/{id}/revisions")
    public Uni<List<RevisionSummaryDto>> revisions(@PathParam("id") long id) {
        return writer().flatMap(newsroom -> service.revisions(newsroom, id).map(result -> result.revisions().stream()
                .map(revision -> RevisionSummaryDto.of(result.article(), revision))
                .toList()));
    }

    @GET
    @Path("/{id}/revisions/{number}")
    public Uni<RevisionDto> revision(@PathParam("id") long id, @PathParam("number") int number) {
        return writer().flatMap(newsroom -> service.revision(newsroom, id, number).map(RevisionDto::of));
    }

    /**
     * Maps the article with the staffing its {@code allowedActions} need.
     */
    private Uni<ArticleDto> dto(Newsroom newsroom, Uni<ArticleView> view) {
        return view.flatMap(v -> staffing.forArticles(newsroom, List.of(v.article()))
                .map(staffed -> ArticleDto.of(v, newsroom, staffed)));
    }

    /**
     * The newsroom of a writer; {@code 403} with an empty body for everyone else.
     */
    private Uni<Newsroom> writer() {
        return newsrooms.of(CurrentUser.of(token)).invoke(newsroom -> {
            if (!newsroom.isWriter()) {
                throw new ForbiddenException();
            }
        });
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
