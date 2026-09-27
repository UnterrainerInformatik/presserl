package info.unterrainer.presserl.media;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Media settings from the deployment ({@code PRESSERL_MEDIA_*}). {@code max-size} is also reported
 * as the deployment-only newspaper setting {@code media.max-size}.
 */
@ConfigMapping(prefix = "presserl.media")
public interface MediaConfig {

    /**
     * Largest accepted upload as a Quarkus memory size ({@code 10M} = 10 MiB); at most
     * {@link MediaLimits#MAX_SIZE_CEILING}.
     */
    @WithName("max-size")
    @WithDefault("10M")
    String maxSize();

    /**
     * How many images are decoded and encoded at the same time; further uploads wait.
     */
    @WithName("max-concurrent-processing")
    @WithDefault("2")
    int maxConcurrentProcessing();

    S3 s3();

    interface S3 {

        @WithDefault("http://rustfs:9000")
        String endpoint();

        @WithDefault("us-east-1")
        String region();

        @WithDefault("presserl-media")
        String bucket();

        @WithName("access-key")
        String accessKey();

        @WithName("secret-key")
        String secretKey();
    }
}
