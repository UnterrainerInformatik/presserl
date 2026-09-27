package info.unterrainer.presserl.article;

/**
 * Actions a user may perform on an article, in the order they are listed in {@code allowedActions}.
 */
public enum ArticleAction {
    EDIT,
    SUBMIT,
    PUBLISH,
    WITHDRAW,
    APPROVE,
    REJECT,
    TAKE_OFFLINE,
    UNLOCK,
    DELETE
}
