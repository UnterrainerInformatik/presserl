package info.unterrainer.presserl.reader;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Deployment settings of the reader beyond the newspaper settings: the fork's theme directory and
 * whether reader preference cookies are {@code Secure} (production only, like the OIDC session cookie).
 */
@ConfigMapping(prefix = "presserl")
public interface ReaderConfig {

    Theme theme();

    Reader reader();

    interface Theme {

        /**
         * Served at {@code /theme/*}; the Quarkus container's working directory holds it.
         */
        @WithDefault("/deployments/theme")
        String dir();
    }

    interface Reader {

        @WithName("cookie-secure")
        @WithDefault("true")
        boolean cookieSecure();
    }
}
