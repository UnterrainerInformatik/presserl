package info.unterrainer.presserl.account;

import java.util.function.Supplier;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Runs blocking Keycloak Admin calls from reactive endpoints: the call runs on a worker thread and
 * the result is delivered back on the caller's Vert.x context, so Hibernate Reactive (Panache)
 * calls can follow in the same chain.
 */
@ApplicationScoped
public class KeycloakCalls {

    @Inject
    Vertx vertx;

    public <T> Uni<T> call(Supplier<T> blocking) {
        return vertx.executeBlocking(Uni.createFrom().item(blocking));
    }

    public Uni<Void> run(Runnable blocking) {
        return call(() -> {
            blocking.run();
            return null;
        }).replaceWithVoid();
    }
}
