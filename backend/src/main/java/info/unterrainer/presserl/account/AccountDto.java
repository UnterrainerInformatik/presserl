package info.unterrainer.presserl.account;

import java.util.List;

import info.unterrainer.presserl.auth.NewspaperRole;
import info.unterrainer.presserl.section.Newsroom;
import info.unterrainer.presserl.section.SectionRoleDto;
import info.unterrainer.presserl.trust.TrustPolicy;
import info.unterrainer.presserl.trust.TrustScope;

/**
 * One account as listed by {@code GET /api/accounts}; {@code lastName} is empty when unset,
 * {@code sectionRoles} are ordered by section position, {@code sectionlessReporter} is the marker
 * that lets the account use the media endpoints without writing, {@code trusts} are the account's trust
 * entries, {@code trustScopes} the entries the requesting user may set or clear on it
 * ({@link TrustPolicy}) and {@code allowedActions} the actions of the requesting user
 * ({@link AccountPolicy}).
 */
public record AccountDto(String id, String username, String firstName, String lastName, List<NewspaperRole> roles,
        List<SectionRoleDto> sectionRoles, boolean sectionlessReporter, boolean enabled, List<TrustScope> trusts, List<TrustScope> trustScopes,
        List<AccountAction> allowedActions) {

    public AccountDto withSectionRoles(List<SectionRoleDto> sectionRoles) {
        return new AccountDto(id, username, firstName, lastName, roles, List.copyOf(sectionRoles), sectionlessReporter, enabled, trusts,
                trustScopes, allowedActions);
    }

    public AccountDto withRoles(List<NewspaperRole> roles) {
        return new AccountDto(id, username, firstName, lastName, List.copyOf(roles), sectionRoles, sectionlessReporter, enabled, trusts,
                trustScopes, allowedActions);
    }

    public AccountDto withSectionlessReporter(boolean sectionlessReporter) {
        return new AccountDto(id, username, firstName, lastName, roles, sectionRoles, sectionlessReporter, enabled,
                trusts, trustScopes, allowedActions);
    }

    public AccountDto withEnabled(boolean enabled) {
        return new AccountDto(id, username, firstName, lastName, roles, sectionRoles, sectionlessReporter, enabled, trusts, trustScopes,
                allowedActions);
    }

    /**
     * This account with its trust entries, ordered {@code PUBLISHER}, {@code EDITOR_IN_CHIEF}, then
     * {@code SECTION_EDITOR} by section position.
     */
    public AccountDto withTrusts(List<TrustScope> trusts) {
        return new AccountDto(id, username, firstName, lastName, roles, sectionRoles, sectionlessReporter, enabled, List.copyOf(trusts),
                trustScopes, allowedActions);
    }

    /**
     * This account with the trust scopes and actions of {@code requester}; its roles, section roles
     * and trust entries must be loaded.
     *
     * @param sectionIds the ids of all sections by position, to order the trust scopes
     */
    public AccountDto withAllowedActionsFor(Newsroom requester, List<Long> sectionIds) {
        return new AccountDto(id, username, firstName, lastName, roles, sectionRoles, sectionlessReporter, enabled, trusts,
                TrustPolicy.scopes(requester, this, sectionIds), AccountPolicy.allowedActions(requester, this));
    }
}
