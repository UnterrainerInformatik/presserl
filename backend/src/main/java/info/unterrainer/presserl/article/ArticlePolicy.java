package info.unterrainer.presserl.article;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import info.unterrainer.presserl.auth.CurrentUser;
import info.unterrainer.presserl.auth.NewspaperRole;
import info.unterrainer.presserl.section.Newsroom;

/**
 * Decides which actions a user may perform on an article and whether they see it at all. Rules are
 * checked in the order role → ownership → state: a missing role, ownership or section access is
 * {@link Verdict#FORBIDDEN}, a state that does not permit the action is {@link Verdict#CONFLICT}.
 * <table>
 * <tr><td>EDIT</td><td>user is author and may write in the article's section</td></tr>
 * <tr><td>DELETE</td><td>user is author, may write in the article's section, and the article was
 * never published</td></tr>
 * <tr><td>PUBLISH</td><td>user is author, holds PUBLISHER, and the article is not PUBLISHED or has
 * unpublished changes</td></tr>
 * <tr><td>TAKE_OFFLINE</td><td>user is author, EDITOR_IN_CHIEF, PUBLISHER or section editor of the
 * article's section, and the article is PUBLISHED</td></tr>
 * </table>
 */
public final class ArticlePolicy {

    public enum Verdict {
        ALLOWED,
        FORBIDDEN,
        CONFLICT
    }

    private ArticlePolicy() {
    }

    /**
     * Whether the latest revision differs from the live one; {@code false} before the first publication.
     */
    public static boolean hasUnpublishedChanges(ArticleEntity article, int latestRevision) {
        return article.liveRevision != null && article.liveRevision != latestRevision;
    }

    /**
     * Administrators see every article, everyone else their own and, as section editor, all
     * articles of their sections.
     */
    public static boolean visible(Newsroom newsroom, ArticleEntity article) {
        return newsroom.isAdministrator() || isAuthor(newsroom, article) || isSectionEditor(newsroom, article);
    }

    public static List<ArticleAction> allowedActions(Newsroom newsroom, ArticleEntity article, int latestRevision) {
        return Arrays.stream(ArticleAction.values())
                .filter(action -> verdict(action, newsroom, article, latestRevision) == Verdict.ALLOWED)
                .toList();
    }

    public static Verdict verdict(ArticleAction action, Newsroom newsroom, ArticleEntity article, int latestRevision) {
        CurrentUser user = newsroom.user();
        boolean author = isAuthor(newsroom, article);
        boolean writingAuthor = author && mayWriteIn(newsroom, article);
        return switch (action) {
            case EDIT -> writingAuthor ? Verdict.ALLOWED : Verdict.FORBIDDEN;
            case DELETE -> !writingAuthor ? Verdict.FORBIDDEN
                    : article.liveRevision != null ? Verdict.CONFLICT : Verdict.ALLOWED;
            case PUBLISH -> !user.has(NewspaperRole.PUBLISHER) || !author ? Verdict.FORBIDDEN
                    : article.status == ArticleStatus.PUBLISHED && !hasUnpublishedChanges(article, latestRevision)
                            ? Verdict.CONFLICT
                            : Verdict.ALLOWED;
            case TAKE_OFFLINE -> !author && !user.has(NewspaperRole.EDITOR_IN_CHIEF) && !user.has(NewspaperRole.PUBLISHER)
                    && !isSectionEditor(newsroom, article)
                    ? Verdict.FORBIDDEN
                    : article.status != ArticleStatus.PUBLISHED ? Verdict.CONFLICT : Verdict.ALLOWED;
        };
    }

    private static boolean isAuthor(Newsroom newsroom, ArticleEntity article) {
        return Objects.equals(newsroom.user().sub(), article.authorSub);
    }

    /**
     * An article without a section (only rows the default-section bootstrap has not filed yet) is
     * writable by administrators only.
     */
    private static boolean mayWriteIn(Newsroom newsroom, ArticleEntity article) {
        return article.sectionId == null ? newsroom.isAdministrator() : newsroom.mayWriteIn(article.sectionId);
    }

    private static boolean isSectionEditor(Newsroom newsroom, ArticleEntity article) {
        return article.sectionId != null && newsroom.isSectionEditorOf(article.sectionId);
    }
}
