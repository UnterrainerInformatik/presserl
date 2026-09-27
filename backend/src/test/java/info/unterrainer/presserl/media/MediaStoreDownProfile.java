package info.unterrainer.presserl.media;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Points the backend at an object store that is not there.
 */
public class MediaStoreDownProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("presserl.media.s3.endpoint", "http://127.0.0.1:1");
    }
}
