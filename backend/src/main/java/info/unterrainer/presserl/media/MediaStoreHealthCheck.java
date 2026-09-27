package info.unterrainer.presserl.media;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Ready only while the object store answers and the media bucket exists.
 */
@Readiness
@ApplicationScoped
public class MediaStoreHealthCheck implements HealthCheck {

    public static final String NAME = "media-store";

    @Inject
    MediaStore store;

    public MediaStoreHealthCheck() {
    }

    MediaStoreHealthCheck(MediaStore store) {
        this.store = store;
    }

    @Override
    public HealthCheckResponse call() {
        try {
            store.checkBucket();
            return HealthCheckResponse.named(NAME).up().withData("bucket", store.bucket()).build();
        } catch (RuntimeException e) {
            return HealthCheckResponse.named(NAME).down().withData("bucket", store.bucket())
                    .withData("error", e.getClass().getSimpleName()).build();
        }
    }
}
