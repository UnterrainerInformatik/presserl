package info.unterrainer.presserl.media;

import software.amazon.awssdk.services.s3.S3Client;

/**
 * The real store, except that the {@code failingPut}-th put (1-based) fails as if the store were
 * unreachable.
 */
class FailingMediaStore extends MediaStore {

    private final int failingPut;
    private int puts;

    FailingMediaStore(S3Client s3, String bucket, int failingPut) {
        super(s3, bucket);
        this.failingPut = failingPut;
    }

    @Override
    public synchronized String put(byte[] bytes, String contentType, String extension) {
        if (++puts == failingPut) {
            throw MediaException.unavailable("the media store is not reachable", null);
        }
        return super.put(bytes, contentType, extension);
    }
}
