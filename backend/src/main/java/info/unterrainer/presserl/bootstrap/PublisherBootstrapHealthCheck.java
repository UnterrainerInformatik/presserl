package info.unterrainer.presserl.bootstrap;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Ready only once the first publisher is guaranteed to exist.
 */
@Readiness
@ApplicationScoped
public class PublisherBootstrapHealthCheck implements HealthCheck {

    public static final String NAME = "publisher-bootstrap";

    @Inject
    PublisherBootstrapRunner runner;

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.named(NAME).status(runner.isCompleted()).build();
    }
}
