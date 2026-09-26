package info.unterrainer.presserl.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The operator's realm template (deploy/keycloak) and the dev realm used by Dev Services must
 * describe the same realm; only hostnames, the dev secret and the dev-only clients differ.
 */
class RealmTemplateDriftTest {

    private static final Set<String> DEV_ONLY_CLIENTS = Set.of("presserl-http", "presserl-noaud");
    private static final List<String> SECURITY_FLAGS = List.of("sslRequired", "registrationAllowed",
            "loginWithEmailAllowed", "resetPasswordAllowed", "rememberMe", "verifyEmail", "editUsernameAllowed",
            "bruteForceProtected", "permanentLockout", "failureFactor", "accessTokenLifespan");
    private static final List<String> CLIENT_FLAGS = List.of("publicClient", "standardFlowEnabled",
            "directAccessGrantsEnabled", "implicitFlowEnabled", "serviceAccountsEnabled");

    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonNode template = read(Path.of("..", "deploy", "keycloak", "presserl-realm.json"));
    private final JsonNode dev = readResource("/dev/presserl-realm.json");

    @Test
    void sameRealmNameAndSecurityFlags() {
        assertThat(template.path("realm").asText()).isEqualTo("presserl").isEqualTo(dev.path("realm").asText());
        for (String flag : SECURITY_FLAGS) {
            assertThat(template.path(flag)).as(flag).isEqualTo(dev.path(flag));
        }
        assertThat(template.path("bruteForceProtected").asBoolean()).isTrue();
        assertThat(template.path("registrationAllowed").asBoolean()).isFalse();
        assertThat(template.path("loginWithEmailAllowed").asBoolean()).isFalse();
        assertThat(template.path("resetPasswordAllowed").asBoolean()).isFalse();
    }

    @Test
    void sameGroups() {
        assertThat(names(template.path("groups"), "name"))
                .containsExactlyInAnyOrder("publisher", "editor-in-chief", "reader")
                .isEqualTo(names(dev.path("groups"), "name"));
    }

    @Test
    void sameClientsFlagsAndMappers() {
        Map<String, JsonNode> templateClients = clients(template);
        Map<String, JsonNode> devClients = clients(dev);
        devClients.keySet().removeAll(DEV_ONLY_CLIENTS);
        assertThat(templateClients.keySet()).containsExactly("presserl-admin", "presserl-backend")
                .isEqualTo(devClients.keySet());

        templateClients.forEach((id, client) -> {
            JsonNode devClient = devClients.get(id);
            for (String flag : CLIENT_FLAGS) {
                assertThat(client.path(flag)).as(id + "." + flag).isEqualTo(devClient.path(flag));
            }
            assertThat(client.path("attributes").path("pkce.code.challenge.method"))
                    .as(id + " PKCE").isEqualTo(devClient.path("attributes").path("pkce.code.challenge.method"));
            assertThat(client.path("protocolMappers")).as(id + " mappers").isEqualTo(devClient.path("protocolMappers"));
        });
        assertThat(templateClients.get("presserl-admin").path("attributes").path("pkce.code.challenge.method").asText())
                .isEqualTo("S256");
        assertThat(templateClients.get("presserl-admin").path("directAccessGrantsEnabled").asBoolean()).isFalse();
    }

    @Test
    void templateCarriesNoSecretsAndNoLocalhost() {
        assertThat(clients(template).get("presserl-backend").has("secret")).isFalse();
        assertThat(template.toString()).doesNotContain("localhost");
    }

    @Test
    void sameServiceAccountRoles() {
        assertThat(serviceAccountRoles(template))
                .containsExactlyInAnyOrder("manage-users", "view-users", "query-users", "query-groups")
                .isEqualTo(serviceAccountRoles(dev));
    }

    @Test
    void sameUserProfile() {
        assertThat(template.path("components")).isEqualTo(dev.path("components"));
    }

    private Map<String, JsonNode> clients(JsonNode realm) {
        Map<String, JsonNode> clients = new TreeMap<>();
        realm.path("clients").forEach(c -> clients.put(c.path("clientId").asText(), c));
        return clients;
    }

    private List<String> serviceAccountRoles(JsonNode realm) {
        JsonNode user = StreamSupport.stream(realm.path("users").spliterator(), false)
                .filter(u -> "presserl-backend".equals(u.path("serviceAccountClientId").asText()))
                .findFirst().orElseThrow();
        return names(user.path("clientRoles").path("realm-management"), null);
    }

    private static List<String> names(JsonNode array, String field) {
        return StreamSupport.stream(array.spliterator(), false)
                .map(n -> field == null ? n.asText() : n.path(field).asText())
                .sorted().toList();
    }

    private JsonNode read(Path path) {
        try {
            return mapper.readTree(Files.readString(path));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode readResource(String name) {
        try (InputStream in = getClass().getResourceAsStream(name)) {
            return mapper.readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
