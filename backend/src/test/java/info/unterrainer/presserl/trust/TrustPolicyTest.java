package info.unterrainer.presserl.trust;

import static info.unterrainer.presserl.article.ApprovalLevel.EDITOR_IN_CHIEF;
import static info.unterrainer.presserl.article.ApprovalLevel.PUBLISHER;
import static info.unterrainer.presserl.article.ApprovalLevel.SECTION_EDITOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import info.unterrainer.presserl.account.AccountDto;
import info.unterrainer.presserl.auth.CurrentUser;
import info.unterrainer.presserl.auth.NewspaperRole;
import info.unterrainer.presserl.section.Newsroom;
import info.unterrainer.presserl.section.SectionRole;
import info.unterrainer.presserl.section.SectionRoleDto;

class TrustPolicyTest {

    private static final long SPORT = 1L;
    private static final long KULTUR = 2L;
    /**
     * Kultur comes first in the section order.
     */
    private static final List<Long> SECTION_IDS = List.of(KULTUR, SPORT);

    private static final TrustScope PUBLISHER_TRUST = new TrustScope(PUBLISHER, null);
    private static final TrustScope CHIEF_TRUST = new TrustScope(EDITOR_IN_CHIEF, null);
    private static final TrustScope SPORT_TRUST = new TrustScope(SECTION_EDITOR, SPORT);
    private static final TrustScope KULTUR_TRUST = new TrustScope(SECTION_EDITOR, KULTUR);

    private static final Newsroom PUB = user("pub", NewspaperRole.PUBLISHER, NewspaperRole.EDITOR_IN_CHIEF);
    private static final Newsroom CHIEF = user("chief", NewspaperRole.EDITOR_IN_CHIEF);
    private static final Newsroom CHIEF_EDITING_SPORT = new Newsroom(new CurrentUser("chief2", "chief2", "chief2",
            List.of(NewspaperRole.EDITOR_IN_CHIEF)), Map.of(SPORT, SectionRole.SECTION_EDITOR));
    private static final Newsroom SPORT_EDITOR = member("sed", Map.of(SPORT, SectionRole.SECTION_EDITOR));
    private static final Newsroom BOTH_EDITOR = member("sed2",
            Map.of(SPORT, SectionRole.SECTION_EDITOR, KULTUR, SectionRole.SECTION_EDITOR));
    private static final Newsroom REPORTER = member("rep", Map.of(SPORT, SectionRole.REPORTER));
    private static final Newsroom READER = user("reader", NewspaperRole.READER);

    private static final AccountDto OTHER_PUBLISHER = account("pub2", List.of(NewspaperRole.PUBLISHER), List.of());
    private static final AccountDto OTHER_CHIEF = account("chief3", List.of(NewspaperRole.EDITOR_IN_CHIEF), List.of());
    private static final AccountDto SPORT_REPORTER = account("rep2", List.of(NewspaperRole.READER),
            List.of(new SectionRoleDto(SPORT, SectionRole.REPORTER)));
    private static final AccountDto BOTH_REPORTER = account("rep3", List.of(NewspaperRole.READER),
            List.of(new SectionRoleDto(KULTUR, SectionRole.REPORTER), new SectionRoleDto(SPORT, SectionRole.REPORTER)));
    private static final AccountDto SPORT_SECTION_EDITOR = account("sed3", List.of(),
            List.of(new SectionRoleDto(SPORT, SectionRole.SECTION_EDITOR)));
    private static final AccountDto PLAIN_READER = account("reader2", List.of(NewspaperRole.READER), List.of());
    private static final AccountDto ROLELESS = account("none", List.of(), List.of());

    private static Newsroom user(String sub, NewspaperRole... roles) {
        return new Newsroom(new CurrentUser(sub, sub, sub, List.of(roles)), Map.of());
    }

    private static Newsroom member(String sub, Map<Long, SectionRole> sectionRoles) {
        return new Newsroom(new CurrentUser(sub, sub, sub, List.of(NewspaperRole.READER)), sectionRoles);
    }

    private static AccountDto account(String id, List<NewspaperRole> roles, List<SectionRoleDto> sectionRoles) {
        return new AccountDto(id, id, id, "", roles, sectionRoles, true, List.of(), List.of(), List.of());
    }

    private static AccountDto self(Newsroom newsroom) {
        return account(newsroom.user().sub(), newsroom.user().roles(), List.of(new SectionRoleDto(SPORT,
                SectionRole.REPORTER)));
    }

    private static List<TrustScope> scopes(Newsroom requester, AccountDto target) {
        return TrustPolicy.scopes(requester, target, SECTION_IDS);
    }

    @Test
    void trustLevelIsTheOwnHighestLevel() {
        assertThat(TrustPolicy.trustLevels(PUB)).containsExactly(PUBLISHER_TRUST);
        assertThat(TrustPolicy.trustLevels(CHIEF)).containsExactly(CHIEF_TRUST);
        assertThat(TrustPolicy.trustLevels(CHIEF_EDITING_SPORT)).containsExactly(CHIEF_TRUST);
        assertThat(TrustPolicy.trustLevels(BOTH_EDITOR)).containsExactlyInAnyOrder(SPORT_TRUST, KULTUR_TRUST);
        assertThat(TrustPolicy.trustLevels(REPORTER)).isEmpty();
        assertThat(TrustPolicy.trustLevels(READER)).isEmpty();
    }

