package info.unterrainer.presserl.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.UUID;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Bucket creation and the readiness check against the Dev Services RustFS and an unreachable store.
 */
@QuarkusTest
class MediaStoreTest {

    @Inject
    S3Client s3;

    @Inject
    MediaStore configured;

    @Test
    void configuredBucketExistsAfterStart() {
        s3.headBucket(b -> b.bucket(configured.bucket()));
    }

    @Test
    void missingBucketIsCreated() {
        MediaStore store = new MediaStore(s3, "fresh-" + UUID.randomUUID());
        try {
            assertThat(store.ensureBucket()).isTrue();
            assertThat(store.ensureBucket()).isFalse();
            assertThat(new MediaStoreHealthCheck(store).call().getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        } finally {
            s3.deleteBucket(b -> b.bucket(store.bucket()));
        }
    }

    @Test
    void missingBucketIsDown() {
        MediaStore store = new MediaStore(s3, "missing-" + UUID.randomUUID());

        assertThat(new MediaStoreHealthCheck(store).call().getStatus()).isEqualTo(HealthCheckResponse.Status.DOWN);
    }

    @Test
    void unreachableStoreIsDownAndUnavailable() {
        try (S3Client unreachable = S3Client.builder()
                .endpointOverride(URI.create("http://127.0.0.1:1"))
                .region(Region.US_EAST_1)
                .forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("a", "b")))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build()) {
            MediaStore store = new MediaStore(unreachable, "presserl-media");

            HealthCheckResponse health = new MediaStoreHealthCheck(store).call();
            assertThat(health.getName()).isEqualTo("media-store");
            assertThat(health.getStatus()).isEqualTo(HealthCheckResponse.Status.DOWN);
            assertThatThrownBy(() -> store.put(new byte[] { 1 }, "image/jpeg", "jpg"))
                    .isInstanceOfSatisfying(MediaException.class,
                            e -> assertThat(e.status().getStatusCode()).isEqualTo(503));
        }
    }
}
