package info.unterrainer.presserl.article;

import info.unterrainer.presserl.issue.IssueEntity;

/**
 * The one definition of "visible to readers": the article is {@code PUBLISHED} and belongs to an issue
 * that is published (live). Every reader surface and the {@code readerVisible} flag of the API use it,
 * so they cannot drift apart. A {@code PUBLISHED} article of a not-live issue or of no issue is treated
 * like a draft.
 */
public final class ReaderVisibility {

    /**
     * JPQL condition on an {@code ArticleEntity} aliased {@code a}; needs no parameter.
     */
    public static final String VISIBLE = "(a.status = info.unterrainer.presserl.article.ArticleStatus.PUBLISHED "
            + "and exists (select 1 from IssueEntity vi where vi.id = a.issueId and vi.published = true))";

    private ReaderVisibility() {
    }

    /**
     * @param issuePublished whether the article's issue is published, {@code null} for no issue
     */
    public static boolean visible(ArticleStatus status, Boolean issuePublished) {
        return status == ArticleStatus.PUBLISHED && Boolean.TRUE.equals(issuePublished);
    }

    /**
     * @param issue the article's issue, {@code null} for none
     */
    public static boolean visible(ArticleEntity article, IssueEntity issue) {
        return visible(article.status, issue == null ? null : issue.published);
    }
}