    @Test
    void publisherTrustsEditorsInChiefAndWritersBelow() {
        assertThat(scopes(PUB, OTHER_CHIEF)).containsExactly(PUBLISHER_TRUST);
        assertThat(scopes(PUB, SPORT_REPORTER)).containsExactly(PUBLISHER_TRUST);
        assertThat(scopes(PUB, SPORT_SECTION_EDITOR)).containsExactly(PUBLISHER_TRUST);
        assertThat(TrustPolicy.mayGrant(PUB, OTHER_CHIEF, PUBLISHER_TRUST)).isTrue();
    }

    @Test
    void publisherDoesNotTrustAtLowerLevels() {
        assertThat(TrustPolicy.mayGrant(PUB, SPORT_REPORTER, CHIEF_TRUST)).isFalse();
        assertThat(TrustPolicy.mayGrant(PUB, SPORT_REPORTER, SPORT_TRUST)).isFalse();
    }

    @Test
    void editorInChiefTrustsSectionMembers() {
        assertThat(scopes(CHIEF, SPORT_REPORTER)).containsExactly(CHIEF_TRUST);
        assertThat(scopes(CHIEF, SPORT_SECTION_EDITOR)).containsExactly(CHIEF_TRUST);
        assertThat(scopes(CHIEF, OTHER_CHIEF)).isEmpty();
        assertThat(scopes(CHIEF, OTHER_PUBLISHER)).isEmpty();
    }

    @Test
    void sectionEditorTrustsReportersOfOwnSectionsInSectionOrder() {
        assertThat(scopes(SPORT_EDITOR, BOTH_REPORTER)).containsExactly(SPORT_TRUST);
        assertThat(scopes(BOTH_EDITOR, BOTH_REPORTER)).containsExactly(KULTUR_TRUST, SPORT_TRUST);
        assertThat(TrustPolicy.mayGrant(SPORT_EDITOR, BOTH_REPORTER, KULTUR_TRUST)).isFalse();
    }

    @Test
    void sectionEditorDoesNotTrustAnotherSectionEditorOrAChief() {
        assertThat(scopes(SPORT_EDITOR, SPORT_SECTION_EDITOR)).isEmpty();
        AccountDto chiefReporting = account("chief4", List.of(NewspaperRole.EDITOR_IN_CHIEF),
                List.of(new SectionRoleDto(SPORT, SectionRole.REPORTER)));
        assertThat(scopes(SPORT_EDITOR, chiefReporting)).isEmpty();
    }

    @Test
    void editorInChiefWhoIsAlsoSectionEditorOnlyTrustsAsEditorInChief() {
        assertThat(scopes(CHIEF_EDITING_SPORT, SPORT_REPORTER)).containsExactly(CHIEF_TRUST);
    }

    @Test
    void nobodyTrustsThemselves() {
        for (Newsroom requester : List.of(PUB, CHIEF, SPORT_EDITOR)) {
            AccountDto own = self(requester).withTrusts(TrustPolicy.trustLevels(requester));
            assertThat(scopes(requester, own)).isEmpty();
            assertThat(TrustPolicy.mayGrant(requester, own, TrustPolicy.trustLevels(requester).getFirst())).isFalse();
        }
    }

    @Test
    void accountsNotWritingGetNoScope() {
        for (Newsroom requester : List.of(PUB, CHIEF, SPORT_EDITOR)) {
            assertThat(scopes(requester, PLAIN_READER)).isEmpty();
            assertThat(scopes(requester, ROLELESS)).isEmpty();
            assertThat(scopes(requester, OTHER_PUBLISHER)).isEmpty();
        }
    }

    @Test
    void usersWithoutTrustLevelGetNoScope() {
        assertThat(scopes(REPORTER, SPORT_REPORTER)).isEmpty();
        assertThat(scopes(READER, OTHER_CHIEF)).isEmpty();
    }

    @Test
    void staleEntriesCanStillBeCleared() {
        AccountDto promoted = OTHER_CHIEF.withTrusts(List.of(CHIEF_TRUST));
        assertThat(scopes(CHIEF, promoted)).containsExactly(CHIEF_TRUST);
        assertThat(TrustPolicy.mayGrant(CHIEF, promoted, CHIEF_TRUST)).isFalse();

        AccountDto leftSport = PLAIN_READER.withTrusts(List.of(SPORT_TRUST));
        assertThat(scopes(SPORT_EDITOR, leftSport)).containsExactly(SPORT_TRUST);

        AccountDto otherPublisher = OTHER_PUBLISHER.withTrusts(List.of(PUBLISHER_TRUST));
        assertThat(scopes(PUB, otherPublisher)).containsExactly(PUBLISHER_TRUST);
    }

    @Test
    void entriesOfOtherLevelsAreNotClearable() {
        AccountDto trusted = SPORT_REPORTER.withTrusts(List.of(PUBLISHER_TRUST, CHIEF_TRUST, SPORT_TRUST));
        assertThat(scopes(CHIEF, trusted)).containsExactly(CHIEF_TRUST);
        assertThat(scopes(SPORT_EDITOR, PLAIN_READER.withTrusts(List.of(KULTUR_TRUST)))).isEmpty();
    }

    @Test
    void lockedAccountsAreTreatedLikeEnabledOnes() {
        assertThat(scopes(PUB, SPORT_REPORTER.withEnabled(false))).containsExactly(PUBLISHER_TRUST);
        assertThat(scopes(SPORT_EDITOR, SPORT_REPORTER.withEnabled(false))).containsExactly(SPORT_TRUST);
    }
}
