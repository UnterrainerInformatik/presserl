package info.unterrainer.presserl.media;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
 * Produces the missing renditions of media stored before renditions existed. Starts once the
 * bucket exists ({@link MediaBucketBootstrap#bucketReady()}) on its own thread, so readiness does
 * not wait for it. One media at a time under the processing limit; a failure is logged and the media
 * is tried again on the next start.
 */
@Singleton
public class MediaRenditionBackfill {

    private static final Logger LOG = Logger.getLogger(MediaRenditionBackfill.class);
    private static final int KIND_COUNT = RenditionKind.values().length;

    @Inject
    MediaBucketBootstrap bucket;

    @Inject
    MediaService service;

    @Inject
    MediaStore store;

    private ExecutorService executor;

    void onStart(@Observes StartupEvent event) {
        executor = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("media-backfill").daemon().factory());
        bucket.bucketReady().thenRunAsync(this::run, executor);
    }

    void onStop(@Observes ShutdownEvent event) {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    /**
     * Fills in every media without a complete set of renditions.
     *
     * @return how many media got renditions
     */
    int run() {
        List<Long> ids;
        try {
            ids = await(() -> Panache.withSession(() -> Panache.getSession().flatMap(session -> session
                    .createSelectionQuery("select m.id from MediaEntity m where (select count(r) from "
                            + "MediaRenditionEntity r where r.mediaId = m.id) < :kinds order by m.id", Long.class)
                    .setParameter("kinds", (long) KIND_COUNT)
                    .getResultList())));
        } catch (Throwable e) {
            LOG.errorf("Rendition backfill could not list the media: %s", e.toString());
            return 0;
        }
        if (ids.isEmpty()) {
            return 0;
        }
        LOG.infof("Rendition backfill: %d media without renditions", ids.size());
        int done = 0;
        for (long id : ids) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            try {
                backfill(id);
                done++;
            } catch (Throwable e) {
                LOG.errorf("Rendition backfill failed for media %d, retried on the next start: %s", id, e.toString());
            }
        }
        LOG.infof("Rendition backfill: %d of %d media done", done, ids.size());
        return done;
    }

    private void backfill(long id) throws Throwable {
        MediaView view = await(() -> Panache.withSession(() -> MediaEntity.<MediaEntity>findById(id)
                .flatMap(media -> MediaRenditionEntity.<MediaRenditionEntity>list("mediaId", id)
                        .map(renditions -> new MediaView(media, renditions)))));
        Set<String> present = view.renditions().stream().map(r -> r.kind).collect(Collectors.toSet());
        byte[] stored = store.get(view.media().objectKey);
        List<MediaProcessor.Rendition> missing = service.underProcessingLimit(
                () -> service.processor().deriveRenditions(stored)).stream()
                .filter(rendition -> !present.contains(rendition.kind().value()))
                .toList();
        List<String> keys = service.putRenditions(missing);
        List<MediaRenditionEntity> rows = MediaService.renditionRows(id, missing, keys);
        try {
            await(() -> Panache.withTransaction(() -> MediaRenditionEntity.persist(rows)));
        } catch (Throwable e) {
            service.discard(keys, "the rendition records failed");
            throw e;
        }
    }

    private static <T> T await(Supplier<Uni<T>> uni) throws Throwable {
        return VertxContextSupport.subscribeAndAwait(uni);
    }
}
