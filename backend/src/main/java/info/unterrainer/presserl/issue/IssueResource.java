package info.unterrainer.presserl.issue;

import java.net.URI;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.RestResponse;

import com.fasterxml.jackson.databind.JsonNode;

import info.unterrainer.presserl.article.ArticleSummaryDto;
import info.unterrainer.presserl.article.ArticleView;
import info.unterrainer.presserl.article.ArticleService;
import info.unterrainer.presserl.article.StaffingService;
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
import jakarta.ws.rs.core.MediaType;

/**
 * Issue endpoints for holders of {@code MANAGE_ISSUES} (publishers and editors-in-chief); everyone
 * else gets {@code 403} with an empty body. Articles are listed regardless of their visibility:
 * issue managers see every article anyway.
 */
@Path("/api/issues")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class IssueResource {

    @Inject
    JsonWebToken token;

    @Inject
    NewsroomService newsrooms;

    @Inject
    IssueService service;

    @Inject
    StaffingService staffing;

    @Inject
    ArticleService articles;

    @GET
    public Uni<IssueListDto> list() {
        return manager().flatMap(newsroom -> service.list()).map(summaries -> new IssueListDto(summaries.stream()
                .map(summary -> IssueDto.of(summary.issue(), summary.articleCount(), summary.newest()))
                .toList()));
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<RestResponse<IssueDetailDto>> create(JsonNode json) {
        return manager().flatMap(newsroom -> dto(newsroom, service.create(IssueRequestValidator.issue(json, false),
                newsroom.user().username())))
                .map(dto -> RestResponse.ResponseBuilder
                        .<IssueDetailDto>created(URI.create("/api/issues/" + dto.id()))
                        .entity(dto)
                        .build());
    }

    @GET
    @Path("/{id}")
    public Uni<IssueDetailDto> get(@PathParam("id") long id) {
        return manager().flatMap(newsroom -> dto(newsroom, service.get(id)));
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<IssueDetailDto> setDate(@PathParam("id") long id, JsonNode json) {
        return manager().flatMap(newsroom -> dto(newsroom, service.setDate(id, IssueRequestValidator.issue(json, true))));
    }

    @POST
    @Path("/{id}/publish")
    public Uni<IssueDetailDto> publish(@PathParam("id") long id) {
        return manager().flatMap(newsroom -> dto(newsroom, service.publish(id, newsroom.user().username())));
    }

    @POST
    @Path("/{id}/unpublish")
    public Uni<IssueDetailDto> unpublish(@PathParam("id") long id) {
        return manager().flatMap(newsroom -> dto(newsroom, service.unpublish(id, newsroom.user().username())));
    }

    @PUT
    @Path("/{id}/articles")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<IssueDetailDto> setArticles(@PathParam("id") long id, JsonNode json) {
        return manager().flatMap(newsroom -> dto(newsroom, service.setArticles(id,
                IssueRequestValidator.articleIds(json))));
    }

    @DELETE
    @Path("/{id}")
    public Uni<Void> delete(@PathParam("id") long id) {
        return manager().flatMap(newsroom -> service.delete(id, newsroom.user().username()));
    }

    /**
     * Maps the issue with the staffing its articles' {@code allowedActions} need.
     */
    private Uni<IssueDetailDto> dto(Newsroom newsroom, Uni<IssueService.Details> details) {
        return details.flatMap(d -> staffing.forArticles(d.articles().stream().map(ArticleView::article).toList())
                .flatMap(staffed -> articles.rules().map(rules -> IssueDetailDto.of(d.issue(), d.newest(),
                        d.articles().stream()
                                .map(view -> ArticleSummaryDto.of(view, newsroom, staffed, rules))
                                .toList()))));
    }

    /**
     * The newsroom of an issue manager; {@code 403} with an empty body for everyone else.
     */
    private Uni<Newsroom> manager() {
        return newsrooms.of(CurrentUser.of(token)).invoke(newsroom -> {
            if (!newsroom.mayManageIssues()) {
                throw new ForbiddenException();
            }
        });
    }
}
