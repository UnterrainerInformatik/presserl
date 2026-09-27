package info.unterrainer.presserl.media;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Semaphore;

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
 * object first and the row second, so no row ever points to a missing object.
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

    public Uni<MediaEntity> upload(Newsroom newsroom, Path file, long size) {
        requireWriter(newsroom);
        if (size == 0) {
            throw MediaException.invalid("the file is empty");
        }
        if (size > maxBytes) {
            throw MediaException.tooLarge("the file is larger than " + config.maxSize());
        }
        return vertx.executeBlocking(Uni.createFrom().item(() -> {
            MediaProcessor.Processed processed = process(file);
            return new Stored(processed, store.put(processed.bytes(), processed.contentType(), processed.extension()));
        })).flatMap(stored -> insert(newsroom, stored)
                .onFailure().call(e -> vertx.executeBlocking(Uni.createFrom().item(() -> {
                    discard(stored.key());
                    return null;
                }))));
    }

    public Uni<MediaEntity> get(Newsroom newsroom, long id) {
        requireWriter(newsroom);
        return Panache.withSession(() -> MediaEntity.<MediaEntity>findById(id))
                .onItem().ifNull().failWith(() -> MediaException.notFound("media " + id + " does not exist"));
    }

    public Uni<Content> content(Newsroom newsroom, long id) {
        return get(newsroom, id).flatMap(media -> vertx.executeBlocking(Uni.createFrom()
                .item(() -> new Content(media, store.get(media.objectKey)))));
    }

    /**
     * The media record and the stored bytes.
     */
    public record Content(MediaEntity media, byte[] bytes) {
    }

    private record Stored(MediaProcessor.Processed processed, String key) {
    }

    private MediaProcessor.Processed process(Path file) {
        try {
            processing.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting to process an image", e);
        }
        try {
            return processor.process(file);
        } finally {
            processing.release();
        }
    }

    private Uni<MediaEntity> insert(Newsroom newsroom, Stored stored) {
        MediaEntity media = new MediaEntity();
        media.objectKey = stored.key();
        media.contentType = stored.processed().contentType();
        media.width = stored.processed().width();
        media.height = stored.processed().height();
        media.byteSize = stored.processed().bytes().length;
        media.uploaderSub = newsroom.user().sub();
        media.uploaderUsername = newsroom.user().username();
        media.uploaderDisplayName = newsroom.user().displayName();
        // PostgreSQL keeps microseconds
        media.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return Panache.withTransaction(media::persist).replaceWith(media);
    }

    /**
     * Best effort: an orphaned object is harmless, it is only logged.
     */
    private void discard(String key) {
        try {
            store.delete(key);
        } catch (RuntimeException e) {
            LOG.warnf("Could not delete media object %s after the media record failed: %s", key, e.toString());
        }
    }

    static void requireWriter(Newsroom newsroom) {
        if (!newsroom.isWriter()) {
            throw MediaException.forbidden("only writers may upload and read media");
        }
    }
}
