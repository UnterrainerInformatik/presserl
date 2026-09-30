package info.unterrainer.presserl.article;

import static info.unterrainer.presserl.article.ApprovalLevel.EDITOR_IN_CHIEF;
import static info.unterrainer.presserl.article.ApprovalLevel.PUBLISHER;
import static info.unterrainer.presserl.article.ApprovalLevel.SECTION_EDITOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import info.unterrainer.presserl.auth.CurrentUser;
import info.unterrainer.presserl.auth.NewspaperRole;
import info.unterrainer.presserl.section.Newsroom;
import info.unterrainer.presserl.section.SectionRole;
import info.unterrainer.presserl.trust.TrustScope;

class ApprovalChainTest {

    private static final long SPORT = 1L;
    private static final long KULTUR = 2L;
    private static final String AUTHOR = "author";

    /**
     * {@code sed} edits Sport, {@code chief} and {@code pub} hold the newspaper-wide levels.
     */
    private static final Staffing FULL = new Staffing(Map.of(SPORT, Set.of("sed")), Set.of("chief"), Set.of("pub"));

    /**
     * {@link #FULL} with the author trusted in {@code scopes}.
     */
    private static Staffing trusting(TrustScope... scopes) {
        return new Staffing(FULL.sectionEditors(), FULL.editorsInChief(), FULL.publishers(),
                Map.of(AUTHOR, Set.of(scopes)));
    }

    private static TrustScope sectionTrust(long sectionId) {
        return new TrustScope(SECTION_EDITOR, sectionId);
    }

    private static TrustScope trust(ApprovalLevel level) {
        return new TrustScope(level, null);
    }

    private static Newsroom user(NewspaperRole... roles) {
        return new Newsroom(new CurrentUser(AUTHOR, AUTHOR, AUTHOR, List.of(roles)), Map.of());
    }

    private static Newsroom member(Map<Long, SectionRole> sectionRoles) {
        return new Newsroom(new CurrentUser(AUTHOR, AUTHOR, AUTHOR, List.of(NewspaperRole.READER)), sectionRoles);
    }

    private static Optional<ApprovalLevel> next(Optional<ApprovalLevel> above, long section, Staffing staffing) {
        return ApprovalChain.next(above, section, Set.of(AUTHOR), staffing, false);
    }

    private static Optional<ApprovalLevel> nextLocked(Optional<ApprovalLevel> above, long section, Staffing staffing) {
        return ApprovalChain.next(above, section, Set.of(AUTHOR), staffing, true);
    }

    @Test
    void reporterHasNoLevel() {
        assertThat(ApprovalChain.approverLevel(member(Map.of(SPORT, SectionRole.REPORTER)), SPORT)).isEmpty();
    }

    @Test
    void sectionEditorInOwnSection() {
        assertThat(ApprovalChain.approverLevel(member(Map.of(SPORT, SectionRole.SECTION_EDITOR)), SPORT))
                .contains(SECTION_EDITOR);
    }

    @Test
    void sectionEditorInForeignSectionIsReporter() {
        Newsroom user = member(Map.of(SPORT, SectionRole.SECTION_EDITOR, KULTUR, SectionRole.REPORTER));
        assertThat(ApprovalChain.approverLevel(user, KULTUR)).isEmpty();
    }

    @Test
    void newspaperWideLevels() {
        assertThat(ApprovalChain.approverLevel(user(NewspaperRole.EDITOR_IN_CHIEF), SPORT)).contains(EDITOR_IN_CHIEF);
        assertThat(ApprovalChain.approverLevel(user(NewspaperRole.PUBLISHER), SPORT)).contains(PUBLISHER);
        assertThat(ApprovalChain.approverLevel(user(NewspaperRole.EDITOR_IN_CHIEF, NewspaperRole.PUBLISHER), SPORT))
                .contains(PUBLISHER);
    }

