package info.unterrainer.presserl.section;

/**
 * A section role of the requesting user with the section's name, as returned by {@code GET /api/me}.
 */
public record NamedSectionRoleDto(long sectionId, String sectionName, SectionRole role) {
}
