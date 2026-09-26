package info.unterrainer.presserl.article;

/**
 * Lifecycle state of an article. {@code SUBMITTED} is reserved for the approval chain and not
 * written yet.
 */
public enum ArticleStatus {
    DRAFT,
    SUBMITTED,
    PUBLISHED,
    OFFLINE
}
