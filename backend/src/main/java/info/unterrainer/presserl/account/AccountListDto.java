package info.unterrainer.presserl.account;

import java.util.List;

import info.unterrainer.presserl.auth.NewspaperRole;

/**
 * Response of {@code GET /api/accounts}: the roles the requesting user may assign, whether they may
 * assign the sectionless-reporter marker, and all accounts.
 */
public record AccountListDto(List<NewspaperRole> assignableRoles, boolean mayAssignSectionlessReporter,
        List<AccountDto> accounts) {
}
