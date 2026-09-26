package info.unterrainer.presserl.auth;

import java.util.List;

/**
 * Response of {@code GET /api/client-config}.
 */
public record ClientConfigDto(Oidc oidc) {

    public record Oidc(String issuer, String clientId, List<String> scopes) {
    }
}
