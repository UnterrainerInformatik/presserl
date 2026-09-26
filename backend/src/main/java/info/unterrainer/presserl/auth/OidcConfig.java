package info.unterrainer.presserl.auth;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * OIDC wiring to the operator's Keycloak ({@code PRESSERL_OIDC_*}).
 */
@ConfigMapping(prefix = "presserl.oidc")
public interface OidcConfig {

    /**
     * Issuer URL of the realm, e.g. {@code https://auth.example.org/realms/presserl}.
     */
    String issuer();

    @WithDefault("presserl-admin")
    String adminClientId();

    @WithDefault("openid,profile")
    List<String> scopes();

    /**
     * Secret of the confidential backend client; used for the Keycloak Admin API.
     */
    Optional<String> backendSecret();

    /**
     * The issuer's origin ({@code scheme://host[:port]}), as needed in a CSP source list.
     */
    default String issuerOrigin() {
        URI uri = URI.create(issuer());
        return uri.getScheme() + "://" + uri.getRawAuthority();
    }
}
