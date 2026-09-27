package info.unterrainer.presserl.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.smallrye.config.EnvConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

class MediaConfigTest {

    private static MediaConfig config(Map<String, String> env) {
        Map<String, String> all = new HashMap<>(Map.of(
                "PRESSERL_MEDIA_S3_ACCESS_KEY", "access",
                "PRESSERL_MEDIA_S3_SECRET_KEY", "secret"));
        all.putAll(env);
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new EnvConfigSource(all, 300))
                .withMapping(MediaConfig.class)
                .build();
        return config.getConfigMapping(MediaConfig.class);
    }

    @Test
    void defaultsMatchTheReferenceDeployment() {
        MediaConfig config = config(Map.of());

        assertThat(config.maxSize()).isEqualTo("10M");
        assertThat(config.maxConcurrentProcessing()).isEqualTo(2);
        assertThat(config.s3().endpoint()).isEqualTo("http://rustfs:9000");
        assertThat(config.s3().region()).isEqualTo("us-east-1");
        assertThat(config.s3().bucket()).isEqualTo("presserl-media");
        assertThat(config.s3().accessKey()).isEqualTo("access");
    }

    @Test
    void credentialsAreMandatory() {
        assertThatThrownBy(() -> new SmallRyeConfigBuilder()
                .withSources(new EnvConfigSource(Map.of(), 300))
                .withMapping(MediaConfig.class)
                .build())
                .hasStackTraceContaining("presserl.media.s3.access-key")
                .hasStackTraceContaining("presserl.media.s3.secret-key");
    }

    @Test
    void maxSizeIsReadFromTheEnvironment() {
        assertThat(config(Map.of("PRESSERL_MEDIA_MAX_SIZE", "1M")).maxSize()).isEqualTo("1M");
    }

    @Test
    void maxBytesParsesMemorySizes() {
        assertThat(MediaLimits.maxBytes("10M")).isEqualTo(10L * 1024 * 1024);
        assertThat(MediaLimits.maxBytes("512K")).isEqualTo(512L * 1024);
        assertThat(MediaLimits.maxBytes("60M")).isEqualTo(60L * 1024 * 1024);
    }

    @Test
    void maxBytesAboveTheCeilingFails() {
        assertThatThrownBy(() -> MediaLimits.maxBytes("61M"))
                .hasMessageContaining("PRESSERL_MEDIA_MAX_SIZE")
                .hasMessageContaining("60M");
        assertThatThrownBy(() -> MediaLimits.maxBytes("1G")).hasMessageContaining("60M");
    }

    @Test
    void unreadableMaxSizeFails() {
        assertThatThrownBy(() -> MediaLimits.maxBytes("lots")).hasMessageContaining("PRESSERL_MEDIA_MAX_SIZE");
        assertThatThrownBy(() -> MediaLimits.maxBytes("0")).hasMessageContaining("between 1 and 60M");
    }
}
