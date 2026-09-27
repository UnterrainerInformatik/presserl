package info.unterrainer.presserl.auth;

import java.util.List;

import info.unterrainer.presserl.section.NamedSectionRoleDto;

/**
 * Response of {@code GET /api/me}; {@code sectionRoles} are ordered by section position.
 */
public record MeDto(String username, String displayName, List<NewspaperRole> roles,
        List<NamedSectionRoleDto> sectionRoles) {
}
