package info.unterrainer.presserl.bootstrap;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;

import info.unterrainer.presserl.auth.OidcConfig;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Builds the Keycloak Admin API client from the issuer URL and the backend client's secret
 * (client credentials grant of the backend's service account).
 */
public class KeycloakAdminProducer {

    static final String BACKEND_SECRET_VARIABLE = "PRESSERL_OIDC_BACKEND_SECRET";
    private static final String REALMS = "/realms/";

    @Inject
    OidcConfig oidc;

    @Produces
    @Singleton
    KeycloakRealm realm() {
        return KeycloakRealm.fromIssuer(oidc.issuer());
    }

    @Produces
    @Singleton
    Keycloak keycloak(KeycloakRealm realm) {
        String secret = oidc.backendSecret().filter(s -> !s.isBlank()).orElseThrow(() -> new IllegalStateException(
                BACKEND_SECRET_VARIABLE + " is not set: the secret of the presserl-backend client is mandatory"));
        return KeycloakBuilder.builder()
                .serverUrl(realm.serverUrl())
                .realm(realm.name())
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientId("presserl-backend")
                .clientSecret(secret)
                .build();
    }

    void close(@Disposes Keycloak keycloak) {
        keycloak.close();
    }

    /**
     * Server base URL and realm name, derived from an issuer {@code <server>/realms/<realm>}.
     */
    public record KeycloakRealm(String serverUrl, String name) {

        public static KeycloakRealm fromIssuer(String issuer) {
            String trimmed = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
            int index = trimmed.lastIndexOf(REALMS);
            if (index < 0 || index + REALMS.length() >= trimmed.length()) {
                throw new IllegalStateException(
                        "PRESSERL_OIDC_ISSUER must look like https://<keycloak>/realms/<realm>, but is " + issuer);
            }
            return new KeycloakRealm(trimmed.substring(0, index), trimmed.substring(index + REALMS.length()));
        }
    }
}
