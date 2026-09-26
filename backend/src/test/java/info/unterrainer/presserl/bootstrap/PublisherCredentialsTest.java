package info.unterrainer.presserl.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URL;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.smallrye.config.EnvConfigSource;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;

class PublisherCredentialsTest {

    /**
     * The real application.properties under the given profile plus environment variables.
     */
    private static PublisherConfig config(String profile, Map<String, String> env) throws IOException {
        URL properties = PublisherCredentialsTest.class.getResource("/application.properties");
        return new SmallRyeConfigBuilder()
                .withProfile(profile)
                .withSources(new PropertiesConfigSource(properties, 250))
                .withSources(new EnvConfigSource(env, 300))
                .withMapping(PublisherConfig.class)
                .build()
                .getConfigMapping(PublisherConfig.class);
    }

    @Test
    void productionWithoutPasswordFailsNamingTheVariable() throws IOException {
        PublisherConfig config = config("prod", Map.of("PRESSERL_PUBLISHER_USERNAME", "papa"));

        assertThatThrownBy(() -> PublisherCredentials.from(config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PRESSERL_PUBLISHER_PASSWORD");
    }

    @Test
    void productionWithoutUsernameFailsNamingTheVariable() throws IOException {
        PublisherConfig config = config("prod", Map.of("PRESSERL_PUBLISHER_PASSWORD", "secret"));

        assertThatThrownBy(() -> PublisherCredentials.from(config))
                .hasMessageContaining("PRESSERL_PUBLISHER_USERNAME");
    }

    @Test
    void blankValueCountsAsMissing() throws IOException {
        PublisherConfig config = config("prod", Map.of(
                "PRESSERL_PUBLISHER_USERNAME", "papa", "PRESSERL_PUBLISHER_PASSWORD", "   "));

        assertThatThrownBy(() -> PublisherCredentials.from(config))
                .hasMessageContaining("PRESSERL_PUBLISHER_PASSWORD");
    }

    @Test
    void productionWithBothVariablesSucceeds() throws IOException {
        PublisherConfig config = config("prod", Map.of(
                "PRESSERL_PUBLISHER_USERNAME", "papa", "PRESSERL_PUBLISHER_PASSWORD", "secret"));

        assertThat(PublisherCredentials.from(config)).isEqualTo(new PublisherCredentials("papa", "secret"));
    }

    @Test
    void developmentHasDefaults() throws IOException {
        assertThat(PublisherCredentials.from(config("dev", Map.of())))
                .isEqualTo(new PublisherCredentials("publisher", "publisher"));
    }

    @Test
    void toStringHidesThePassword() {
        assertThat(new PublisherCredentials("papa", "secret").toString()).doesNotContain("secret");
    }
}
