package info.unterrainer.presserl.media;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

import org.jboss.logging.Logger;

import info.unterrainer.presserl.section.Newsroom;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.runtime.Startup;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Uploading and reading media for writers ({@code WRITE_ARTICLES}). Decoding, encoding and the
 * blocking object-store calls run on worker threads; at most
 * {@code presserl.media.max-concurrent-processing} images are processed at once. An upload writes the
 * objects (image and renditions) first and the rows second, in one transaction, so no row ever
 * points to a missing object; a failure removes the objects already written.
 */
@Startup
@ApplicationScoped
public class MediaService {

    private static final Logger LOG = Logger.getLogger(MediaService.class);

    @Inject
    MediaConfig config;

    @Inject
    MediaStore store;

    @Inject
    Vertx vertx;

    private final MediaProcessor processor = new MediaProcessor();
    private long maxBytes;
    private Semaphore processing;

    /**
     * Fails startup when {@code presserl.media.max-size} is unreadable or above 60M.
     */
    @PostConstruct
    void init() {
        maxBytes = MediaLimits.maxBytes(config.maxSize());
        processing = new Semaphore(Math.max(1, config.maxConcurrentProcessing()), true);
    }

    public Uni<MediaView> upload(Newsroom newsroom, Path file, long size) {
        requireWriter(newsroom);
        if (size == 0) {
            throw MediaException.invalid("the file is empty");
        }
        if (size > maxBytes) {
            throw MediaException.tooLarge("the file is larger than " + config.maxSize());
        }
        return vertx.executeBlocking(Uni.createFrom().item(() -> store(process(file))))
                .flatMap(stored -> insert(newsroom, stored)
                        .onFailure().call(e -> vertx.executeBlocking(Uni.createFrom().item(() -> {
                            discard(stored.keys(), "the media record failed");
                            return null;
                        }))));
    }

    public Uni<MediaView> get(Newsroom newsroom, long id) {
        requireWriter(newsroom);
        return Panache.withSession(() -> MediaEntity.<MediaEntity>findById(id)
                .onItem().ifNull().failWith(() -> MediaException.notFound("media " + id + " does not exist"))
                .flatMap(media -> MediaRenditionEntity.<MediaRenditionEntity>list("mediaId", id)
                        .map(renditions -> new MediaView(media, renditions))));
    }

    public Uni<Content> content(Newsroom newsroom, long id) {
        return get(newsroom, id).map(MediaView::media).flatMap(media -> vertx.executeBlocking(Uni.createFrom()
                .item(() -> new Content(media.contentType, store.get(media.objectKey)))));
    }

    /**
     * The bytes of one rendition; {@code 404} for an unknown kind and for a rendition not produced yet.
     */
    public Uni<Content> rendition(Newsroom newsroom, long id, String kind) {
        requireWriter(newsroom);
        RenditionKind parsed = RenditionKind.parse(kind)
                .orElseThrow(() -> MediaException.notFound("unknown rendition '" + kind + "'"));
        return Panache.withSession(() -> MediaRenditionEntity
                .<MediaRenditionEntity>findById(new MediaRenditionId(id, parsed.value())))
                .onItem().ifNull().failWith(() -> MediaException.notFound(
                        "media " + id + " has no rendition " + parsed.value()))
                .flatMap(rendition -> vertx.executeBlocking(Uni.createFrom()
                        .item(() -> new Content(rendition.contentType, store.get(rendition.objectKey)))));
    }

    /**
     * Stored bytes and their content type.
     */
    public record Content(String contentType, byte[] bytes) {
    }

    /**
     * The processed upload and its object keys: the image first, then the renditions in the order of
     * {@link MediaProcessor.Processed#renditions()}.
     */
    private record Stored(MediaProcessor.Processed processed, List<String> keys) {
    }

    MediaProcessor.Processed process(Path file) {
        return underProcessingLimit(() -> processor.process(file));
    }

    /**
     * Runs one decode/encode job while holding a processing slot.
     */
    <T> T underProcessingLimit(Supplier<T> job) {
        try {
            processing.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting to process an image", e);
        }
        try {
            return job.get();
        } finally {
            processing.release();
        }
    }

    MediaProcessor processor() {
        return processor;
    }

    /**
     * Puts the image and its renditions; when one put fails, the objects already written are deleted.
     */
    private Stored store(MediaProcessor.Processed processed) {
        List<String> keys = new ArrayList<>();
        try {
            keys.add(store.put(processed.bytes(), processed.contentType(), processed.extension()));
            keys.addAll(putRenditions(processed.renditions()));
        } catch (RuntimeException e) {
            discard(keys, "storing the upload failed");
            throw e;
        }
        return new Stored(processed, keys);
    }

    /**
     * Puts the renditions in order; when one put fails, the ones already written are deleted.
     *
     * @return their object keys, in the order of {@code renditions}
     */
    List<String> putRenditions(List<MediaProcessor.Rendition> renditions) {
        List<String> keys = new ArrayList<>();
        try {
            for (MediaProcessor.Rendition rendition : renditions) {
                keys.add(store.put(rendition.bytes(), rendition.contentType(), rendition.extension()));
            }
        } catch (RuntimeException e) {
            discard(keys, "storing a rendition failed");
            throw e;
        }
        return keys;
    }

    private Uni<MediaView> insert(Newsroom newsroom, Stored stored) {
        MediaProcessor.Processed processed = stored.processed();
        MediaEntity media = new MediaEntity();
        media.objectKey = stored.keys().getFirst();
        media.contentType = processed.contentType();
        media.width = processed.width();
        media.height = processed.height();
        media.byteSize = processed.bytes().length;
        media.uploaderSub = newsroom.user().sub();
        media.uploaderUsername = newsroom.user().username();
        media.uploaderDisplayName = newsroom.user().displayName();
        // PostgreSQL keeps microseconds
        media.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return Panache.withTransaction(() -> media.<MediaEntity>persist().flatMap(persisted -> {
            List<MediaRenditionEntity> rows = renditionRows(persisted.id, processed.renditions(),
                    stored.keys().subList(1, stored.keys().size()));
            return MediaRenditionEntity.persist(rows).replaceWith(new MediaView(persisted, rows));
        }));
    }

    /**
     * The rows for renditions stored under {@code keys} (same order).
     */
    static List<MediaRenditionEntity> renditionRows(long mediaId, List<MediaProcessor.Rendition> renditions,
            List<String> keys) {
        List<MediaRenditionEntity> rows = new ArrayList<>();
        for (int i = 0; i < renditions.size(); i++) {
            MediaProcessor.Rendition rendition = renditions.get(i);
            MediaRenditionEntity row = new MediaRenditionEntity();
            row.mediaId = mediaId;
            row.kind = rendition.kind().value();
            row.objectKey = keys.get(i);
            row.contentType = rendition.contentType();
            row.width = rendition.width();
            row.height = rendition.height();
            row.byteSize = rendition.bytes().length;
            rows.add(row);
        }
        return rows;
    }

    /**
     * Best effort: an orphaned object is harmless, it is only logged.
     */
    void discard(List<String> keys, String reason) {
        for (String key : keys) {
            try {
                store.delete(key);
            } catch (RuntimeException e) {
                LOG.warnf("Could not delete media object %s after %s: %s", key, reason, e.toString());
            }
        }
    }

    static void requireWriter(Newsroom newsroom) {
        if (!newsroom.isWriter()) {
            throw MediaException.forbidden("only writers may upload and read media");
        }
    }
}