    @Test
    void levelOfFollowsTheHolderSets() {
        Staffing staffing = new Staffing(Map.of(SPORT, Set.of("sed", "both")), Set.of("chief", "both"),
                Set.of("pub", "both"));
        assertThat(staffing.levelOf("pub", SPORT)).contains(PUBLISHER);
        assertThat(staffing.levelOf("both", SPORT)).contains(PUBLISHER);
        assertThat(staffing.levelOf("chief", SPORT)).contains(EDITOR_IN_CHIEF);
        assertThat(staffing.levelOf("sed", SPORT)).contains(SECTION_EDITOR);
        assertThat(staffing.levelOf("sed", KULTUR)).isEmpty();
        assertThat(staffing.levelOf("reporter", SPORT)).isEmpty();
    }

    @Test
    void approverLevelFollowsTheSameMapping() {
        assertThat(ApprovalChain.approverLevel(member(Map.of(SPORT, SectionRole.SECTION_EDITOR)), SPORT))
                .contains(SECTION_EDITOR);
        assertThat(ApprovalChain.approverLevel(member(Map.of(SPORT, SectionRole.SECTION_EDITOR)), KULTUR)).isEmpty();
        assertThat(ApprovalChain.approverLevel(user(NewspaperRole.EDITOR_IN_CHIEF), KULTUR)).contains(EDITOR_IN_CHIEF);
    }

    @Test
    void reporterStartsAtTheSectionEditor() {
        assertThat(next(Optional.empty(), SPORT, FULL)).contains(SECTION_EDITOR);
    }

    @Test
    void unstaffedSectionEditorLevelIsSkipped() {
        assertThat(next(Optional.empty(), KULTUR, FULL)).contains(EDITOR_IN_CHIEF);
    }

    @Test
    void unstaffedEditorInChiefLevelIsSkipped() {
        Staffing noChief = new Staffing(Map.of(), Set.of(), Set.of("pub"));
        assertThat(next(Optional.empty(), SPORT, noChief)).contains(PUBLISHER);
        assertThat(next(Optional.of(SECTION_EDITOR), SPORT, FULL)).contains(EDITOR_IN_CHIEF);
    }

    @Test
    void authorAsSoleHolderDoesNotStaffALevel() {
        Staffing authorHoldsAll = new Staffing(Map.of(SPORT, Set.of(AUTHOR)), Set.of(AUTHOR), Set.of("pub"));
        assertThat(next(Optional.empty(), SPORT, authorHoldsAll)).contains(PUBLISHER);
        // holding a level's role puts the author at that level, so their chain starts above it
        Staffing authorAndOther = new Staffing(Map.of(SPORT, Set.of(AUTHOR, "sed")), Set.of(), Set.of("pub"));
        assertThat(next(Optional.empty(), SPORT, authorAndOther)).contains(PUBLISHER);
    }

    @Test
    void everyHolderCountsWhetherLockedOrNot() {
        // RoleHolders and SectionRoleStore report locked accounts too; any holder but the author staffs a level
        Staffing lockedChiefOnly = new Staffing(Map.of(), Set.of("locked-chief"), Set.of("pub"));
        assertThat(next(Optional.empty(), KULTUR, lockedChiefOnly)).contains(EDITOR_IN_CHIEF);
    }

    @Test
    void editorInChiefWaitsForAnotherPublisher() {
        assertThat(next(Optional.of(EDITOR_IN_CHIEF), SPORT, FULL)).contains(PUBLISHER);
        assertThat(next(Optional.of(EDITOR_IN_CHIEF), SPORT, new Staffing(Map.of(), Set.of(), Set.of(AUTHOR))))
                .isEmpty();
    }

    @Test
    void nothingStaffedMeansAnEmptyChain() {
        assertThat(next(Optional.empty(), SPORT, new Staffing(Map.of(), Set.of(), Set.of()))).isEmpty();
    }

    @Test
    void publishersChainIsEmptyWithoutAskingForStaffing() {
        assertThat(next(Optional.of(PUBLISHER), SPORT, FULL)).isEmpty();
        assertThat(next(Optional.of(PUBLISHER), SPORT, Staffing.NOT_NEEDED)).isEmpty();
    }

