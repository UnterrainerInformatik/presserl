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
 * "Pending" means a submission waits for an approval level ({@link ArticleEntity#pendingLevel}); the
 * chain is computed by {@link ApprovalChain} from the author's level and the {@link Staffing}.
 * <table>
 * <tr><td>EDIT</td><td>user is author and may write in the article's section; nothing pending</td></tr>
 * <tr><td>SUBMIT</td><td>user is author, may write in the article's section and their chain is not
 * empty; nothing pending and the article is not PUBLISHED without unpublished changes</td></tr>
 * <tr><td>PUBLISH</td><td>as SUBMIT, but the author's chain is empty</td></tr>
 * <tr><td>WITHDRAW</td><td>user is author; a submission is pending</td></tr>
 * <tr><td>APPROVE, REJECT</td><td>user is not the author and their approval level is at least the
 * pending level (any level while nothing is pending); a submission is pending</td></tr>
 * <tr><td>TAKE_OFFLINE</td><td>user is author, EDITOR_IN_CHIEF, PUBLISHER or section editor of the
 * article's section; the article is PUBLISHED</td></tr>
 * <tr><td>DELETE</td><td>user is author and may write in the article's section; the article was
 * never published</td></tr>
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

    public static List<ArticleAction> allowedActions(Newsroom newsroom, ArticleEntity article, int latestRevision,
            Staffing staffing) {
        return Arrays.stream(ArticleAction.values())
                .filter(action -> verdict(action, newsroom, article, latestRevision, staffing) == Verdict.ALLOWED)
                .toList();
    }

    /**
     * @param staffing may be {@link Staffing#NOT_NEEDED} unless the user is the author and does not
     *                 hold PUBLISHER (see {@link StaffingService#forArticles})
     */
    public static Verdict verdict(ArticleAction action, Newsroom newsroom, ArticleEntity article, int latestRevision,
            Staffing staffing) {
        CurrentUser user = newsroom.user();
        boolean author = isAuthor(newsroom, article);
        boolean writingAuthor = author && mayWriteIn(newsroom, article);
        boolean pending = article.pendingLevel != null;
        boolean upToDate = article.status == ArticleStatus.PUBLISHED && !hasUnpublishedChanges(article, latestRevision);
        Verdict goOnline = pending || upToDate ? Verdict.CONFLICT : Verdict.ALLOWED;
        return switch (action) {
            case EDIT -> !writingAuthor ? Verdict.FORBIDDEN : pending ? Verdict.CONFLICT : Verdict.ALLOWED;
            case SUBMIT -> !writingAuthor || chainIsEmpty(newsroom, article, staffing) ? Verdict.FORBIDDEN : goOnline;
            case PUBLISH -> !writingAuthor || !chainIsEmpty(newsroom, article, staffing) ? Verdict.FORBIDDEN : goOnline;
            case WITHDRAW -> !author ? Verdict.FORBIDDEN : !pending ? Verdict.CONFLICT : Verdict.ALLOWED;
            case APPROVE, REJECT -> author || !mayApprove(newsroom, article) ? Verdict.FORBIDDEN
                    : !pending ? Verdict.CONFLICT : Verdict.ALLOWED;
            case DELETE -> !writingAuthor ? Verdict.FORBIDDEN
                    : article.liveRevision != null ? Verdict.CONFLICT : Verdict.ALLOWED;
            case TAKE_OFFLINE -> !author && !user.has(NewspaperRole.EDITOR_IN_CHIEF) && !user.has(NewspaperRole.PUBLISHER)
                    && !isSectionEditor(newsroom, article)
                    ? Verdict.FORBIDDEN
                    : article.status != ArticleStatus.PUBLISHED ? Verdict.CONFLICT : Verdict.ALLOWED;
        };
    }

    /**
     * Whether no level applies to the author's (the user's) article, so it is published directly.
     */
    private static boolean chainIsEmpty(Newsroom author, ArticleEntity article, Staffing staffing) {
        return ApprovalChain.next(ApprovalChain.authorLevel(author, article.sectionId), article.sectionId,
                article.authorSub, staffing).isEmpty();
    }

    /**
     * Whether the user's approval level reaches the pending level; while nothing is pending, whether
     * they hold any approval level for the article's section.
     */
    private static boolean mayApprove(Newsroom newsroom, ArticleEntity article) {
        return ApprovalChain.approverLevel(newsroom, article.sectionId)
                .filter(level -> article.pendingLevel == null || level.compareTo(article.pendingLevel) >= 0)
                .isPresent();
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
