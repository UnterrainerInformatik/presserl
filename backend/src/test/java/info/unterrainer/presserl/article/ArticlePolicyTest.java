package info.unterrainer.presserl.article;

import static info.unterrainer.presserl.article.ArticleAction.APPROVE;
import static info.unterrainer.presserl.article.ArticleAction.DELETE;
import static info.unterrainer.presserl.article.ArticleAction.EDIT;
import static info.unterrainer.presserl.article.ArticleAction.PUBLISH;
import static info.unterrainer.presserl.article.ArticleAction.REJECT;
import static info.unterrainer.presserl.article.ArticleAction.SUBMIT;
import static info.unterrainer.presserl.article.ArticleAction.TAKE_OFFLINE;
import static info.unterrainer.presserl.article.ArticleAction.UNLOCK;
import static info.unterrainer.presserl.article.ArticleAction.WITHDRAW;
import static info.unterrainer.presserl.article.ArticlePolicy.Verdict.ALLOWED;
import static info.unterrainer.presserl.article.ArticlePolicy.Verdict.CONFLICT;
import static info.unterrainer.presserl.article.ArticlePolicy.Verdict.FORBIDDEN;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import info.unterrainer.presserl.article.ArticlePolicy.Verdict;
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
    private static final Newsroom OTHER_CHIEF = user("chief2", NewspaperRole.EDITOR_IN_CHIEF);
    private static final Newsroom PUBLISHING_CHIEF = user("both", NewspaperRole.PUBLISHER,
            NewspaperRole.EDITOR_IN_CHIEF);
    private static final Newsroom NOBODY = user("nobody");
    private static final Newsroom REPORTER = member("rep", Map.of(SPORT, SectionRole.REPORTER));
    private static final Newsroom OTHER_REPORTER = member("rep2", Map.of(SPORT, SectionRole.REPORTER));
    private static final Newsroom SECTION_EDITOR = member("sed", Map.of(SPORT, SectionRole.SECTION_EDITOR));

    /**
     * The users above hold their roles; Kultur has no section editor.
     */
    private static final Staffing STAFFING = new Staffing(Map.of(SPORT, Set.of("sed")),
            Set.of("chief", "chief2", "both"), Set.of("pub", "pub2", "both"));

    private static final ArticleRules CORRECTIONS = new ArticleRules(true);
    private static final ArticleRules NO_CORRECTIONS = new ArticleRules(false);

    /**
     * {@link #STAFFING} in which {@code article} (given id 42) has exactly {@code contributors}.
     */
    private static Staffing contributed(ArticleEntity article, Newsroom... contributors) {
        article.id = 42L;
        return new Staffing(STAFFING.sectionEditors(), STAFFING.editorsInChief(), STAFFING.publishers(), Map.of(),
                Map.of(42L, Arrays.stream(contributors).map(c -> c.user().sub()).collect(Collectors.toSet())));
    }

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

    /**
     * An offline article by {@code author} in Sport with live revision 1, locked by the emergency brake.
     */
    private static ArticleEntity locked(Newsroom author) {
        ArticleEntity article = article(author, ArticleStatus.OFFLINE, 1);
        article.locked = true;
        return article;
    }

    private static ArticleEntity waiting(ArticleEntity article, ApprovalLevel level) {
        article.pendingLevel = level;
        return article;
    }

    /**
     * A never-published article by {@code author} in Sport that waits for {@code level}.
     */
    private static ArticleEntity submitted(Newsroom author, ApprovalLevel level) {
        return waiting(article(author, ArticleStatus.SUBMITTED, null), level);
    }

    private static Verdict verdict(ArticleAction action, Newsroom user, ArticleEntity article, int latest) {
        return ArticlePolicy.verdict(action, user, article, latest, STAFFING, CORRECTIONS);
    }

    private static Verdict verdict(ArticleAction action, Newsroom user, ArticleEntity article) {
        return verdict(action, user, article, 1);
    }

    private static List<ArticleAction> allowed(Newsroom user, ArticleEntity article, int latest) {
        return ArticlePolicy.allowedActions(user, article, latest, STAFFING, CORRECTIONS);
    }

    private static List<ArticleAction> allowed(Newsroom user, ArticleEntity article) {
        return allowed(user, article, 1);
    }

    private static final ArticleEntity DRAFT = article(PUBLISHER, ArticleStatus.DRAFT, null);
    private static final ArticleEntity PUBLISHED = article(PUBLISHER, ArticleStatus.PUBLISHED, 1);
    private static final ArticleEntity OFFLINE = article(PUBLISHER, ArticleStatus.OFFLINE, 1);
    private static final ArticleEntity REPORTERS_DRAFT = article(REPORTER, ArticleStatus.DRAFT, null);

    // --- EDIT, DELETE

    @Test
    void editOnlyByAuthorInEveryStatus() {
        for (ArticleEntity article : List.of(DRAFT, PUBLISHED, OFFLINE)) {
            assertThat(verdict(EDIT, PUBLISHER, article)).isEqualTo(ALLOWED);
            assertThat(verdict(EDIT, OTHER_PUBLISHER, article)).isEqualTo(FORBIDDEN);
            assertThat(verdict(EDIT, CHIEF, article)).isEqualTo(FORBIDDEN);
        }
        assertThat(verdict(EDIT, CHIEF, article(CHIEF, ArticleStatus.DRAFT, null))).isEqualTo(ALLOWED);
    }

    @Test
    void editConflictsWhilePending() {
        assertThat(verdict(EDIT, REPORTER, submitted(REPORTER, ApprovalLevel.SECTION_EDITOR))).isEqualTo(CONFLICT);
        assertThat(verdict(EDIT, CHIEF, waiting(article(CHIEF, ArticleStatus.PUBLISHED, 1), ApprovalLevel.PUBLISHER), 2))
                .isEqualTo(CONFLICT);
        // the section editor corrects instead (see "corrections")
        assertThat(verdict(EDIT, SECTION_EDITOR, submitted(REPORTER, ApprovalLevel.SECTION_EDITOR)))
                .isEqualTo(ALLOWED);
    }

    @Test
    void deleteOnlyByAuthorAndOnlyIfNeverPublished() {
        assertThat(verdict(DELETE, PUBLISHER, DRAFT)).isEqualTo(ALLOWED);
        assertThat(verdict(DELETE, CHIEF, DRAFT)).isEqualTo(FORBIDDEN);
        assertThat(verdict(DELETE, PUBLISHER, PUBLISHED)).isEqualTo(CONFLICT);
        assertThat(verdict(DELETE, PUBLISHER, OFFLINE)).isEqualTo(CONFLICT);
        assertThat(verdict(DELETE, OTHER_PUBLISHER, OFFLINE)).isEqualTo(FORBIDDEN);
    }

    @Test
    void deleteWorksWhilePending() {
        assertThat(verdict(DELETE, REPORTER, submitted(REPORTER, ApprovalLevel.SECTION_EDITOR))).isEqualTo(ALLOWED);
        assertThat(verdict(DELETE, SECTION_EDITOR, submitted(REPORTER, ApprovalLevel.SECTION_EDITOR)))
                .isEqualTo(FORBIDDEN);
    }

    // --- PUBLISH

    @Test
    void publishByAuthorWithEmptyChain() {
        assertThat(verdict(PUBLISH, PUBLISHER, DRAFT)).isEqualTo(ALLOWED);
        assertThat(verdict(PUBLISH, OTHER_PUBLISHER, DRAFT)).isEqualTo(FORBIDDEN);
        assertThat(verdict(PUBLISH, CHIEF, DRAFT)).isEqualTo(FORBIDDEN);
        assertThat(verdict(PUBLISH, PUBLISHING_CHIEF, article(PUBLISHING_CHIEF, ArticleStatus.DRAFT, null)))
                .isEqualTo(ALLOWED);
    }

    @Test
    void publishForbiddenWhileALevelApplies() {
        assertThat(verdict(PUBLISH, CHIEF, article(CHIEF, ArticleStatus.DRAFT, null))).isEqualTo(FORBIDDEN);
        assertThat(verdict(PUBLISH, REPORTER, REPORTERS_DRAFT)).isEqualTo(FORBIDDEN);
        assertThat(verdict(PUBLISH, SECTION_EDITOR, article(SECTION_EDITOR, ArticleStatus.DRAFT, null)))
                .isEqualTo(FORBIDDEN);
    }

    @Test
    void publishAllowedWhenNoOtherAccountStaffsALevel() {
        Staffing chiefAlone = new Staffing(Map.of(), Set.of("chief"), Set.of());
        assertThat(ArticlePolicy.verdict(PUBLISH, CHIEF, article(CHIEF, ArticleStatus.DRAFT, null), 1, chiefAlone,
                CORRECTIONS)).isEqualTo(ALLOWED);
        assertThat(ArticlePolicy.verdict(SUBMIT, CHIEF, article(CHIEF, ArticleStatus.DRAFT, null), 1, chiefAlone,
                CORRECTIONS)).isEqualTo(FORBIDDEN);
    }

    @Test
    void publishDependsOnStateAndUnpublishedChanges() {
        assertThat(verdict(PUBLISH, PUBLISHER, PUBLISHED, 1)).isEqualTo(CONFLICT);
        assertThat(verdict(PUBLISH, PUBLISHER, PUBLISHED, 2)).isEqualTo(ALLOWED);
        assertThat(verdict(PUBLISH, PUBLISHER, OFFLINE, 1)).isEqualTo(ALLOWED);
        assertThat(verdict(PUBLISH, PUBLISHER, OFFLINE, 2)).isEqualTo(ALLOWED);
        assertThat(verdict(PUBLISH, OTHER_PUBLISHER, PUBLISHED, 1)).isEqualTo(FORBIDDEN);
    }

    @Test
    void publishConflictsWhilePending() {
        // chief's article waits for PUBLISHER; chief was then promoted to publisher
        ArticleEntity promoted = waiting(article(PUBLISHING_CHIEF, ArticleStatus.SUBMITTED, null),
                ApprovalLevel.PUBLISHER);
        assertThat(verdict(PUBLISH, PUBLISHING_CHIEF, promoted)).isEqualTo(CONFLICT);
    }

    // --- SUBMIT

    @Test
    void submitByAuthorWithNonEmptyChain() {
        assertThat(verdict(SUBMIT, REPORTER, REPORTERS_DRAFT)).isEqualTo(ALLOWED);
        assertThat(verdict(SUBMIT, CHIEF, article(CHIEF, ArticleStatus.DRAFT, null))).isEqualTo(ALLOWED);
        assertThat(verdict(SUBMIT, SECTION_EDITOR, article(SECTION_EDITOR, ArticleStatus.DRAFT, null)))
                .isEqualTo(ALLOWED);
    }

    @Test
    void submitForbiddenForOthersAndForEmptyChains() {
        assertThat(verdict(SUBMIT, PUBLISHER, DRAFT)).isEqualTo(FORBIDDEN);
        assertThat(verdict(SUBMIT, SECTION_EDITOR, REPORTERS_DRAFT)).isEqualTo(FORBIDDEN);
        assertThat(verdict(SUBMIT, OTHER_REPORTER, REPORTERS_DRAFT)).isEqualTo(FORBIDDEN);
        assertThat(verdict(SUBMIT, REPORTER, article(REPORTER, ArticleStatus.DRAFT, null, KULTUR)))
                .isEqualTo(FORBIDDEN);
    }

    @Test
    void submitDependsOnStateAndPending() {
        ArticleEntity published = article(CHIEF, ArticleStatus.PUBLISHED, 1);
        assertThat(verdict(SUBMIT, CHIEF, published, 1)).isEqualTo(CONFLICT);
        assertThat(verdict(SUBMIT, CHIEF, published, 2)).isEqualTo(ALLOWED);
        assertThat(verdict(SUBMIT, CHIEF, article(CHIEF, ArticleStatus.OFFLINE, 1), 1)).isEqualTo(ALLOWED);
        assertThat(verdict(SUBMIT, REPORTER, submitted(REPORTER, ApprovalLevel.SECTION_EDITOR)))
                .isEqualTo(CONFLICT);
        assertThat(verdict(SUBMIT, CHIEF, waiting(article(CHIEF, ArticleStatus.OFFLINE, 1), ApprovalLevel.PUBLISHER)))
                .isEqualTo(CONFLICT);
    }

    // --- WITHDRAW

    @Test
    void withdrawOnlyByAuthorWhilePending() {
        ArticleEntity submitted = submitted(REPORTER, ApprovalLevel.SECTION_EDITOR);
        assertThat(verdict(WITHDRAW, REPORTER, submitted)).isEqualTo(ALLOWED);
        assertThat(verdict(WITHDRAW, SECTION_EDITOR, submitted)).isEqualTo(FORBIDDEN);
        assertThat(verdict(WITHDRAW, PUBLISHER, submitted)).isEqualTo(FORBIDDEN);
        assertThat(verdict(WITHDRAW, REPORTER, REPORTERS_DRAFT)).isEqualTo(CONFLICT);
    }

    @Test
    void withdrawAfterLosingTheSectionRole() {
        ArticleEntity inKultur = waiting(article(REPORTER, ArticleStatus.SUBMITTED, null, KULTUR),
                ApprovalLevel.EDITOR_IN_CHIEF);
        assertThat(verdict(WITHDRAW, REPORTER, inKultur)).isEqualTo(ALLOWED);
    }

    // --- APPROVE, REJECT

    @Test
    void approveAndRejectFromThePendingLevelUp() {
        for (ArticleAction action : List.of(APPROVE, REJECT)) {
            ArticleEntity forSectionEditor = submitted(REPORTER, ApprovalLevel.SECTION_EDITOR);
            for (Newsroom approver : List.of(SECTION_EDITOR, CHIEF, PUBLISHER)) {
                assertThat(verdict(action, approver, forSectionEditor)).isEqualTo(ALLOWED);
            }
            ArticleEntity forChief = submitted(REPORTER, ApprovalLevel.EDITOR_IN_CHIEF);
            assertThat(verdict(action, SECTION_EDITOR, forChief)).isEqualTo(FORBIDDEN);
            assertThat(verdict(action, CHIEF, forChief)).isEqualTo(ALLOWED);
            ArticleEntity forPublisher = submitted(REPORTER, ApprovalLevel.PUBLISHER);
            assertThat(verdict(action, CHIEF, forPublisher)).isEqualTo(FORBIDDEN);
            assertThat(verdict(action, OTHER_PUBLISHER, forPublisher)).isEqualTo(ALLOWED);
        }
    }

    @Test
    void theAuthorNeverApproves() {
        ArticleEntity promoted = waiting(article(PUBLISHING_CHIEF, ArticleStatus.SUBMITTED, null),
                ApprovalLevel.PUBLISHER);
        assertThat(verdict(APPROVE, PUBLISHING_CHIEF, promoted)).isEqualTo(FORBIDDEN);
        assertThat(verdict(REJECT, PUBLISHING_CHIEF, promoted)).isEqualTo(FORBIDDEN);
    }

    @Test
    void usersWithoutApprovalLevelAreForbidden() {
        ArticleEntity submitted = submitted(REPORTER, ApprovalLevel.SECTION_EDITOR);
        assertThat(verdict(APPROVE, OTHER_REPORTER, submitted)).isEqualTo(FORBIDDEN);
        ArticleEntity inKultur = waiting(article(REPORTER, ArticleStatus.SUBMITTED, null, KULTUR),
                ApprovalLevel.SECTION_EDITOR);
        assertThat(verdict(APPROVE, SECTION_EDITOR, inKultur)).isEqualTo(FORBIDDEN);
    }

    @Test
    void approveConflictsWhenNothingIsPending() {
        assertThat(verdict(APPROVE, OTHER_PUBLISHER, DRAFT)).isEqualTo(CONFLICT);
        assertThat(verdict(REJECT, SECTION_EDITOR, REPORTERS_DRAFT)).isEqualTo(CONFLICT);
        assertThat(verdict(APPROVE, OTHER_REPORTER, REPORTERS_DRAFT)).isEqualTo(FORBIDDEN);
        assertThat(verdict(APPROVE, PUBLISHER, DRAFT)).isEqualTo(FORBIDDEN);
    }

    // --- TAKE_OFFLINE

    @Test
    void takeOfflineByAuthorChiefOrPublisherOnlyWhenPublished() {
        for (Newsroom user : List.of(PUBLISHER, OTHER_PUBLISHER, CHIEF)) {
            assertThat(verdict(TAKE_OFFLINE, user, PUBLISHED)).isEqualTo(ALLOWED);
            assertThat(verdict(TAKE_OFFLINE, user, DRAFT)).isEqualTo(CONFLICT);
            assertThat(verdict(TAKE_OFFLINE, user, OFFLINE)).isEqualTo(CONFLICT);
        }
        assertThat(verdict(TAKE_OFFLINE, NOBODY, PUBLISHED)).isEqualTo(FORBIDDEN);
        assertThat(verdict(TAKE_OFFLINE, NOBODY, article(NOBODY, ArticleStatus.PUBLISHED, 1))).isEqualTo(ALLOWED);
    }

    @Test
    void takeOfflineWhileChangesWait() {
        ArticleEntity waiting = waiting(article(CHIEF, ArticleStatus.PUBLISHED, 1), ApprovalLevel.PUBLISHER);
        assertThat(verdict(TAKE_OFFLINE, CHIEF, waiting, 2)).isEqualTo(ALLOWED);
    }

    @Test
    void sectionEditorTakesArticlesOfTheirSectionOffline() {
        assertThat(allowed(SECTION_EDITOR, PUBLISHED)).containsExactly(TAKE_OFFLINE);
        assertThat(allowed(SECTION_EDITOR, article(PUBLISHER, ArticleStatus.PUBLISHED, 1, KULTUR))).isEmpty();
        assertThat(verdict(TAKE_OFFLINE, REPORTER, PUBLISHED)).isEqualTo(FORBIDDEN);
    }

    // --- UNLOCK and the emergency brake

    @Test
    void unlockOnlyByPublishersOnlyWhenLocked() {
        assertThat(verdict(UNLOCK, PUBLISHER, locked(CHIEF))).isEqualTo(ALLOWED);
        assertThat(verdict(UNLOCK, PUBLISHING_CHIEF, locked(CHIEF))).isEqualTo(ALLOWED);
        assertThat(verdict(UNLOCK, PUBLISHER, article(CHIEF, ArticleStatus.OFFLINE, 1))).isEqualTo(CONFLICT);
        assertThat(verdict(UNLOCK, CHIEF, locked(CHIEF))).isEqualTo(FORBIDDEN);
        assertThat(verdict(UNLOCK, SECTION_EDITOR, locked(REPORTER))).isEqualTo(FORBIDDEN);
        assertThat(verdict(UNLOCK, REPORTER, locked(REPORTER))).isEqualTo(FORBIDDEN);
    }

    @Test
    void lockedArticleOfAnAuthorWithEmptyChainMustBeSubmitted() {
        // no other account holds a level: unlocked the reporter publishes directly, locked they submit
        Staffing nobodyElse = new Staffing(Map.of(), Set.of(), Set.of());
        ArticleEntity offline = article(REPORTER, ArticleStatus.OFFLINE, 1);
        assertThat(ArticlePolicy.verdict(PUBLISH, REPORTER, offline, 1, nobodyElse, CORRECTIONS)).isEqualTo(ALLOWED);
        assertThat(ArticlePolicy.verdict(PUBLISH, REPORTER, locked(REPORTER), 1, nobodyElse, CORRECTIONS))
                .isEqualTo(FORBIDDEN);
        assertThat(ArticlePolicy.verdict(SUBMIT, REPORTER, locked(REPORTER), 1, nobodyElse, CORRECTIONS))
                .isEqualTo(ALLOWED);
    }

    @Test
    void publisherPublishesTheirOwnLockedArticle() {
        assertThat(allowed(PUBLISHER, locked(PUBLISHER))).containsExactly(EDIT, PUBLISH, UNLOCK);
    }

    @Test
    void allowedActionsOnALockedArticle() {
        assertThat(allowed(PUBLISHER, locked(CHIEF))).containsExactly(EDIT, UNLOCK);
        assertThat(allowed(CHIEF, locked(CHIEF))).containsExactly(EDIT, SUBMIT);
        assertThat(allowed(PUBLISHER, waiting(locked(CHIEF), ApprovalLevel.PUBLISHER)))
                .containsExactly(EDIT, APPROVE, REJECT, UNLOCK);
    }

    // --- allowedActions

    @Test
    void allowedActionsExamplesFromTheSpec() {
        assertThat(allowed(PUBLISHER, DRAFT)).containsExactly(EDIT, PUBLISH, DELETE);
        assertThat(allowed(CHIEF, PUBLISHED)).containsExactly(TAKE_OFFLINE);
        assertThat(allowed(PUBLISHER, PUBLISHED, 1)).containsExactly(EDIT, TAKE_OFFLINE);
        assertThat(allowed(PUBLISHER, PUBLISHED, 2)).containsExactly(EDIT, PUBLISH, TAKE_OFFLINE);
        assertThat(allowed(PUBLISHER, OFFLINE)).containsExactly(EDIT, PUBLISH);
        assertThat(allowed(CHIEF, article(CHIEF, ArticleStatus.DRAFT, null))).containsExactly(EDIT, SUBMIT, DELETE);
    }

    @Test
    void reporterOnOwnDraft() {
        assertThat(allowed(REPORTER, REPORTERS_DRAFT)).containsExactly(EDIT, SUBMIT, DELETE);
    }

    @Test
    void reporterOnOwnSubmittedDraft() {
        assertThat(allowed(REPORTER, submitted(REPORTER, ApprovalLevel.SECTION_EDITOR)))
                .containsExactly(WITHDRAW, DELETE);
    }

    @Test
    void sectionEditorOnAWaitingArticleOfTheirSection() {
        assertThat(allowed(SECTION_EDITOR, submitted(REPORTER, ApprovalLevel.SECTION_EDITOR)))
                .containsExactly(EDIT, APPROVE, REJECT);
    }

    @Test
    void foreignArticlesNeedNoStaffingWhileCorrectionsAreOff() {
        assertThat(ArticlePolicy.allowedActions(SECTION_EDITOR, submitted(REPORTER, ApprovalLevel.SECTION_EDITOR), 1,
                Staffing.NOT_NEEDED, NO_CORRECTIONS)).containsExactly(APPROVE, REJECT);
        assertThat(ArticlePolicy.allowedActions(CHIEF, PUBLISHED, 1, Staffing.NOT_NEEDED, NO_CORRECTIONS))
                .containsExactly(TAKE_OFFLINE);
    }

    // --- corrections by higher levels

    @Test
    void higherLevelsCorrectPendingPublishedAndOfflineArticles() {
        assertThat(verdict(EDIT, CHIEF, submitted(REPORTER, ApprovalLevel.SECTION_EDITOR))).isEqualTo(ALLOWED);
        assertThat(verdict(EDIT, CHIEF, submitted(REPORTER, ApprovalLevel.EDITOR_IN_CHIEF))).isEqualTo(ALLOWED);
        assertThat(verdict(EDIT, SECTION_EDITOR, article(REPORTER, ArticleStatus.PUBLISHED, 1))).isEqualTo(ALLOWED);
        assertThat(verdict(EDIT, CHIEF, article(SECTION_EDITOR, ArticleStatus.OFFLINE, 1))).isEqualTo(ALLOWED);
        assertThat(verdict(EDIT, PUBLISHER, article(CHIEF, ArticleStatus.PUBLISHED, 1))).isEqualTo(ALLOWED);
    }

    @Test
    void draftsStayTheAuthors() {
        assertThat(verdict(EDIT, CHIEF, REPORTERS_DRAFT)).isEqualTo(CONFLICT);
        assertThat(verdict(EDIT, SECTION_EDITOR, REPORTERS_DRAFT)).isEqualTo(CONFLICT);
        assertThat(allowed(PUBLISHER, REPORTERS_DRAFT)).isEmpty();
    }

    @Test
    void pendingLevelAboveTheCorrector() {
        assertThat(verdict(EDIT, SECTION_EDITOR, submitted(REPORTER, ApprovalLevel.PUBLISHER))).isEqualTo(FORBIDDEN);
        assertThat(verdict(EDIT, CHIEF, submitted(REPORTER, ApprovalLevel.PUBLISHER))).isEqualTo(FORBIDDEN);
        assertThat(verdict(EDIT, PUBLISHER, submitted(REPORTER, ApprovalLevel.PUBLISHER))).isEqualTo(ALLOWED);
    }

    @Test
    void equalOrLowerLevelMayNotCorrect() {
        assertThat(verdict(EDIT, CHIEF, article(OTHER_CHIEF, ArticleStatus.PUBLISHED, 1))).isEqualTo(FORBIDDEN);
        assertThat(verdict(EDIT, OTHER_PUBLISHER, PUBLISHED)).isEqualTo(FORBIDDEN);
        assertThat(verdict(EDIT, SECTION_EDITOR, article(CHIEF, ArticleStatus.PUBLISHED, 1))).isEqualTo(FORBIDDEN);
        assertThat(verdict(EDIT, OTHER_REPORTER, article(REPORTER, ArticleStatus.PUBLISHED, 1))).isEqualTo(FORBIDDEN);
        assertThat(allowed(SECTION_EDITOR, PUBLISHED)).containsExactly(TAKE_OFFLINE);
    }

    @Test
    void sectionEditorCorrectsInTheirSectionOnly() {
        assertThat(verdict(EDIT, SECTION_EDITOR, article(REPORTER, ArticleStatus.PUBLISHED, 1, KULTUR)))
                .isEqualTo(FORBIDDEN);
    }

    @Test
    void correctionsSwitchedOff() {
        ArticleEntity published = article(REPORTER, ArticleStatus.PUBLISHED, 1);
        assertThat(ArticlePolicy.verdict(EDIT, PUBLISHER, published, 1, STAFFING, NO_CORRECTIONS)).isEqualTo(FORBIDDEN);
        assertThat(ArticlePolicy.allowedActions(PUBLISHER, published, 1, STAFFING, NO_CORRECTIONS))
                .containsExactly(TAKE_OFFLINE);
        assertThat(ArticlePolicy.allowedActions(SECTION_EDITOR, submitted(REPORTER, ApprovalLevel.SECTION_EDITOR), 1,
                STAFFING, NO_CORRECTIONS)).containsExactly(APPROVE, REJECT);
    }

    @Test
    void publisherOnAReportersPublishedArticle() {
        assertThat(allowed(PUBLISHER, article(REPORTER, ArticleStatus.PUBLISHED, 1))).containsExactly(EDIT, TAKE_OFFLINE);
    }

    @Test
    void publisherOnTheirCorrection() {
        ArticleEntity corrected = article(REPORTER, ArticleStatus.PUBLISHED, 1);
        assertThat(ArticlePolicy.allowedActions(PUBLISHER, corrected, 2, contributed(corrected, PUBLISHER), CORRECTIONS))
                .containsExactly(EDIT, PUBLISH, TAKE_OFFLINE);
    }

    @Test
    void sectionEditorSubmitsTheirCorrection() {
        ArticleEntity corrected = article(REPORTER, ArticleStatus.PUBLISHED, 1);
        Staffing staffing = contributed(corrected, SECTION_EDITOR);
        assertThat(ArticlePolicy.allowedActions(SECTION_EDITOR, corrected, 2, staffing, CORRECTIONS))
                .containsExactly(EDIT, SUBMIT, TAKE_OFFLINE);
        assertThat(ArticlePolicy.verdict(SUBMIT, SECTION_EDITOR, corrected, 2, staffing, NO_CORRECTIONS))
                .isEqualTo(FORBIDDEN);
    }

    @Test
    void correctorWhoIsNoContributorMayNotSubmit() {
        ArticleEntity edited = article(REPORTER, ArticleStatus.PUBLISHED, 1);
        Staffing staffing = contributed(edited, REPORTER);
        assertThat(ArticlePolicy.verdict(SUBMIT, SECTION_EDITOR, edited, 2, staffing, CORRECTIONS)).isEqualTo(FORBIDDEN);
        assertThat(ArticlePolicy.verdict(PUBLISH, PUBLISHER, edited, 2, staffing, CORRECTIONS)).isEqualTo(FORBIDDEN);
        assertThat(ArticlePolicy.verdict(SUBMIT, REPORTER, edited, 2, staffing, CORRECTIONS)).isEqualTo(ALLOWED);
    }

    @Test
    void correctionKeepsTheAuthorsChainForTheAuthor() {
        // the author submits after a correction: the chain spans both contributors
        ArticleEntity corrected = article(REPORTER, ArticleStatus.PUBLISHED, 1);
        Staffing staffing = contributed(corrected, REPORTER, PUBLISHER);
        assertThat(ArticlePolicy.allowedActions(REPORTER, corrected, 3, staffing, CORRECTIONS))
                .containsExactly(EDIT, SUBMIT, TAKE_OFFLINE);
        assertThat(ArticlePolicy.allowedActions(PUBLISHER, corrected, 3, staffing, CORRECTIONS))
                .containsExactly(EDIT, SUBMIT, TAKE_OFFLINE);
    }

    @Test
    void authorWithoutSectionRoleMayNotEditButMayTakeOffline() {
        ArticleEntity draftInKultur = article(REPORTER, ArticleStatus.DRAFT, null, KULTUR);
        assertThat(verdict(EDIT, REPORTER, draftInKultur)).isEqualTo(FORBIDDEN);
        assertThat(verdict(DELETE, REPORTER, draftInKultur)).isEqualTo(FORBIDDEN);
        assertThat(allowed(REPORTER, draftInKultur)).isEmpty();
        ArticleEntity publishedInKultur = article(REPORTER, ArticleStatus.PUBLISHED, 1, KULTUR);
        assertThat(allowed(REPORTER, publishedInKultur)).containsExactly(TAKE_OFFLINE);
    }

    // --- visibility, unpublished changes

    @Test
    void visibility() {
        ArticleEntity reportersDraftInKultur = article(REPORTER, ArticleStatus.DRAFT, null, KULTUR);
        for (Newsroom administrator : List.of(PUBLISHER, CHIEF)) {
            assertThat(ArticlePolicy.visible(administrator, REPORTERS_DRAFT)).isTrue();
            assertThat(ArticlePolicy.visible(administrator, reportersDraftInKultur)).isTrue();
        }
        assertThat(ArticlePolicy.visible(REPORTER, REPORTERS_DRAFT)).isTrue();
        assertThat(ArticlePolicy.visible(REPORTER, reportersDraftInKultur)).isTrue();
        assertThat(ArticlePolicy.visible(SECTION_EDITOR, REPORTERS_DRAFT)).isTrue();
        assertThat(ArticlePolicy.visible(SECTION_EDITOR, reportersDraftInKultur)).isFalse();
        assertThat(ArticlePolicy.visible(OTHER_REPORTER, REPORTERS_DRAFT)).isFalse();
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
