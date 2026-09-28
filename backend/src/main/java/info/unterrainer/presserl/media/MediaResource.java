package info.unterrainer.presserl.media;

import java.net.URI;
import java.util.List;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import info.unterrainer.presserl.auth.CurrentUser;
import info.unterrainer.presserl.section.Newsroom;
import info.unterrainer.presserl.section.NewsroomService;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Media endpoints for writers ({@link Newsroom#isWriter()}, {@code WRITE_ARTICLES}): upload an image
 * and read it back with its renditions. Everyone else gets {@code 403}. Uploads are re-encoded by
 * {@link MediaProcessor}.
 */
@Path("/api/media")
@Authenticated
public class MediaResource {

    static final String FILE_PART = "file";

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
            MediaService.requireWriter(newsroom);
            FileUpload file = single(files);
            return service.upload(newsroom, file.uploadedFile(), file.size())
                    .map(MediaDto::of)
                    .map(dto -> RestResponse.ResponseBuilder.<MediaDto>created(URI.create("/api/media/" + dto.id()))
                            .entity(dto)
                            .type(MediaType.APPLICATION_JSON_TYPE)
                            .build());
        });
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<MediaDto> get(@PathParam("id") long id) {
        return newsroom().flatMap(newsroom -> service.get(newsroom, id)).map(MediaDto::of);
    }

    /**
     * The stored bytes; they never change, so browsers may keep them for a year (privately, as they
     * need a token). No {@code @Produces}: with image types declared, Quarkus REST picks a writer
     * that serializes {@code byte[]} via {@code toString()}; the type is set on the response instead.
     */
    @GET
    @Path("/{id}/content")
    public Uni<RestResponse<byte[]>> content(@PathParam("id") long id) {
        return newsroom().flatMap(newsroom -> service.content(newsroom, id)).map(MediaResource::bytes);
    }

    /**
     * One rendition ({@link RenditionKind#value()}), with the headers of {@link #content}.
     */
    @GET
    @Path("/{id}/renditions/{kind}")
    public Uni<RestResponse<byte[]>> rendition(@PathParam("id") long id, @PathParam("kind") String kind) {
        return newsroom().flatMap(newsroom -> service.rendition(newsroom, id, kind)).map(MediaResource::bytes);
    }

    private static RestResponse<byte[]> bytes(MediaService.Content content) {
        return RestResponse.ResponseBuilder.ok(content.bytes())
                .type(content.contentType())
                .header("Content-Disposition", "inline")
                .header("X-Content-Type-Options", "nosniff")
                .header("Cache-Control", "private, max-age=31536000, immutable")
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
