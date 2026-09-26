package info.unterrainer.presserl.article;

import static info.unterrainer.presserl.article.ArticleAction.DELETE;
import static info.unterrainer.presserl.article.ArticleAction.EDIT;
import static info.unterrainer.presserl.article.ArticleAction.PUBLISH;
import static info.unterrainer.presserl.article.ArticleAction.TAKE_OFFLINE;
import static info.unterrainer.presserl.article.ArticlePolicy.Verdict.ALLOWED;
import static info.unterrainer.presserl.article.ArticlePolicy.Verdict.CONFLICT;
import static info.unterrainer.presserl.article.ArticlePolicy.Verdict.FORBIDDEN;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import info.unterrainer.presserl.auth.CurrentUser;
import info.unterrainer.presserl.auth.NewspaperRole;

class ArticlePolicyTest {

    private static final CurrentUser PUBLISHER = user("pub", NewspaperRole.PUBLISHER);
    private static final CurrentUser OTHER_PUBLISHER = user("pub2", NewspaperRole.PUBLISHER);
    private static final CurrentUser CHIEF = user("chief", NewspaperRole.EDITOR_IN_CHIEF);
    private static final CurrentUser PUBLISHING_CHIEF = user("both", NewspaperRole.PUBLISHER,
            NewspaperRole.EDITOR_IN_CHIEF);
    private static final CurrentUser NOBODY = user("nobody");

    private static CurrentUser user(String sub, NewspaperRole... roles) {
        return new CurrentUser(sub, sub, sub, List.of(roles));
    }

    /**
     * An article by {@code author} in {@code status}, live revision {@code live} (or never published).
     */
    private static ArticleEntity article(CurrentUser author, ArticleStatus status, Integer live) {
        ArticleEntity article = new ArticleEntity();
        article.authorSub = author.sub();
        article.status = status;
        article.liveRevision = live;
        return article;
    }

    private static final ArticleEntity DRAFT = article(PUBLISHER, ArticleStatus.DRAFT, null);
    private static final ArticleEntity PUBLISHED = article(PUBLISHER, ArticleStatus.PUBLISHED, 1);
    private static final ArticleEntity OFFLINE = article(PUBLISHER, ArticleStatus.OFFLINE, 1);

    @Test
    void editOnlyByAuthorInEveryStatus() {
        for (ArticleEntity article : List.of(DRAFT, PUBLISHED, OFFLINE)) {
            assertThat(ArticlePolicy.verdict(EDIT, PUBLISHER, article, 1)).isEqualTo(ALLOWED);
            assertThat(ArticlePolicy.verdict(EDIT, OTHER_PUBLISHER, article, 1)).isEqualTo(FORBIDDEN);
            assertThat(ArticlePolicy.verdict(EDIT, CHIEF, article, 1)).isEqualTo(FORBIDDEN);
        }
        assertThat(ArticlePolicy.verdict(EDIT, CHIEF, article(CHIEF, ArticleStatus.DRAFT, null), 1)).isEqualTo(ALLOWED);
    }

    @Test
    void deleteOnlyByAuthorAndOnlyIfNeverPublished() {
        assertThat(ArticlePolicy.verdict(DELETE, PUBLISHER, DRAFT, 1)).isEqualTo(ALLOWED);
        assertThat(ArticlePolicy.verdict(DELETE, CHIEF, DRAFT, 1)).isEqualTo(FORBIDDEN);
        assertThat(ArticlePolicy.verdict(DELETE, PUBLISHER, PUBLISHED, 1)).isEqualTo(CONFLICT);
        assertThat(ArticlePolicy.verdict(DELETE, PUBLISHER, OFFLINE, 1)).isEqualTo(CONFLICT);
        assertThat(ArticlePolicy.verdict(DELETE, OTHER_PUBLISHER, OFFLINE, 1)).isEqualTo(FORBIDDEN);
    }

    @Test
    void publishOnlyByPublisherAuthor() {
        assertThat(ArticlePolicy.verdict(PUBLISH, PUBLISHER, DRAFT, 1)).isEqualTo(ALLOWED);
        assertThat(ArticlePolicy.verdict(PUBLISH, OTHER_PUBLISHER, DRAFT, 1)).isEqualTo(FORBIDDEN);
        assertThat(ArticlePolicy.verdict(PUBLISH, CHIEF, DRAFT, 1)).isEqualTo(FORBIDDEN);
        assertThat(ArticlePolicy.verdict(PUBLISH, CHIEF, article(CHIEF, ArticleStatus.DRAFT, null), 1))
                .isEqualTo(FORBIDDEN);
        assertThat(ArticlePolicy.verdict(PUBLISH, PUBLISHING_CHIEF,
                article(PUBLISHING_CHIEF, ArticleStatus.DRAFT, null), 1)).isEqualTo(ALLOWED);
    }

