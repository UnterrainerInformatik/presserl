package info.unterrainer.presserl.bootstrap;

import java.util.Optional;

/**
 * Validated credentials of the first publisher.
 */
public record PublisherCredentials(String username, String password) {

    public static final String USERNAME_VARIABLE = "PRESSERL_PUBLISHER_USERNAME";
    public static final String PASSWORD_VARIABLE = "PRESSERL_PUBLISHER_PASSWORD";

    /**
     * @throws IllegalStateException naming the variable that is missing or blank
     */
    public static PublisherCredentials from(PublisherConfig config) {
        return new PublisherCredentials(
                require(config.username(), USERNAME_VARIABLE, "user name"),
                require(config.password(), PASSWORD_VARIABLE, "password"));
    }

    private static String require(Optional<String> value, String variable, String what) {
        return value.filter(v -> !v.isBlank()).orElseThrow(() -> new IllegalStateException(
                "%s is not set: the %s of the first publisher is mandatory (see deploy/.env.example)"
                        .formatted(variable, what)));
    }

    @Override
    public String toString() {
        return "PublisherCredentials[username=" + username + "]";
    }
}
