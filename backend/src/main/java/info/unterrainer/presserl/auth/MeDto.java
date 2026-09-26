package info.unterrainer.presserl.auth;

import java.util.List;

/**
 * Response of {@code GET /api/me}.
 */
public record MeDto(String username, String displayName, List<NewspaperRole> roles) {
}
