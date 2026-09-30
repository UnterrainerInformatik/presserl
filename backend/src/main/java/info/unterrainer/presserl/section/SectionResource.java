package info.unterrainer.presserl.section;

import java.net.URI;
import java.util.Map;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.RestResponse;

import com.fasterxml.jackson.databind.JsonNode;

import info.unterrainer.presserl.auth.CurrentUser;
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
 * Section endpoints. Every authenticated user may list sections; publishers and editors-in-chief
 * create, change, reorder and delete them; members are managed by whoever may assign section roles
 * in the section ({@link SectionDelegation}). Refused access is answered with {@code 403} and an
 * empty body.
 */
@Path("/api/sections")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class SectionResource {

    @Inject
    JsonWebToken token;

    @Inject
    NewsroomService newsrooms;

    @Inject
    SectionService service;

    @Inject
    SectionMembers members;

    @GET
    public Uni<SectionListDto> list() {
        return newsroom().flatMap(newsroom -> service.list().flatMap(sections -> counts(newsroom)
                .map(counts -> SectionListDto.of(sections, newsroom, counts))));
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<RestResponse<SectionDto>> create(JsonNode json) {
        return manager().flatMap(newsroom -> service.create(SectionRequestValidator.section(json, false))
                .map(section -> RestResponse.ResponseBuilder
                        .<SectionDto>created(URI.create("/api/sections/" + section.id))
                        .entity(SectionDto.of(section, newsroom, Map.of()))
                        .build()));
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<SectionDto> update(@PathParam("id") long id, JsonNode json) {
        return manager().flatMap(newsroom -> service.update(id, SectionRequestValidator.section(json, true))
                .flatMap(section -> counts(newsroom).map(counts -> SectionDto.of(section, newsroom, counts))));
    }

    @PUT
    @Path("/order")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<SectionListDto> reorder(JsonNode json) {
        return manager().flatMap(newsroom -> service.reorder(SectionRequestValidator.order(json))
                .flatMap(sections -> counts(newsroom).map(counts -> SectionListDto.of(sections, newsroom, counts))));
    }

    @DELETE
    @Path("/{id}")
    public Uni<Void> delete(@PathParam("id") long id) {
        return manager().flatMap(newsroom -> service.delete(id, newsroom.user()));
    }

    @GET
    @Path("/{id}/members")
    public Uni<MemberListDto> members(@PathParam("id") long id) {
        return newsroom().flatMap(newsroom -> members.list(newsroom, id));
    }

    @PUT
    @Path("/{id}/members/{accountId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<MemberDto> assign(@PathParam("id") long id, @PathParam("accountId") String accountId, JsonNode json) {
        return newsroom().flatMap(newsroom -> members.assign(newsroom, id, accountId,
                SectionRequestValidator.member(json)));
    }

    @DELETE
    @Path("/{id}/members/{accountId}")
    public Uni<Void> remove(@PathParam("id") long id, @PathParam("accountId") String accountId) {
        return newsroom().flatMap(newsroom -> members.remove(newsroom, id, accountId));
    }

    private Uni<Newsroom> newsroom() {
        return newsrooms.of(CurrentUser.of(token));
    }

    /**
     * The newsroom of a publisher or editor-in-chief; {@code 403} for everyone else.
     */
    private Uni<Newsroom> manager() {
        return newsroom().invoke(newsroom -> {
            if (!newsroom.mayManageSections()) {
                throw new ForbiddenException();
            }
        });
    }

    /**
     * The article counts for writers; none (answered as {@code null}) for everyone else.
     */
    private Uni<Map<Long, ArticleCountsDto>> counts(Newsroom newsroom) {
        return newsroom.isWriter() ? service.articleCounts() : Uni.createFrom().item(Map.of());
    }
}
