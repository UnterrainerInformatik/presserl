package info.unterrainer.presserl.account;

import java.util.List;

import info.unterrainer.presserl.auth.CurrentUser;
import info.unterrainer.presserl.auth.NewspaperRole;

/**
 * Which newspaper roles a user may assign to accounts: roles at or below the own level. The
 * account list reports it as {@code assignableRoles}; account creation enforces it.
 */
public final class RoleDelegation {

    private RoleDelegation() {
    }

    /**
     * The assignable roles in declaration order of {@link NewspaperRole}.
     */
    public static List<NewspaperRole> assignableBy(CurrentUser user) {
        if (user.has(NewspaperRole.PUBLISHER)) {
            return List.of(NewspaperRole.PUBLISHER, NewspaperRole.EDITOR_IN_CHIEF, NewspaperRole.READER);
        }
        if (user.has(NewspaperRole.EDITOR_IN_CHIEF)) {
            return List.of(NewspaperRole.EDITOR_IN_CHIEF, NewspaperRole.READER);
        }
        return List.of();
    }
}
