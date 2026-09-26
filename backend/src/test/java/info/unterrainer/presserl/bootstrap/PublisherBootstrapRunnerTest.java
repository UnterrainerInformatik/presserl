package info.unterrainer.presserl.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PublisherBootstrapRunnerTest {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @AfterEach
    void stop() {
        scheduler.shutdownNow();
    }

    private static HealthCheckResponse.Status readiness(PublisherBootstrapRunner runner) {
        PublisherBootstrapHealthCheck check = new PublisherBootstrapHealthCheck();
        check.runner = runner;
        return check.call().getStatus();
    }

    private static void awaitAttempts(AtomicInteger attempts, int count) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (attempts.get() < count && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
    }

    @Test
    void readinessIsDownUntilKeycloakBecomesReachable() throws InterruptedException {
        PublisherBootstrapRunner runner = new PublisherBootstrapRunner();
        AtomicInteger attempts = new AtomicInteger();

        runner.start(scheduler, () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("Connection refused");
            }
            return PublisherBootstrap.Outcome.CREATED;
        }, new Backoff(Duration.ofMillis(10), Duration.ofMillis(40)));

        awaitAttempts(attempts, 3);
        Thread.sleep(50);
        assertThat(attempts).hasValue(3);
        assertThat(runner.isCompleted()).isTrue();
        assertThat(readiness(runner)).isEqualTo(HealthCheckResponse.Status.UP);
    }

    @Test
    void missingGroupKeepsReadinessDownAndRetries() throws InterruptedException {
        PublisherBootstrapRunner runner = new PublisherBootstrapRunner();
        AtomicInteger attempts = new AtomicInteger();

        runner.start(scheduler, () -> {
            attempts.incrementAndGet();
            throw new BootstrapException("Keycloak realm 'presserl' has no group 'publisher'");
        }, new Backoff(Duration.ofMillis(10), Duration.ofMillis(20)));

        awaitAttempts(attempts, 4);
        assertThat(attempts.get()).isGreaterThanOrEqualTo(4);
        assertThat(readiness(runner)).isEqualTo(HealthCheckResponse.Status.DOWN);
    }

    @Test
    void backoffDoublesUpToTheCap() {
        Backoff backoff = new Backoff(PublisherBootstrapRunner.INITIAL_DELAY, PublisherBootstrapRunner.MAX_DELAY);

        assertThat(java.util.stream.Stream.generate(backoff::next).limit(9).map(Duration::toSeconds))
                .containsExactly(1L, 2L, 4L, 8L, 16L, 32L, 60L, 60L, 60L);
    }
}
