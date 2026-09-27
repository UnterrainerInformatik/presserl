package info.unterrainer.presserl.account;

import java.util.List;

import info.unterrainer.presserl.auth.NewspaperRole;
import info.unterrainer.presserl.section.SectionRoleDto;

/**
 * One account as listed by {@code GET /api/accounts}; {@code lastName} is empty when unset,
 * {@code sectionRoles} are ordered by section position.
 */
public record AccountDto(String id, String username, String firstName, String lastName, List<NewspaperRole> roles,
        List<SectionRoleDto> sectionRoles, boolean enabled) {

    public AccountDto withSectionRoles(List<SectionRoleDto> sectionRoles) {
        return new AccountDto(id, username, firstName, lastName, roles, List.copyOf(sectionRoles), enabled);
    }
}
