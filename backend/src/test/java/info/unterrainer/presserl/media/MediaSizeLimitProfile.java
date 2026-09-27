package info.unterrainer.presserl.media;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * A deployment that lowers {@code media.max-size} to 1M.
 */
public class MediaSizeLimitProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("presserl.media.max-size", "1M");
    }
}
