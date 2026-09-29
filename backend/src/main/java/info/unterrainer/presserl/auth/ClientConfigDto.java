package info.unterrainer.presserl.auth;

import java.util.List;

/**
 * Response of {@code GET /api/client-config}; {@code spellCheck} tells the admin app whether to
 * offer the spell check at all.
 */
public record ClientConfigDto(Oidc oidc, boolean spellCheck) {

    public record Oidc(String issuer, String clientId, List<String> scopes) {
    }
}
