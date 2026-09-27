package info.unterrainer.presserl.account;

import java.util.List;

import info.unterrainer.presserl.auth.NewspaperRole;
import info.unterrainer.presserl.section.Newsroom;
import info.unterrainer.presserl.section.SectionRoleDto;

/**
 * One account as listed by {@code GET /api/accounts}; {@code lastName} is empty when unset,
 * {@code sectionRoles} are ordered by section position, {@code allowedActions} are those of the
 * requesting user ({@link AccountPolicy}).
 */
public record AccountDto(String id, String username, String firstName, String lastName, List<NewspaperRole> roles,
        List<SectionRoleDto> sectionRoles, boolean enabled, List<AccountAction> allowedActions) {

    public AccountDto withSectionRoles(List<SectionRoleDto> sectionRoles) {
        return new AccountDto(id, username, firstName, lastName, roles, List.copyOf(sectionRoles), enabled,
                allowedActions);
    }

    public AccountDto withRoles(List<NewspaperRole> roles) {
        return new AccountDto(id, username, firstName, lastName, List.copyOf(roles), sectionRoles, enabled,
                allowedActions);
    }

    public AccountDto withEnabled(boolean enabled) {
        return new AccountDto(id, username, firstName, lastName, roles, sectionRoles, enabled, allowedActions);
    }

    /**
     * This account with the actions {@code requester} may perform on it.
     */
    public AccountDto withAllowedActionsFor(Newsroom requester) {
        return new AccountDto(id, username, firstName, lastName, roles, sectionRoles, enabled,
                AccountPolicy.allowedActions(requester, this));
    }
}
