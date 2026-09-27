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

class ApprovalChainTest {

    private static final long SPORT = 1L;
    private static final long KULTUR = 2L;
    private static final String AUTHOR = "author";

    /**
     * {@code sed} edits Sport, {@code chief} and {@code pub} hold the newspaper-wide levels.
     */
    private static final Staffing FULL = new Staffing(Map.of(SPORT, Set.of("sed")), Set.of("chief"), Set.of("pub"));

    private static Newsroom user(NewspaperRole... roles) {
        return new Newsroom(new CurrentUser(AUTHOR, AUTHOR, AUTHOR, List.of(roles)), Map.of());
    }

    private static Newsroom member(Map<Long, SectionRole> sectionRoles) {
        return new Newsroom(new CurrentUser(AUTHOR, AUTHOR, AUTHOR, List.of(NewspaperRole.READER)), sectionRoles);
    }

    private static Optional<ApprovalLevel> next(Optional<ApprovalLevel> above, long section, Staffing staffing) {
        return ApprovalChain.next(above, section, AUTHOR, staffing, false);
    }

    private static Optional<ApprovalLevel> nextLocked(Optional<ApprovalLevel> above, long section, Staffing staffing) {
        return ApprovalChain.next(above, section, AUTHOR, staffing, true);
    }

    @Test
    void reporterHasNoLevel() {
        assertThat(ApprovalChain.authorLevel(member(Map.of(SPORT, SectionRole.REPORTER)), SPORT)).isEmpty();
    }

    @Test
    void sectionEditorInOwnSection() {
        assertThat(ApprovalChain.authorLevel(member(Map.of(SPORT, SectionRole.SECTION_EDITOR)), SPORT))
                .contains(SECTION_EDITOR);
    }

    @Test
    void sectionEditorInForeignSectionIsReporter() {
        Newsroom user = member(Map.of(SPORT, SectionRole.SECTION_EDITOR, KULTUR, SectionRole.REPORTER));
        assertThat(ApprovalChain.authorLevel(user, KULTUR)).isEmpty();
    }

    @Test
    void newspaperWideLevels() {
        assertThat(ApprovalChain.authorLevel(user(NewspaperRole.EDITOR_IN_CHIEF), SPORT)).contains(EDITOR_IN_CHIEF);
        assertThat(ApprovalChain.authorLevel(user(NewspaperRole.PUBLISHER), SPORT)).contains(PUBLISHER);
        assertThat(ApprovalChain.authorLevel(user(NewspaperRole.EDITOR_IN_CHIEF, NewspaperRole.PUBLISHER), SPORT))
                .contains(PUBLISHER);
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
        Staffing authorAndOther = new Staffing(Map.of(SPORT, Set.of(AUTHOR, "sed")), Set.of(), Set.of("pub"));
        assertThat(next(Optional.empty(), SPORT, authorAndOther)).contains(SECTION_EDITOR);
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
        Staffing authorIsTheOnlyPublisher = new Staffing(Map.of(), Set.of(), Set.of(AUTHOR));
        assertThat(nextLocked(Optional.of(EDITOR_IN_CHIEF), SPORT, authorIsTheOnlyPublisher)).contains(PUBLISHER);
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
}
