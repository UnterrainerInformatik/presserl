package info.unterrainer.presserl.account;

import static info.unterrainer.presserl.account.AccountAction.LOCK;
import static info.unterrainer.presserl.account.AccountAction.RESET_PASSWORD;
import static info.unterrainer.presserl.account.AccountAction.UNLOCK;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import info.unterrainer.presserl.auth.CurrentUser;
import info.unterrainer.presserl.auth.NewspaperRole;
import info.unterrainer.presserl.section.Newsroom;
import info.unterrainer.presserl.section.SectionRole;
import info.unterrainer.presserl.section.SectionRoleDto;

class AccountPolicyTest {

    private static final long SPORT = 1L;
    private static final long KULTUR = 2L;

    private static final Newsroom PUBLISHER = user("pub", NewspaperRole.PUBLISHER);
    private static final Newsroom CHIEF = user("chief", NewspaperRole.EDITOR_IN_CHIEF);
    private static final Newsroom READER = user("reader", NewspaperRole.READER);
    private static final Newsroom NOBODY = user("nobody");
    private static final Newsroom SECTION_EDITOR = member("sed", Map.of(SPORT, SectionRole.SECTION_EDITOR));
    private static final Newsroom REPORTER = member("rep", Map.of(SPORT, SectionRole.REPORTER));

    private static final AccountDto OTHER_PUBLISHER = account("pub2", List.of(NewspaperRole.PUBLISHER), List.of());
    private static final AccountDto OTHER_CHIEF = account("chief2", List.of(NewspaperRole.EDITOR_IN_CHIEF), List.of());
    private static final AccountDto PLAIN_READER = account("reader2", List.of(NewspaperRole.READER), List.of());
    private static final AccountDto ROLELESS = account("none", List.of(), List.of());
    private static final AccountDto SPORT_REPORTER = account("rep2", List.of(NewspaperRole.READER),
            List.of(new SectionRoleDto(SPORT, SectionRole.REPORTER)));
    private static final AccountDto SPORT_AND_KULTUR_REPORTER = account("rep3", List.of(NewspaperRole.READER),
            List.of(new SectionRoleDto(SPORT, SectionRole.REPORTER), new SectionRoleDto(KULTUR, SectionRole.REPORTER)));
    private static final AccountDto KULTUR_REPORTER = account("rep4", List.of(),
            List.of(new SectionRoleDto(KULTUR, SectionRole.REPORTER)));
    private static final AccountDto SPORT_SECTION_EDITOR = account("sed2", List.of(NewspaperRole.READER),
            List.of(new SectionRoleDto(SPORT, SectionRole.SECTION_EDITOR)));
    private static final AccountDto CHIEF_REPORTING_IN_SPORT = account("chief3", List.of(NewspaperRole.EDITOR_IN_CHIEF),
            List.of(new SectionRoleDto(SPORT, SectionRole.REPORTER)));

    private static Newsroom user(String sub, NewspaperRole... roles) {
        return new Newsroom(new CurrentUser(sub, sub, sub, List.of(roles)), Map.of());
    }

    private static Newsroom member(String sub, Map<Long, SectionRole> sectionRoles) {
        return new Newsroom(new CurrentUser(sub, sub, sub, List.of(NewspaperRole.READER)), sectionRoles);
    }

    private static AccountDto account(String id, List<NewspaperRole> roles, List<SectionRoleDto> sectionRoles) {
        return new AccountDto(id, id, id, "", roles, sectionRoles, true, List.of());
    }

    private static AccountDto self(Newsroom newsroom) {
        return account(newsroom.user().sub(), newsroom.user().roles(), List.of());
    }

    @Test
    void nobodyActsOnTheirOwnAccount() {
        for (Newsroom requester : List.of(PUBLISHER, CHIEF, SECTION_EDITOR, READER)) {
            assertThat(AccountPolicy.allowedActions(requester, self(requester))).isEmpty();
            assertThat(AccountPolicy.allowedActions(requester, self(requester).withEnabled(false))).isEmpty();
        }
    }

    @Test
    void nobodyActsOnAPublisher() {
        for (Newsroom requester : List.of(PUBLISHER, CHIEF, SECTION_EDITOR, READER)) {
            assertThat(AccountPolicy.allowedActions(requester, OTHER_PUBLISHER)).isEmpty();
            assertThat(AccountPolicy.allowedActions(requester, OTHER_PUBLISHER.withEnabled(false))).isEmpty();
        }
    }

