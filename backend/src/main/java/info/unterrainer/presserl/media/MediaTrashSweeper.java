package info.unterrainer.presserl.media;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.jboss.logging.Logger;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.vertx.VertxContextSupport;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Deletes the objects of replaced media images that an edit could not delete right away
 * ({@code media_object_trash}): once the bucket exists after start, then every
 * {@link #INTERVAL}, on its own thread. A row is removed only after its object is gone (a missing
 * object counts as gone), so a failed deletion is retried, also after a restart.
 */
@Singleton
public class MediaTrashSweeper {

    static final Duration INTERVAL = Duration.ofMinutes(10);
    private static final Logger LOG = Logger.getLogger(MediaTrashSweeper.class);

    @Inject
    MediaBucketBootstrap bucket;

    @Inject
    MediaService service;

    private ScheduledExecutorService executor;

    void onStart(@Observes StartupEvent event) {
        executor = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("media-trash").daemon().factory());
        bucket.bucketReady().thenRunAsync(() -> executor.scheduleWithFixedDelay(this::runLogged, 0,
                INTERVAL.toMillis(), TimeUnit.MILLISECONDS), executor);
    }

    void onStop(@Observes ShutdownEvent event) {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void runLogged() {
        try {
            run();
        } catch (Throwable e) {
            LOG.errorf("Media trash sweep failed, retried in %s: %s", INTERVAL, e.toString());
        }
    }

    /**
     * Deletes every trashed object and then its row.
     *
     * @return how many rows were removed
     */
    int run() throws Throwable {
        List<String> keys = await(() -> Panache.withSession(() -> MediaObjectTrashEntity
                .<MediaObjectTrashEntity>listAll()
                .map(rows -> rows.stream().map(row -> row.objectKey).toList())));
        if (keys.isEmpty()) {
            return 0;
        }
        List<String> deleted = service.deleteObjects(keys);
        if (deleted.isEmpty()) {
            return 0;
        }
        long removed = await(() -> Panache.withTransaction(
                () -> MediaObjectTrashEntity.delete("objectKey in ?1", deleted)));
        LOG.infof("Media trash sweep: %d of %d replaced objects deleted", removed, keys.size());
        return (int) removed;
    }

    private static <T> T await(Supplier<Uni<T>> uni) throws Throwable {
        return VertxContextSupport.subscribeAndAwait(uni);
    }
}
