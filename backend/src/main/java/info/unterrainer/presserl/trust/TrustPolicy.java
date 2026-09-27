package info.unterrainer.presserl.trust;

import java.util.List;
import java.util.Map;

import info.unterrainer.presserl.account.AccountDto;
import info.unterrainer.presserl.article.ApprovalLevel;
import info.unterrainer.presserl.auth.NewspaperRole;
import info.unterrainer.presserl.section.Newsroom;
import info.unterrainer.presserl.section.SectionRole;
import info.unterrainer.presserl.section.SectionRoleDto;

/**
 * Decides which trust entries a user may set or clear on an account. A user acts only for their own
 * highest approving level (their trust level), never on their own account. Locked accounts are
 * treated like enabled ones.
 * <table>
 * <tr><td>set</td><td>the account is below the level and writes there: PUBLISHER for accounts not
 * holding PUBLISHER that hold EDITOR_IN_CHIEF or a section role; EDITOR_IN_CHIEF for accounts holding
 * neither and a section role; SECTION_EDITOR in S for accounts holding neither that are REPORTER in
 * S</td></tr>
 * <tr><td>clear</td><td>an existing entry at the trust level, whoever set it and whether or not the
 * account is still below</td></tr>
 * </table>
 */
public final class TrustPolicy {

    private TrustPolicy() {
    }

    /**
     * The scopes the user acts for: {@code PUBLISHER} if they hold {@code PUBLISHER}, else
     * {@code EDITOR_IN_CHIEF} if they hold {@code EDITOR_IN_CHIEF}, else {@code SECTION_EDITOR} in each
     * of their sections as section editor; none otherwise.
     */
    public static List<TrustScope> trustLevels(Newsroom requester) {
        if (requester.user().has(NewspaperRole.PUBLISHER)) {
            return List.of(new TrustScope(ApprovalLevel.PUBLISHER, null));
        }
        if (requester.user().has(NewspaperRole.EDITOR_IN_CHIEF)) {
            return List.of(new TrustScope(ApprovalLevel.EDITOR_IN_CHIEF, null));
        }
        return requester.sectionRoles().entrySet().stream()
                .filter(entry -> entry.getValue() == SectionRole.SECTION_EDITOR)
                .map(Map.Entry::getKey)
                .sorted()
                .map(sectionId -> new TrustScope(ApprovalLevel.SECTION_EDITOR, sectionId))
                .toList();
    }

    /**
     * Whether {@code requester} may set trust {@code scope} on {@code target}.
     */
    public static boolean mayGrant(Newsroom requester, AccountDto target, TrustScope scope) {
        return !isOwn(requester, target) && trustLevels(requester).contains(scope) && below(target, scope);
    }

    /**
     * The entries {@code requester} may set or clear on {@code target} (whose {@code trusts} are
     * loaded), ordered {@code PUBLISHER}, {@code EDITOR_IN_CHIEF}, then {@code SECTION_EDITOR} by
     * section position.
     *
     * @param sectionIds the ids of all sections by position
     */
    public static List<TrustScope> scopes(Newsroom requester, AccountDto target, List<Long> sectionIds) {
        if (isOwn(requester, target)) {
            return List.of();
        }
        return trustLevels(requester).stream()
                .filter(scope -> below(target, scope) || target.trusts().contains(scope))
                .sorted(TrustScope.order(sectionIds))
                .toList();
    }

    private static boolean isOwn(Newsroom requester, AccountDto target) {
        return target.id().equals(requester.user().sub());
    }

    private static boolean below(AccountDto target, TrustScope scope) {
        List<NewspaperRole> roles = target.roles();
        List<SectionRoleDto> sectionRoles = target.sectionRoles();
        if (roles.contains(NewspaperRole.PUBLISHER)) {
            return false;
        }
        return switch (scope.level()) {
            case PUBLISHER -> roles.contains(NewspaperRole.EDITOR_IN_CHIEF) || !sectionRoles.isEmpty();
            case EDITOR_IN_CHIEF -> !roles.contains(NewspaperRole.EDITOR_IN_CHIEF) && !sectionRoles.isEmpty();
            case SECTION_EDITOR -> !roles.contains(NewspaperRole.EDITOR_IN_CHIEF)
                    && sectionRoles.contains(new SectionRoleDto(scope.sectionId(), SectionRole.REPORTER));
        };
    }
}
