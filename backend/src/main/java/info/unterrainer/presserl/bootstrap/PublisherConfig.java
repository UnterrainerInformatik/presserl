package info.unterrainer.presserl.bootstrap;

import java.util.Optional;

import io.smallrye.config.ConfigMapping;

/**
 * The first publisher's credentials ({@code PRESSERL_PUBLISHER_USERNAME}/{@code _PASSWORD}).
 * Optional here so that {@link PublisherCredentials} can report a missing value by its variable name.
 */
@ConfigMapping(prefix = "presserl.publisher")
public interface PublisherConfig {

    Optional<String> username();

    Optional<String> password();
}
