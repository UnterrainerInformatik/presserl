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
import java.util.Map;

import org.junit.jupiter.api.Test;

import info.unterrainer.presserl.auth.CurrentUser;
import info.unterrainer.presserl.auth.NewspaperRole;
import info.unterrainer.presserl.section.Newsroom;
import info.unterrainer.presserl.section.SectionRole;

class ArticlePolicyTest {

    private static final long SPORT = 1L;
    private static final long KULTUR = 2L;

    private static final Newsroom PUBLISHER = user("pub", NewspaperRole.PUBLISHER);
    private static final Newsroom OTHER_PUBLISHER = user("pub2", NewspaperRole.PUBLISHER);
    private static final Newsroom CHIEF = user("chief", NewspaperRole.EDITOR_IN_CHIEF);
    private static final Newsroom PUBLISHING_CHIEF = user("both", NewspaperRole.PUBLISHER,
            NewspaperRole.EDITOR_IN_CHIEF);
    private static final Newsroom NOBODY = user("nobody");
    private static final Newsroom REPORTER = member("rep", Map.of(SPORT, SectionRole.REPORTER));
    private static final Newsroom OTHER_REPORTER = member("rep2", Map.of(SPORT, SectionRole.REPORTER));
    private static final Newsroom SECTION_EDITOR = member("sed", Map.of(SPORT, SectionRole.SECTION_EDITOR));

    private static Newsroom user(String sub, NewspaperRole... roles) {
        return new Newsroom(new CurrentUser(sub, sub, sub, List.of(roles)), Map.of());
    }

    private static Newsroom member(String sub, Map<Long, SectionRole> sectionRoles) {
        return new Newsroom(new CurrentUser(sub, sub, sub, List.of(NewspaperRole.READER)), sectionRoles);
    }

    /**
     * An article by {@code author} in {@code status} in {@code Sport}, live revision {@code live}
     * (or never published).
     */
    private static ArticleEntity article(Newsroom author, ArticleStatus status, Integer live) {
        return article(author, status, live, SPORT);
    }

    private static ArticleEntity article(Newsroom author, ArticleStatus status, Integer live, long section) {
        ArticleEntity article = new ArticleEntity();
        article.authorSub = author.user().sub();
        article.status = status;
        article.liveRevision = live;
        article.sectionId = section;
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
        for (Newsroom user : List.of(PUBLISHER, OTHER_PUBLISHER, CHIEF)) {
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
    void reporterOnOwnDraftInOwnSection() {
        assertThat(ArticlePolicy.allowedActions(REPORTER, article(REPORTER, ArticleStatus.DRAFT, null), 1))
                .containsExactly(EDIT, DELETE);
    }

    @Test
    void authorWithoutSectionRoleMayNotEditButMayTakeOffline() {
        ArticleEntity draftInKultur = article(REPORTER, ArticleStatus.DRAFT, null, KULTUR);
        assertThat(ArticlePolicy.verdict(EDIT, REPORTER, draftInKultur, 1)).isEqualTo(FORBIDDEN);
        assertThat(ArticlePolicy.verdict(DELETE, REPORTER, draftInKultur, 1)).isEqualTo(FORBIDDEN);
        assertThat(ArticlePolicy.allowedActions(REPORTER, draftInKultur, 1)).isEmpty();
        ArticleEntity publishedInKultur = article(REPORTER, ArticleStatus.PUBLISHED, 1, KULTUR);
        assertThat(ArticlePolicy.allowedActions(REPORTER, publishedInKultur, 1)).containsExactly(TAKE_OFFLINE);
    }

    @Test
    void sectionEditorTakesArticlesOfTheirSectionOffline() {
        assertThat(ArticlePolicy.allowedActions(SECTION_EDITOR, PUBLISHED, 1)).containsExactly(TAKE_OFFLINE);
        assertThat(ArticlePolicy.allowedActions(SECTION_EDITOR,
                article(PUBLISHER, ArticleStatus.PUBLISHED, 1, KULTUR), 1)).isEmpty();
        assertThat(ArticlePolicy.verdict(TAKE_OFFLINE, REPORTER, PUBLISHED, 1)).isEqualTo(FORBIDDEN);
    }

    @Test
    void visibility() {
        ArticleEntity reportersDraft = article(REPORTER, ArticleStatus.DRAFT, null);
        ArticleEntity reportersDraftInKultur = article(REPORTER, ArticleStatus.DRAFT, null, KULTUR);
        for (Newsroom administrator : List.of(PUBLISHER, CHIEF)) {
            assertThat(ArticlePolicy.visible(administrator, reportersDraft)).isTrue();
            assertThat(ArticlePolicy.visible(administrator, reportersDraftInKultur)).isTrue();
        }
        assertThat(ArticlePolicy.visible(REPORTER, reportersDraft)).isTrue();
        assertThat(ArticlePolicy.visible(REPORTER, reportersDraftInKultur)).isTrue();
        assertThat(ArticlePolicy.visible(SECTION_EDITOR, reportersDraft)).isTrue();
        assertThat(ArticlePolicy.visible(SECTION_EDITOR, reportersDraftInKultur)).isFalse();
        assertThat(ArticlePolicy.visible(OTHER_REPORTER, reportersDraft)).isFalse();
        assertThat(ArticlePolicy.visible(OTHER_REPORTER, PUBLISHED)).isFalse();
        assertThat(ArticlePolicy.visible(NOBODY, PUBLISHED)).isFalse();
    }

    @Test
    void unpublishedChanges() {
        assertThat(ArticlePolicy.hasUnpublishedChanges(DRAFT, 1)).isFalse();
        assertThat(ArticlePolicy.hasUnpublishedChanges(PUBLISHED, 1)).isFalse();
        assertThat(ArticlePolicy.hasUnpublishedChanges(PUBLISHED, 2)).isTrue();
        assertThat(ArticlePolicy.hasUnpublishedChanges(OFFLINE, 2)).isTrue();
    }
}
