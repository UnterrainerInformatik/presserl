package info.unterrainer.presserl.account;

/**
 * Actions a user may perform on an account, in the order they are listed in {@code allowedActions}.
 */
public enum AccountAction {
    EDIT_ROLES,
    RESET_PASSWORD,
    LOCK,
    UNLOCK,
    DELETE
}