    @Test
    void publisherResetsAndLocksEveryOtherAccount() {
        for (AccountDto target : List.of(OTHER_CHIEF, PLAIN_READER, ROLELESS, SPORT_REPORTER, SPORT_SECTION_EDITOR)) {
            assertThat(AccountPolicy.allowedActions(PUBLISHER, target)).containsExactly(RESET_PASSWORD, LOCK);
        }
    }

    @Test
    void lockForEnabledUnlockForDisabled() {
        assertThat(AccountPolicy.allowedActions(PUBLISHER, PLAIN_READER)).containsExactly(RESET_PASSWORD, LOCK);
        assertThat(AccountPolicy.allowedActions(PUBLISHER, PLAIN_READER.withEnabled(false)))
                .containsExactly(RESET_PASSWORD, UNLOCK);
    }

    @Test
    void publisherMayLockLockedAndUnlockEnabledAccounts() {
        assertThat(AccountPolicy.permitted(LOCK, PUBLISHER, PLAIN_READER.withEnabled(false))).isTrue();
        assertThat(AccountPolicy.permitted(UNLOCK, PUBLISHER, PLAIN_READER)).isTrue();
        assertThat(AccountPolicy.permitted(LOCK, CHIEF, PLAIN_READER)).isFalse();
        assertThat(AccountPolicy.permitted(LOCK, PUBLISHER, OTHER_PUBLISHER)).isFalse();
        assertThat(AccountPolicy.permitted(UNLOCK, PUBLISHER, self(PUBLISHER).withEnabled(false))).isFalse();
    }

    @Test
    void editorInChiefResetsBelowOwnRankButNeverLocks() {
        for (AccountDto target : List.of(PLAIN_READER, ROLELESS, SPORT_REPORTER, SPORT_SECTION_EDITOR)) {
            assertThat(AccountPolicy.allowedActions(CHIEF, target)).containsExactly(RESET_PASSWORD);
            assertThat(AccountPolicy.allowedActions(CHIEF, target.withEnabled(false))).containsExactly(RESET_PASSWORD);
        }
        assertThat(AccountPolicy.allowedActions(CHIEF, OTHER_CHIEF)).isEmpty();
        assertThat(AccountPolicy.allowedActions(CHIEF, CHIEF_REPORTING_IN_SPORT)).isEmpty();
    }

    @Test
    void sectionEditorResetsReportersOfOwnSectionsOnly() {
        assertThat(AccountPolicy.allowedActions(SECTION_EDITOR, SPORT_REPORTER)).containsExactly(RESET_PASSWORD);
        assertThat(AccountPolicy.allowedActions(SECTION_EDITOR, SPORT_REPORTER.withEnabled(false)))
                .containsExactly(RESET_PASSWORD);
    }

    @Test
    void sectionEditorDoesNotResetReporterAlsoInForeignSection() {
        assertThat(AccountPolicy.allowedActions(SECTION_EDITOR, SPORT_AND_KULTUR_REPORTER)).isEmpty();
        assertThat(AccountPolicy.allowedActions(SECTION_EDITOR, KULTUR_REPORTER)).isEmpty();
    }

    @Test
    void sectionEditorDoesNotResetOtherSectionEditorsOrHigherRanks() {
        assertThat(AccountPolicy.allowedActions(SECTION_EDITOR, SPORT_SECTION_EDITOR)).isEmpty();
        assertThat(AccountPolicy.allowedActions(SECTION_EDITOR, CHIEF_REPORTING_IN_SPORT)).isEmpty();
        assertThat(AccountPolicy.allowedActions(SECTION_EDITOR, OTHER_CHIEF)).isEmpty();
    }

    @Test
    void sectionEditorDoesNotResetAccountsWithoutSectionRoles() {
        assertThat(AccountPolicy.allowedActions(SECTION_EDITOR, PLAIN_READER)).isEmpty();
        assertThat(AccountPolicy.allowedActions(SECTION_EDITOR, ROLELESS)).isEmpty();
    }

    @Test
    void reportersReadersAndRolelessUsersDoNothing() {
        for (Newsroom requester : List.of(REPORTER, READER, NOBODY)) {
            for (AccountDto target : List.of(OTHER_CHIEF, PLAIN_READER, ROLELESS, SPORT_REPORTER)) {
                assertThat(AccountPolicy.allowedActions(requester, target)).isEmpty();
                assertThat(AccountPolicy.allowedActions(requester, target.withEnabled(false))).isEmpty();
            }
        }
    }
}
