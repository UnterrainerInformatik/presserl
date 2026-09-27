package info.unterrainer.presserl.article;

/**
 * Lifecycle state of an article. {@code SUBMITTED} marks a never-published article that waits for
 * approval; a published or offline article keeps its status while changes wait (see
 * {@link ArticleEntity#pendingLevel}).
 */
public enum ArticleStatus {
    DRAFT,
    SUBMITTED,
    PUBLISHED,
    OFFLINE
}
