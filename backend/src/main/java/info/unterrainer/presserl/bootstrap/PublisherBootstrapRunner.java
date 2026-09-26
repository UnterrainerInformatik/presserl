package info.unterrainer.presserl.bootstrap;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.jboss.logging.Logger;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Runs the publisher bootstrap after startup on its own thread and retries with exponential
 * backoff (1 s up to 60 s) until it succeeds; the HTTP server is not blocked meanwhile.
 * Missing credentials fail startup.
 */
@Singleton
public class PublisherBootstrapRunner {

    private static final Logger LOG = Logger.getLogger(PublisherBootstrapRunner.class);
    static final Duration INITIAL_DELAY = Duration.ofSeconds(1);
    static final Duration MAX_DELAY = Duration.ofSeconds(60);

    @Inject
    PublisherConfig config;

    @Inject
    PublisherBootstrap bootstrap;

    private ScheduledExecutorService executor;
    private volatile boolean completed;

    void onStart(@Observes StartupEvent event) {
        PublisherCredentials credentials = PublisherCredentials.from(config);
        executor = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("publisher-bootstrap").daemon().factory());
        start(executor, () -> bootstrap.attempt(credentials), new Backoff(INITIAL_DELAY, MAX_DELAY));
    }

    void onStop(@Observes ShutdownEvent event) {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    /**
     * Schedules the first attempt immediately and every retry after the next backoff delay.
     */
    void start(ScheduledExecutorService scheduler, Supplier<PublisherBootstrap.Outcome> attempt, Backoff backoff) {
        scheduler.execute(() -> run(scheduler, attempt, backoff));
    }

    private void run(ScheduledExecutorService scheduler, Supplier<PublisherBootstrap.Outcome> attempt, Backoff backoff) {
        try {
            PublisherBootstrap.Outcome outcome = attempt.get();
            completed = true;
            LOG.infof("Publisher bootstrap complete (%s)", outcome);
        } catch (RuntimeException e) {
            Duration delay = backoff.next();
            if (e instanceof BootstrapException) {
                LOG.errorf("Publisher bootstrap failed: %s - retrying in %d s", e.getMessage(), delay.toSeconds());
            } else {
                LOG.errorf("Publisher bootstrap failed, Keycloak unreachable or refusing the backend client: %s - retrying in %d s",
                        e.toString(), delay.toSeconds());
            }
            if (!scheduler.isShutdown()) {
                scheduler.schedule(() -> run(scheduler, attempt, backoff), delay.toMillis(), TimeUnit.MILLISECONDS);
            }
        }
    }

    public boolean isCompleted() {
        return completed;
    }
}
