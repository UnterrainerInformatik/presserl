package info.unterrainer.presserl.media;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.jboss.logging.Logger;

import info.unterrainer.presserl.bootstrap.Backoff;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Creates the media bucket after startup on its own thread, retrying with exponential backoff (1 s
 * up to 60 s) so a slowly starting object store does not stop the backend. Readiness reports the
 * store meanwhile ({@link MediaStoreHealthCheck}).
 */
@Singleton
public class MediaBucketBootstrap {

    private static final Logger LOG = Logger.getLogger(MediaBucketBootstrap.class);
    static final Duration INITIAL_DELAY = Duration.ofSeconds(1);
    static final Duration MAX_DELAY = Duration.ofSeconds(60);

    @Inject
    MediaStore store;

    private ScheduledExecutorService executor;

    void onStart(@Observes StartupEvent event) {
        executor = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("media-bucket").daemon().factory());
        Backoff backoff = new Backoff(INITIAL_DELAY, MAX_DELAY);
        executor.execute(() -> run(backoff));
    }

    void onStop(@Observes ShutdownEvent event) {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void run(Backoff backoff) {
        try {
            if (store.ensureBucket()) {
                LOG.infof("Media bucket %s created", store.bucket());
            } else {
                LOG.infof("Media bucket %s present", store.bucket());
            }
        } catch (RuntimeException e) {
            Duration delay = backoff.next();
            LOG.errorf("Media bucket %s not available, object store unreachable or refusing the credentials: %s - retrying in %d s",
                    store.bucket(), e.toString(), delay.toSeconds());
            if (!executor.isShutdown()) {
                executor.schedule(() -> run(backoff), delay.toMillis(), TimeUnit.MILLISECONDS);
            }
        }
    }
}
