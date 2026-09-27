package info.unterrainer.presserl.media;

import java.time.Duration;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * The media bucket in the S3-compatible object store. Objects are written once under a random key
 * {@code media/<uuid>.<ext>} and never overwritten. All calls block; failures of the store surface
 * as {@code 503} ({@link MediaException#unavailable}).
 */
@ApplicationScoped
public class MediaStore {

    static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(3);
    private static final String UNAVAILABLE = "the media store is not reachable";

    private final S3Client s3;
    private final String bucket;

    @Inject
    public MediaStore(S3Client s3, MediaConfig config) {
        this(s3, config.s3().bucket());
    }

    MediaStore(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    public String bucket() {
        return bucket;
    }

    /**
     * Stores the image under a new key.
     *
     * @return the object key
     */
    public String put(byte[] bytes, String contentType, String extension) {
        String key = "media/" + UUID.randomUUID() + "." + extension;
        try {
            s3.putObject(b -> b.bucket(bucket).key(key).contentType(contentType).contentLength((long) bytes.length),
                    RequestBody.fromBytes(bytes));
        } catch (SdkException e) {
            throw MediaException.unavailable(UNAVAILABLE, e);
        }
        return key;
    }

    public byte[] get(String key) {
        try {
            return s3.getObjectAsBytes(b -> b.bucket(bucket).key(key)).asByteArray();
        } catch (SdkException e) {
            throw MediaException.unavailable(UNAVAILABLE, e);
        }
    }

    public void delete(String key) {
        try {
            s3.deleteObject(b -> b.bucket(bucket).key(key));
        } catch (SdkException e) {
            throw MediaException.unavailable(UNAVAILABLE, e);
        }
    }

    /**
     * Creates the bucket if it does not exist yet.
     *
     * @return whether the bucket was created
     */
    public boolean ensureBucket() {
        try {
            s3.headBucket(b -> b.bucket(bucket));
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() != 404) {
                throw e;
            }
        }
        s3.createBucket(b -> b.bucket(bucket));
        return true;
    }

    /**
     * Checks that the bucket is reachable, within {@link #HEALTH_TIMEOUT}.
     *
     * @throws SdkException when the store or the bucket cannot be reached
     */
    public void checkBucket() {
        s3.headBucket(b -> b.bucket(bucket).overrideConfiguration(c -> c.apiCallTimeout(HEALTH_TIMEOUT)));
    }
}
