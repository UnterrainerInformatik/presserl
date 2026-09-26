package info.unterrainer.presserl.article;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import info.unterrainer.presserl.auth.CurrentUser;
import info.unterrainer.presserl.auth.NewspaperRole;

/**
 * Decides which actions a user may perform on an article. Rules are checked in the order
 * role → ownership → state: a missing role or ownership is {@link Verdict#FORBIDDEN}, a state that
 * does not permit the action is {@link Verdict#CONFLICT}.
 * <table>
 * <tr><td>EDIT</td><td>user is author</td></tr>
 * <tr><td>DELETE</td><td>user is author and the article was never published</td></tr>
 * <tr><td>PUBLISH</td><td>user is author, holds PUBLISHER, and the article is not PUBLISHED or has
 * unpublished changes</td></tr>
 * <tr><td>TAKE_OFFLINE</td><td>user is author, EDITOR_IN_CHIEF or PUBLISHER, and the article is
 * PUBLISHED</td></tr>
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

    public static List<ArticleAction> allowedActions(CurrentUser user, ArticleEntity article, int latestRevision) {
        return Arrays.stream(ArticleAction.values())
                .filter(action -> verdict(action, user, article, latestRevision) == Verdict.ALLOWED)
                .toList();
    }

    public static Verdict verdict(ArticleAction action, CurrentUser user, ArticleEntity article, int latestRevision) {
        boolean author = Objects.equals(user.sub(), article.authorSub);
        return switch (action) {
            case EDIT -> author ? Verdict.ALLOWED : Verdict.FORBIDDEN;
            case DELETE -> !author ? Verdict.FORBIDDEN
                    : article.liveRevision != null ? Verdict.CONFLICT : Verdict.ALLOWED;
            case PUBLISH -> !user.has(NewspaperRole.PUBLISHER) || !author ? Verdict.FORBIDDEN
                    : article.status == ArticleStatus.PUBLISHED && !hasUnpublishedChanges(article, latestRevision)
                            ? Verdict.CONFLICT
                            : Verdict.ALLOWED;
            case TAKE_OFFLINE -> !author && !user.has(NewspaperRole.EDITOR_IN_CHIEF) && !user.has(NewspaperRole.PUBLISHER)
                    ? Verdict.FORBIDDEN
                    : article.status != ArticleStatus.PUBLISHED ? Verdict.CONFLICT : Verdict.ALLOWED;
        };
    }
}
