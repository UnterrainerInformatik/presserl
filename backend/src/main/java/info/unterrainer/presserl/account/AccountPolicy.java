package info.unterrainer.presserl.account;

import java.util.Arrays;
import java.util.List;

import info.unterrainer.presserl.auth.NewspaperRole;
import info.unterrainer.presserl.section.Newsroom;
import info.unterrainer.presserl.section.SectionRole;
import info.unterrainer.presserl.section.SectionRoleDto;

/**
 * Decides which actions a user may perform on an account. Nobody acts on their own account or on
 * a publisher's.
 * <table>
 * <tr><td>RESET_PASSWORD</td><td>user ranks above the account: publisher; editor-in-chief for
 * accounts not holding EDITOR_IN_CHIEF; section editor for accounts holding no newspaper role but
 * READER and at least one section role, each of them REPORTER in one of the user's sections</td></tr>
 * <tr><td>LOCK</td><td>user holds PUBLISHER and the account is enabled</td></tr>
 * <tr><td>UNLOCK</td><td>user holds PUBLISHER and the account is disabled</td></tr>
 * </table>
 */
public final class AccountPolicy {

    private AccountPolicy() {
    }

    /**
     * The actions {@code requester} may perform on {@code target} now: {@code LOCK} only for enabled
     * accounts, {@code UNLOCK} only for disabled ones.
     */
    public static List<AccountAction> allowedActions(Newsroom requester, AccountDto target) {
        return Arrays.stream(AccountAction.values())
                .filter(action -> permitted(action, requester, target))
                .filter(action -> action != AccountAction.LOCK || target.enabled())
                .filter(action -> action != AccountAction.UNLOCK || !target.enabled())
                .toList();
    }

    /**
     * Whether {@code requester} may perform {@code action} on {@code target} regardless of its
     * current state, so locking a locked account (or unlocking an enabled one) stays allowed.
     */
    public static boolean permitted(AccountAction action, Newsroom requester, AccountDto target) {
        if (target.id().equals(requester.user().sub()) || target.roles().contains(NewspaperRole.PUBLISHER)) {
            return false;
        }
        boolean publisher = requester.user().has(NewspaperRole.PUBLISHER);
        return switch (action) {
            case RESET_PASSWORD -> publisher || ranksAboveAsEditorInChief(requester, target)
                    || ranksAboveAsSectionEditor(requester, target);
            case LOCK, UNLOCK -> publisher;
        };
    }

    private static boolean ranksAboveAsEditorInChief(Newsroom requester, AccountDto target) {
        return requester.user().has(NewspaperRole.EDITOR_IN_CHIEF)
                && !target.roles().contains(NewspaperRole.EDITOR_IN_CHIEF);
    }

    private static boolean ranksAboveAsSectionEditor(Newsroom requester, AccountDto target) {
        List<SectionRoleDto> sectionRoles = target.sectionRoles();
        return target.roles().stream().allMatch(role -> role == NewspaperRole.READER)
                && !sectionRoles.isEmpty()
                && sectionRoles.stream().allMatch(role -> role.role() == SectionRole.REPORTER
                        && requester.isSectionEditorOf(role.sectionId()));
    }
}
