package info.unterrainer.presserl.media;

import java.net.URI;
import java.util.List;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import com.fasterxml.jackson.databind.JsonNode;

import info.unterrainer.presserl.auth.CurrentUser;
import info.unterrainer.presserl.section.Newsroom;
import info.unterrainer.presserl.section.NewsroomService;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

/**
 * Media endpoints for writers and sectionless reporters ({@link Newsroom#mayUseMedia()},
 * {@code USE_MEDIA}): upload an image, list the media with their usage, read them back with their
 * renditions and edit them (under the rules of {@link MediaService#mayEdit}). Everyone else gets {@code 403}. Uploads are re-encoded by
 * {@link MediaProcessor}.
 */
@Path("/api/media")
@Authenticated
public class MediaResource {

    static final String FILE_PART = "file";
    static final String CACHE_CONTROL = "private, no-cache";

    @Inject
    JsonWebToken token;

    @Inject
    NewsroomService newsrooms;

    @Inject
    MediaService service;

    /**
     * Exactly one file part named {@code file}; Quarkus has already stored it in its uploads directory.
     */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<RestResponse<MediaDto>> upload(@RestForm(FileUpload.ALL) List<FileUpload> files) {
        return newsroom().flatMap(newsroom -> {
            MediaService.requireMediaUser(newsroom);
            FileUpload file = single(files);
            return service.upload(newsroom, file.uploadedFile(), file.size())
                    .map(MediaDto::of)
                    .map(dto -> RestResponse.ResponseBuilder.<MediaDto>created(URI.create("/api/media/" + dto.id()))
                            .entity(dto)
                            .type(MediaType.APPLICATION_JSON_TYPE)
                            .build());
        });
    }

    /**
     * The newspaper's media, newest first; {@code limit} (1–200, default 60) per page, {@code before}
     * the {@code next} value of the previous page.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<MediaPageDto> list(@QueryParam("limit") String limit, @QueryParam("before") String before) {
        return newsroom().flatMap(newsroom -> service.list(newsroom, limit, before));
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<MediaDto> get(@PathParam("id") long id) {
        return newsroom().flatMap(newsroom -> service.get(newsroom, id)).map(MediaDto::of);
    }

    /**
     * The articles using the media and whether the caller may edit it.
     */
    @GET
    @Path("/{id}/usage")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<MediaUsageDto> usage(@PathParam("id") long id) {
        return newsroom().flatMap(newsroom -> service.usage(newsroom, id));
    }

    /**
     * Crops and/or pixelates the image, replacing it and its renditions under the same id.
     */
    @POST
    @Path("/{id}/edit")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<MediaDto> edit(@PathParam("id") long id, JsonNode json) {
        return newsroom().flatMap(newsroom -> service.edit(newsroom, id, json)).map(MediaDto::of);
    }

    /**
     * The stored bytes. An edit replaces them, so browsers revalidate every time (privately, as they
     * need a token) with the {@code ETag} {@code "{id}-{version}"}; a matching {@code If-None-Match}
     * answers {@code 304}. No {@code @Produces}: with image types declared, Quarkus REST picks a writer
     * that serializes {@code byte[]} via {@code toString()}; the type is set on the response instead.
     */
    @GET
    @Path("/{id}/content")
    public Uni<RestResponse<byte[]>> content(@PathParam("id") long id,
            @HeaderParam(HttpHeaders.IF_NONE_MATCH) String ifNoneMatch) {
        return newsroom().flatMap(newsroom -> service.content(newsroom, id, ifNoneMatch)).map(MediaResource::bytes);
    }

    /**
     * One rendition ({@link RenditionKind#value()}), with the headers of {@link #content}.
     */
    @GET
    @Path("/{id}/renditions/{kind}")
    public Uni<RestResponse<byte[]>> rendition(@PathParam("id") long id, @PathParam("kind") String kind,
            @HeaderParam(HttpHeaders.IF_NONE_MATCH) String ifNoneMatch) {
        return newsroom().flatMap(newsroom -> service.rendition(newsroom, id, kind, ifNoneMatch))
                .map(MediaResource::bytes);
    }

    private static RestResponse<byte[]> bytes(MediaService.Content content) {
        if (content.bytes() == null) {
            return RestResponse.ResponseBuilder.<byte[]>create(RestResponse.Status.NOT_MODIFIED)
                    .header(HttpHeaders.ETAG, content.etag())
                    .header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL)
                    .build();
        }
        return RestResponse.ResponseBuilder.ok(content.bytes())
                .type(content.contentType())
                .header("Content-Disposition", "inline")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL)
                .header(HttpHeaders.ETAG, content.etag())
                .build();
    }

    private Uni<Newsroom> newsroom() {
        return newsrooms.of(CurrentUser.of(token));
    }

    private static FileUpload single(List<FileUpload> files) {
        if (files == null || files.isEmpty() || files.stream().noneMatch(f -> FILE_PART.equals(f.name()))) {
            throw MediaException.invalid("a file part named 'file' is required");
        }
        if (files.size() > 1) {
            throw MediaException.invalid("exactly one file is accepted");
        }
        return files.getFirst();
    }
}