    @Test
    void publishDependsOnStateAndUnpublishedChanges() {
        assertThat(ArticlePolicy.verdict(PUBLISH, PUBLISHER, PUBLISHED, 1)).isEqualTo(CONFLICT);
        assertThat(ArticlePolicy.verdict(PUBLISH, PUBLISHER, PUBLISHED, 2)).isEqualTo(ALLOWED);
        assertThat(ArticlePolicy.verdict(PUBLISH, PUBLISHER, OFFLINE, 1)).isEqualTo(ALLOWED);
        assertThat(ArticlePolicy.verdict(PUBLISH, PUBLISHER, OFFLINE, 2)).isEqualTo(ALLOWED);
        assertThat(ArticlePolicy.verdict(PUBLISH, OTHER_PUBLISHER, PUBLISHED, 1)).isEqualTo(FORBIDDEN);
    }

    @Test
    void takeOfflineByAuthorChiefOrPublisherOnlyWhenPublished() {
        for (CurrentUser user : List.of(PUBLISHER, OTHER_PUBLISHER, CHIEF)) {
            assertThat(ArticlePolicy.verdict(TAKE_OFFLINE, user, PUBLISHED, 1)).isEqualTo(ALLOWED);
            assertThat(ArticlePolicy.verdict(TAKE_OFFLINE, user, DRAFT, 1)).isEqualTo(CONFLICT);
            assertThat(ArticlePolicy.verdict(TAKE_OFFLINE, user, OFFLINE, 1)).isEqualTo(CONFLICT);
        }
        assertThat(ArticlePolicy.verdict(TAKE_OFFLINE, NOBODY, PUBLISHED, 1)).isEqualTo(FORBIDDEN);
        assertThat(ArticlePolicy.verdict(TAKE_OFFLINE, NOBODY, article(NOBODY, ArticleStatus.PUBLISHED, 1), 1))
                .isEqualTo(ALLOWED);
    }

    @Test
    void submittedIsNeitherPublishedNorOffline() {
        ArticleEntity submitted = article(PUBLISHER, ArticleStatus.SUBMITTED, null);
        assertThat(ArticlePolicy.allowedActions(PUBLISHER, submitted, 1)).containsExactly(EDIT, PUBLISH, DELETE);
        assertThat(ArticlePolicy.allowedActions(CHIEF, submitted, 1)).isEmpty();
    }

    @Test
    void allowedActionsExamplesFromTheSpec() {
        assertThat(ArticlePolicy.allowedActions(PUBLISHER, DRAFT, 1)).containsExactly(EDIT, PUBLISH, DELETE);
        assertThat(ArticlePolicy.allowedActions(CHIEF, PUBLISHED, 1)).containsExactly(TAKE_OFFLINE);
        assertThat(ArticlePolicy.allowedActions(PUBLISHER, PUBLISHED, 1)).containsExactly(EDIT, TAKE_OFFLINE);
        assertThat(ArticlePolicy.allowedActions(PUBLISHER, PUBLISHED, 2)).containsExactly(EDIT, PUBLISH, TAKE_OFFLINE);
        assertThat(ArticlePolicy.allowedActions(PUBLISHER, OFFLINE, 1)).containsExactly(EDIT, PUBLISH);
        assertThat(ArticlePolicy.allowedActions(CHIEF, article(CHIEF, ArticleStatus.DRAFT, null), 1))
                .containsExactly(EDIT, DELETE);
    }

    @Test
    void unpublishedChanges() {
        assertThat(ArticlePolicy.hasUnpublishedChanges(DRAFT, 1)).isFalse();
        assertThat(ArticlePolicy.hasUnpublishedChanges(PUBLISHED, 1)).isFalse();
        assertThat(ArticlePolicy.hasUnpublishedChanges(PUBLISHED, 2)).isTrue();
        assertThat(ArticlePolicy.hasUnpublishedChanges(OFFLINE, 2)).isTrue();
    }
}
