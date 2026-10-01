package info.unterrainer.presserl.auth;

import java.time.Instant;
import java.util.List;

import info.unterrainer.presserl.section.NamedSectionRoleDto;

/**
 * Response of {@code GET /api/me}; {@code sectionRoles} are ordered by section position,
 * {@code allowedActions} in declaration order of {@link NewspaperAction}, {@code deletionRequestedAt}
 * is {@code null} without a pending deletion request.
 */
public record MeDto(String username, String displayName, List<NewspaperRole> roles,
        List<NamedSectionRoleDto> sectionRoles, boolean sectionlessReporter, List<NewspaperAction> allowedActions,
        Instant deletionRequestedAt) {
}
