package info.unterrainer.presserl.account;

import org.keycloak.admin.client.Keycloak;

import info.unterrainer.presserl.bootstrap.KeycloakAdminProducer.KeycloakRealm;
import info.unterrainer.presserl.section.SectionEntity;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Test-only endpoint: a blocking Keycloak call followed by a Panache query in one reactive request.
 */
@Path("/test/keycloak-then-panache")
public class KeycloakCallsProbe {

    @Inject
    KeycloakCalls keycloakCalls;

    @Inject
    Keycloak keycloak;

    @Inject
    KeycloakRealm realm;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<String> probe() {
        return keycloakCalls.call(() -> keycloak.realm(realm.name()).users().count())
                .flatMap(users -> Panache.withSession(() -> SectionEntity.count())
                        .map(sections -> users + " users, " + sections + " sections, on "
                                + Thread.currentThread().getName()));
    }
}
