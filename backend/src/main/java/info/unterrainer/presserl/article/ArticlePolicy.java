package info.unterrainer.presserl.article;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import info.unterrainer.presserl.auth.CurrentUser;
import info.unterrainer.presserl.auth.NewspaperRole;
import info.unterrainer.presserl.section.Newsroom;

/**
 * Decides which actions a user may perform on an article and whether they see it at all. Rules are
 * checked in the order role → ownership → state: a missing role, ownership or section access is
 * {@link Verdict#FORBIDDEN}, a state that does not permit the action is {@link Verdict#CONFLICT}.
 * "Pending" means a submission waits for an approval level ({@link ArticleEntity#pendingLevel}); the
 * chain is computed by {@link ApprovalChain} over the article's contributors from the
 * {@link Staffing} and the emergency-brake lock, which keeps {@code PUBLISHER} in it. A "corrector" is
 * a user other than the author whose approval level for the article lies above the author's level,
 * while {@link ArticleRules#corrections()} is on.
 * <table>
 * <tr><td>EDIT</td><td>user is author and may write in the article's section, nothing pending; or
 * user is corrector, the article is not DRAFT and, while pending, their level reaches the pending
 * level</td></tr>
 * <tr><td>SUBMIT</td><td>user is author and may write in the article's section, or a contributor who
 * is corrector of an article that is not DRAFT; the chain is not empty; nothing pending and the
 * article is not PUBLISHED without unpublished changes</td></tr>
 * <tr><td>PUBLISH</td><td>as SUBMIT, but the chain is empty</td></tr>
 * <tr><td>WITHDRAW</td><td>user is author; a submission is pending</td></tr>
 * <tr><td>APPROVE, REJECT</td><td>user is not the author and their approval level is at least the
 * pending level (any level while nothing is pending); a submission is pending</td></tr>
 * <tr><td>TAKE_OFFLINE</td><td>user is author, EDITOR_IN_CHIEF, PUBLISHER or section editor of the
 * article's section; the article is PUBLISHED (taken offline by a PUBLISHER, it becomes locked)</td></tr>
 * <tr><td>UNLOCK</td><td>user holds PUBLISHER; the article is locked</td></tr>
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
            Staffing staffing, ArticleRules rules) {
        return Arrays.stream(ArticleAction.values())
                .filter(action -> verdict(action, newsroom, article, latestRevision, staffing, rules) == Verdict.ALLOWED)
                .toList();
    }

    /**
     * @param staffing from {@link StaffingService#forArticles} for the article; only asked when the
     *                 user is the author or holds an approval level for the article
     */
    public static Verdict verdict(ArticleAction action, Newsroom newsroom, ArticleEntity article, int latestRevision,
            Staffing staffing, ArticleRules rules) {
        CurrentUser user = newsroom.user();
        boolean author = isAuthor(newsroom, article);
        boolean writingAuthor = author && mayWriteIn(newsroom, article);
        boolean pending = article.pendingLevel != null;
        boolean upToDate = article.status == ArticleStatus.PUBLISHED && !hasUnpublishedChanges(article, latestRevision);
        Verdict goOnline = pending || upToDate ? Verdict.CONFLICT : Verdict.ALLOWED;
        return switch (action) {
            case EDIT -> author ? !writingAuthor ? Verdict.FORBIDDEN : pending ? Verdict.CONFLICT : Verdict.ALLOWED
                    : correction(newsroom, article, staffing, rules);
            case SUBMIT -> !maySendOnline(newsroom, article, writingAuthor, staffing, rules)
                    || chainIsEmpty(article, staffing) ? Verdict.FORBIDDEN : goOnline;
            case PUBLISH -> !maySendOnline(newsroom, article, writingAuthor, staffing, rules)
                    || !chainIsEmpty(article, staffing) ? Verdict.FORBIDDEN : goOnline;
            case WITHDRAW -> !author ? Verdict.FORBIDDEN : !pending ? Verdict.CONFLICT : Verdict.ALLOWED;
            case APPROVE, REJECT -> author || !mayApprove(newsroom, article) ? Verdict.FORBIDDEN
                    : !pending ? Verdict.CONFLICT : Verdict.ALLOWED;
            case DELETE -> !writingAuthor ? Verdict.FORBIDDEN
                    : article.liveRevision != null ? Verdict.CONFLICT : Verdict.ALLOWED;
            case TAKE_OFFLINE -> !author && !user.has(NewspaperRole.EDITOR_IN_CHIEF) && !user.has(NewspaperRole.PUBLISHER)
                    && !isSectionEditor(newsroom, article)
                    ? Verdict.FORBIDDEN
                    : article.status != ArticleStatus.PUBLISHED ? Verdict.CONFLICT : Verdict.ALLOWED;
            case UNLOCK -> !user.has(NewspaperRole.PUBLISHER) ? Verdict.FORBIDDEN
                    : !article.locked ? Verdict.CONFLICT : Verdict.ALLOWED;
        };
    }

    /**
     * The EDIT verdict for a user other than the author: a corrector may save an article that is not
     * a draft, while pending only when their level reaches the pending level.
     */
    private static Verdict correction(Newsroom newsroom, ArticleEntity article, Staffing staffing, ArticleRules rules) {
        Optional<ApprovalLevel> level = correctorLevel(newsroom, article, staffing, rules);
        if (level.isEmpty()) {
            return Verdict.FORBIDDEN;
        }
        if (article.status == ArticleStatus.DRAFT) {
            return Verdict.CONFLICT;
        }
        return article.pendingLevel != null && level.get().compareTo(article.pendingLevel) < 0 ? Verdict.FORBIDDEN
                : Verdict.ALLOWED;
    }

    /**
     * The user's approval level for the article when corrections are on and it lies above the
     * author's level (from the author's current roles); empty otherwise.
     */
    private static Optional<ApprovalLevel> correctorLevel(Newsroom newsroom, ArticleEntity article, Staffing staffing,
            ArticleRules rules) {
        if (!rules.corrections() || isAuthor(newsroom, article)) {
            return Optional.empty();
        }
        return ApprovalChain.approverLevel(newsroom, article.sectionId)
                .filter(level -> staffing.levelOf(article.authorSub, article.sectionId)
                        .map(authorLevel -> level.compareTo(authorLevel) > 0).orElse(true));
    }

    /**
     * Whether the user may submit or publish the article in some state: its writing author, or a
     * contributor who is corrector of an article that is not a draft.
     */
    private static boolean maySendOnline(Newsroom newsroom, ArticleEntity article, boolean writingAuthor,
            Staffing staffing, ArticleRules rules) {
        return writingAuthor || article.status != ArticleStatus.DRAFT
                && correctorLevel(newsroom, article, staffing, rules).isPresent()
                && staffing.contributorsOf(article).contains(newsroom.user().sub());
    }

    /**
     * Whether no level applies to the article's contributors, so it is published directly.
     */
    private static boolean chainIsEmpty(ArticleEntity article, Staffing staffing) {
        return ApprovalChain.next(Optional.empty(), article.sectionId, staffing.contributorsOf(article), staffing,
                article.locked).isEmpty();
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