    @Test
    void notNeededRefusesChainQuestions() {
        assertThatThrownBy(() -> next(Optional.empty(), SPORT, Staffing.NOT_NEEDED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void lockKeepsAnUnstaffedPublisherLevel() {
        // the author is editor-in-chief and nobody holds PUBLISHER
        Staffing noPublisher = new Staffing(Map.of(), Set.of(AUTHOR), Set.of());
        assertThat(nextLocked(Optional.of(EDITOR_IN_CHIEF), SPORT, noPublisher)).contains(PUBLISHER);
        assertThat(nextLocked(Optional.empty(), SPORT, new Staffing(Map.of(), Set.of(), Set.of()))).contains(PUBLISHER);
    }

    @Test
    void lockKeepsLowerLevelsAsTheyAre() {
        assertThat(nextLocked(Optional.empty(), SPORT, FULL)).contains(SECTION_EDITOR);
        assertThat(nextLocked(Optional.of(SECTION_EDITOR), SPORT, FULL)).contains(EDITOR_IN_CHIEF);
        assertThat(nextLocked(Optional.of(EDITOR_IN_CHIEF), SPORT, FULL)).contains(PUBLISHER);
    }

    @Test
    void lockedChainOfAPublisherIsEmpty() {
        assertThat(nextLocked(Optional.of(PUBLISHER), SPORT, Staffing.NOT_NEEDED)).isEmpty();
    }

    @Test
    void trustedSectionEditorLevelIsSkippedInItsSectionOnly() {
        Staffing sportTrust = new Staffing(Map.of(SPORT, Set.of("sed"), KULTUR, Set.of("sed")), Set.of("chief"),
                Set.of("pub"), Map.of(AUTHOR, Set.of(sectionTrust(SPORT))));
        assertThat(next(Optional.empty(), SPORT, sportTrust)).contains(EDITOR_IN_CHIEF);
        assertThat(next(Optional.empty(), KULTUR, sportTrust)).contains(SECTION_EDITOR);
    }

    @Test
    void trustedNewspaperWideLevelsAreSkipped() {
        assertThat(next(Optional.of(SECTION_EDITOR), SPORT, trusting(trust(EDITOR_IN_CHIEF)))).contains(PUBLISHER);
        assertThat(next(Optional.of(EDITOR_IN_CHIEF), SPORT, trusting(trust(PUBLISHER)))).isEmpty();
        assertThat(next(Optional.empty(), KULTUR, trusting(trust(EDITOR_IN_CHIEF), trust(PUBLISHER)))).isEmpty();
    }

    @Test
    void trustOfAnotherAccountDoesNotCount() {
        Staffing otherTrusted = new Staffing(FULL.sectionEditors(), FULL.editorsInChief(), FULL.publishers(),
                Map.of("someone-else", Set.of(sectionTrust(SPORT), trust(EDITOR_IN_CHIEF), trust(PUBLISHER))));
        assertThat(next(Optional.empty(), SPORT, otherTrusted)).contains(SECTION_EDITOR);
    }

    @Test
    void fullyTrustedAuthorHasAnEmptyChain() {
        assertThat(next(Optional.empty(), SPORT, trusting(sectionTrust(SPORT), trust(EDITOR_IN_CHIEF),
                trust(PUBLISHER)))).isEmpty();
    }

    @Test
    void lockOverridesTrust() {
        assertThat(nextLocked(Optional.of(EDITOR_IN_CHIEF), SPORT, trusting(trust(PUBLISHER)))).contains(PUBLISHER);
        assertThat(nextLocked(Optional.empty(), SPORT, trusting(sectionTrust(SPORT), trust(EDITOR_IN_CHIEF),
                trust(PUBLISHER)))).contains(PUBLISHER);
    }

    @Test
    void approvalSkipsTrustedLevelsAbove() {
        // a section editor approves up to their level; the trusted editor-in-chief level is skipped
        assertThat(next(Optional.of(SECTION_EDITOR), SPORT, trusting(trust(EDITOR_IN_CHIEF)))).contains(PUBLISHER);
    }

    @Test
    void notNeededRefusesTrustQuestions() {
        assertThatThrownBy(() -> Staffing.NOT_NEEDED.trusts(PUBLISHER, SPORT, AUTHOR))
                .isInstanceOf(IllegalStateException.class);
    }

    // --- several contributors

    private static final String CORRECTOR = "sed";

    private static Optional<ApprovalLevel> nextOf(Optional<ApprovalLevel> above, Staffing staffing, boolean locked,
            String... contributors) {
        return ApprovalChain.next(above, SPORT, Set.of(contributors), staffing, locked);
    }

    @Test
    void trustInTheAuthorDoesNotSkipTheCorrectorsLevels() {
        // the author is trusted at the newspaper-wide levels, the section editor corrected
        Staffing staffing = trusting(trust(EDITOR_IN_CHIEF), trust(PUBLISHER));
        assertThat(nextOf(Optional.empty(), staffing, false, AUTHOR)).contains(SECTION_EDITOR);
        assertThat(nextOf(Optional.of(SECTION_EDITOR), staffing, false, AUTHOR)).isEmpty();
        assertThat(nextOf(Optional.empty(), staffing, false, AUTHOR, CORRECTOR)).contains(SECTION_EDITOR);
        assertThat(nextOf(Optional.of(SECTION_EDITOR), staffing, false, AUTHOR, CORRECTOR)).contains(EDITOR_IN_CHIEF);
        assertThat(nextOf(Optional.of(EDITOR_IN_CHIEF), staffing, false, AUTHOR, CORRECTOR)).contains(PUBLISHER);
    }

    @Test
    void theCorrectorsOwnLevelIsNotInTheirChain() {
        // only the section editor contributed: their chain starts above SECTION_EDITOR
        assertThat(nextOf(Optional.empty(), FULL, false, CORRECTOR)).contains(EDITOR_IN_CHIEF);
    }

    @Test
    void trustedCorrectorAddsNothing() {
        Staffing staffing = new Staffing(FULL.sectionEditors(), FULL.editorsInChief(), FULL.publishers(),
                Map.of(AUTHOR, Set.of(trust(EDITOR_IN_CHIEF), trust(PUBLISHER)),
                        CORRECTOR, Set.of(trust(EDITOR_IN_CHIEF), trust(PUBLISHER))));
        assertThat(nextOf(Optional.of(SECTION_EDITOR), staffing, false, AUTHOR, CORRECTOR)).isEmpty();
    }

    @Test
    void lockWithACorrector() {
        Staffing staffing = trusting(sectionTrust(SPORT), trust(EDITOR_IN_CHIEF), trust(PUBLISHER));
        assertThat(nextOf(Optional.empty(), staffing, true, AUTHOR, CORRECTOR)).contains(EDITOR_IN_CHIEF);
        assertThat(nextOf(Optional.of(EDITOR_IN_CHIEF), staffing, true, AUTHOR, CORRECTOR)).contains(PUBLISHER);
        // a locked article only the publisher contributed to goes live directly
        assertThat(nextOf(Optional.empty(), FULL, true, "pub")).isEmpty();
    }

    @Test
    void publisherOnlyContributorGivesAnEmptyChain() {
        assertThat(nextOf(Optional.empty(), FULL, false, "pub")).isEmpty();
    }

    @Test
    void theEditorInChiefCorrectingBringsInThePublisher() {
        // the author is fully trusted; the editor-in-chief's correction needs the publisher
        Staffing staffing = trusting(sectionTrust(SPORT), trust(EDITOR_IN_CHIEF), trust(PUBLISHER));
        assertThat(nextOf(Optional.empty(), staffing, false, AUTHOR)).isEmpty();
        assertThat(nextOf(Optional.empty(), staffing, false, AUTHOR, "chief")).contains(PUBLISHER);
    }
}
