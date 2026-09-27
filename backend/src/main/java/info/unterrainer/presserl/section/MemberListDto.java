package info.unterrainer.presserl.section;

import java.util.List;

/**
 * Response of {@code GET /api/sections/{id}/members}: the roles the requesting user may assign in
 * the section and its members, section editors first, then by username.
 */
public record MemberListDto(List<SectionRole> assignableRoles, List<MemberDto> members) {
}
