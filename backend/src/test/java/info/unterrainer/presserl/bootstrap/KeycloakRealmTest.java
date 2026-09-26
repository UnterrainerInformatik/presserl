package info.unterrainer.presserl.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import info.unterrainer.presserl.bootstrap.KeycloakAdminProducer.KeycloakRealm;

class KeycloakRealmTest {

    @Test
    void derivesServerUrlAndRealmFromIssuer() {
        assertThat(KeycloakRealm.fromIssuer("https://auth.unterrainer.info/realms/presserl"))
                .isEqualTo(new KeycloakRealm("https://auth.unterrainer.info", "presserl"));
    }

    @Test
    void keepsAContextPathAndIgnoresATrailingSlash() {
        assertThat(KeycloakRealm.fromIssuer("https://example.org/auth/realms/zeitung/"))
                .isEqualTo(new KeycloakRealm("https://example.org/auth", "zeitung"));
    }

    @Test
    void rejectsAnIssuerWithoutRealm() {
        assertThatThrownBy(() -> KeycloakRealm.fromIssuer("https://auth.example.org"))
                .hasMessageContaining("PRESSERL_OIDC_ISSUER");
    }
}
