package info.unterrainer.presserl.article;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.RestResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    static final String WEIGHT = "weight";
    static final int MIN_WEIGHT = 1;
    static final int MAX_WEIGHT = 999;

    @Inject
    JsonWebToken token;

    @Inject
    NewsroomService newsrooms;

    @Inject
    ArticleService service;

    @Inject
    StaffingService staffing;

    @Inject
    ObjectMapper mapper;

    /**
     * {@code awaitingMe} keeps only the articles the user may approve now: the query narrows to
     * pending articles of other authors, {@link ArticlePolicy} decides via {@code APPROVE}.
     * {@code sort} is one of {@link ArticleSort}, {@code changed} when absent.
     */
    @GET
    public Uni<List<ArticleSummaryDto>> list(@QueryParam("status") String status, @QueryParam("mine") boolean mine,
            @QueryParam("pending") boolean pending, @QueryParam("awaitingMe") boolean awaitingMe,
            @QueryParam("sort") String sort) {
        return writer().flatMap(newsroom -> service.list(newsroom, status(status), mine, pending || awaitingMe,
                awaitingMe, sort(sort))
                .flatMap(views -> staffing.forArticles(views.stream().map(ArticleView::article).toList())
                        .flatMap(staffed -> service.rules().map(rules -> views.stream()
                                .map(view -> ArticleSummaryDto.of(view, newsroom, staffed, rules))
                                .filter(summary -> !awaitingMe
                                        || summary.allowedActions().contains(ArticleAction.APPROVE))
                                .toList()))));
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

    /**
     * The optional body ({@code {"version": 7}}) is read as text, so a request without body or
     * content type is accepted as before; it is validated before the policy.
     */
    @POST
    @Path("/{id}/approve")
    public Uni<ArticleDto> approve(@PathParam("id") long id, String body) {
        return writer().flatMap(newsroom -> {
            Long version = RejectRequestValidator.approveVersion(json(body));
            return dto(newsroom, service.approve(newsroom, id, version));
        });
    }

    /**
     * The body ({@code {"note": "...", "version": 7}}, version optional) is validated before the
     * policy, so a malformed body is {@code 400} for everyone.
     */
    @POST
    @Path("/{id}/reject")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<ArticleDto> reject(@PathParam("id") long id, JsonNode json) {
        return writer().flatMap(newsroom -> {
            RejectRequestValidator.Request request = RejectRequestValidator.validate(json);
            return dto(newsroom, service.reject(newsroom, id, request.note(), request.version()));
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

    @POST
    @Path("/{id}/unlock")
    public Uni<ArticleDto> unlock(@PathParam("id") long id) {
        return writer().flatMap(newsroom -> dto(newsroom, service.unlock(newsroom, id)));
    }

    /**
     * Sets ({@code {"weight": 1}}) or clears ({@code {"weight": null}}) the front-page weight; the body
     * is validated before the role check. Deliberately not in {@code allowedActions}: the admin app
     * offers it by role.
     */
    @PUT
    @Path("/{id}/front-page-weight")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<ArticleDto> setFrontPageWeight(@PathParam("id") long id, JsonNode json) {
        return writer().flatMap(newsroom -> dto(newsroom, service.setFrontPageWeight(newsroom, id, weight(json))));
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
        return writer().flatMap(newsroom -> service.revision(newsroom, id, number)
                .flatMap(view -> service.leadImage(view.revision()).map(leadImage -> RevisionDto.of(view, leadImage))));
    }

    /**
     * Maps the article with the staffing and rules its {@code allowedActions} need and its lead image.
     */
    private Uni<ArticleDto> dto(Newsroom newsroom, Uni<ArticleView> view) {
        return view.flatMap(v -> staffing.forArticles(List.of(v.article()))
                .flatMap(staffed -> service.rules()
                        .flatMap(rules -> service.leadImage(v.revision())
                                .map(leadImage -> ArticleDto.of(v, newsroom, staffed, rules, leadImage)))));
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

    /**
     * An absent or empty body is no body.
     *
     * @throws ArticleException {@code 400} for a body that is not JSON
     */
    private JsonNode json(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw ArticleException.invalid(null, "request body must be JSON");
        }
    }

    /**
     * The weight of a {@code front-page-weight} body: an integer from 1 to 999 or {@code null}.
     *
     * @throws ArticleException {@code 400 weight} for a missing field, a non-integer or a value out of
     *                          range
     */
    static Integer weight(JsonNode json) {
        if (json == null || !json.isObject() || !json.has(WEIGHT)) {
            throw ArticleException.invalid(WEIGHT, "weight is required: a number from 1 to 999, or null to clear it");
        }
        JsonNode weight = json.get(WEIGHT);
        if (weight.isNull()) {
            return null;
        }
        if (!weight.isIntegralNumber() || !weight.canConvertToInt() || weight.intValue() < MIN_WEIGHT
                || weight.intValue() > MAX_WEIGHT) {
            throw ArticleException.invalid(WEIGHT, "weight must be a whole number from 1 to 999, or null");
        }
        return weight.intValue();
    }

    private static ArticleSort sort(String value) {
        if (value == null || value.isEmpty()) {
            return ArticleSort.CHANGED;
        }
        return ArticleSort.parse(value).orElseThrow(() -> ArticleException.invalid("sort", "unknown sort '" + value
                + "'; allowed: changed, newest, section"));
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
