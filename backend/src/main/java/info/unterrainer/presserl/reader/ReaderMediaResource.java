package info.unterrainer.presserl.reader;

import java.util.Optional;

import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.RestResponse.ResponseBuilder;
import org.jboss.resteasy.reactive.RestResponse.Status;

import info.unterrainer.presserl.media.MediaException;
import info.unterrainer.presserl.media.MediaStore;
import info.unterrainer.presserl.media.RenditionKind;
import info.unterrainer.presserl.newspaper.NewspaperSettings;
import info.unterrainer.presserl.newspaper.Visibility;
import info.unterrainer.presserl.reader.ReaderViewer.Access;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.HttpHeaders;

/**
 * Lead images for readers: {@code /media/{id}/{kind}} serves a rendition only while the media is the
 * lead image of a live revision of a published article, and only to visitors who may read the
 * newspaper. Everything else, including a malformed id or an unknown kind, is an empty {@code 404}
 * (the route is only loaded by {@code <img>}, so no page and no login redirect). The stored image
 * itself is never served. Caches keep an image for at most an hour, so one taken offline disappears.
 * No {@code @Produces}: the type is set on the response (see {@code MediaResource#content}).
 */
@Path("/media")
public class ReaderMediaResource {

    static final String PUBLIC_CACHE = "public, max-age=3600";
    static final String PRIVATE_CACHE = "private, max-age=3600";

    @Inject
    NewspaperSettings settings;

    @Inject
    ReaderArticles articles;

    @Inject
    SecurityIdentity identity;

    @Inject
    MediaStore store;

    @Inject
    Vertx vertx;

    @GET
    @Path("{id}/{kind}")
    public Uni<RestResponse<byte[]>> rendition(@PathParam("id") String id, @PathParam("kind") String kind) {
        Optional<RenditionKind> parsed = RenditionKind.parse(kind);
        if (!id.matches("\\d{1,18}") || parsed.isEmpty()) {
            return Uni.createFrom().item(empty(Status.NOT_FOUND));
        }
        ReaderViewer viewer = ReaderViewer.of(identity);
        return settings.effective().flatMap(s -> {
            boolean privateNewspaper = s.visibility() == Visibility.PRIVATE;
            if (privateNewspaper && viewer.access() != Access.ENTITLED) {
                return Uni.createFrom().item(empty(Status.NOT_FOUND));
            }
            return articles.publishedRendition(Long.parseLong(id), parsed.get()).flatMap(found -> found
                    .map(rendition -> vertx.executeBlocking(Uni.createFrom().item(() -> store.get(rendition.objectKey)))
                            .map(bytes -> ResponseBuilder.ok(bytes)
                                    .type(rendition.contentType)
                                    .header("X-Content-Type-Options", "nosniff")
                                    .header(HttpHeaders.CACHE_CONTROL, privateNewspaper ? PRIVATE_CACHE : PUBLIC_CACHE)
                                    .build())
                            .onFailure(MediaException.class).recoverWithItem(e -> empty(Status.SERVICE_UNAVAILABLE)))
                    .orElseGet(() -> Uni.createFrom().item(empty(Status.NOT_FOUND))));
        });
    }

    private static RestResponse<byte[]> empty(Status status) {
        return ResponseBuilder.<byte[]> create(status).header(HttpHeaders.CACHE_CONTROL, ReaderResource.NO_STORE)
                .build();
    }
}
