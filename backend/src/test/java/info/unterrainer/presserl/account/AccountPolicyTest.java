package info.unterrainer.presserl.account;

import static info.unterrainer.presserl.account.AccountAction.DELETE;
import static info.unterrainer.presserl.account.AccountAction.EDIT_ROLES;
import static info.unterrainer.presserl.account.AccountAction.LOCK;
import static info.unterrainer.presserl.account.AccountAction.RESET_PASSWORD;
import static info.unterrainer.presserl.account.AccountAction.UNLOCK;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        return new AccountDto(id, id, id, "", roles, sectionRoles, false, true, null, List.of(), List.of(), List.of());
    }

    /**
     * Both publishers enabled.
     */
    private static final Set<String> ENABLED_PUBLISHERS = Set.of("pub", "pub2");
    private static final Instant REQUESTED = Instant.parse("2026-10-02T08:15:00Z");

    private static List<AccountAction> actions(Newsroom requester, AccountDto target) {
        return AccountPolicy.allowedActions(requester, target, ENABLED_PUBLISHERS);
    }

    private static boolean permitted(AccountAction action, Newsroom requester, AccountDto target) {
        return AccountPolicy.permitted(action, requester, target, ENABLED_PUBLISHERS);
    }

    private static AccountDto self(Newsroom newsroom) {
        return account(newsroom.user().sub(), newsroom.user().roles(), List.of());
    }

    @Test
    void nobodyActsOnTheirOwnAccount() {
        for (Newsroom requester : List.of(PUBLISHER, CHIEF, SECTION_EDITOR, READER)) {
            assertThat(actions(requester, self(requester))).isEmpty();
            assertThat(actions(requester, self(requester).withEnabled(false))).isEmpty();
        }
    }

    @Test
    void nobodyActsOnAPublisherWithoutDeletionRequest() {
        for (Newsroom requester : List.of(PUBLISHER, CHIEF, SECTION_EDITOR, READER)) {
            assertThat(actions(requester, OTHER_PUBLISHER)).isEmpty();
            assertThat(actions(requester, OTHER_PUBLISHER.withEnabled(false))).isEmpty();
        }
    }

    @Test
    void publisherEditsResetsLocksAndDeletesEveryOtherAccount() {
        for (AccountDto target : List.of(OTHER_CHIEF, PLAIN_READER, ROLELESS, SPORT_REPORTER, SPORT_SECTION_EDITOR)) {
            assertThat(actions(PUBLISHER, target))
                    .containsExactly(EDIT_ROLES, RESET_PASSWORD, LOCK, DELETE);
            assertThat(actions(PUBLISHER, target.withDeletionRequestedAt(REQUESTED)))
                    .containsExactly(EDIT_ROLES, RESET_PASSWORD, LOCK, DELETE);
        }
    }

    @Test
    void lockForEnabledUnlockForDisabled() {
        assertThat(actions(PUBLISHER, PLAIN_READER))
                .containsExactly(EDIT_ROLES, RESET_PASSWORD, LOCK, DELETE);
        assertThat(actions(PUBLISHER, PLAIN_READER.withEnabled(false)))
                .containsExactly(EDIT_ROLES, RESET_PASSWORD, UNLOCK, DELETE);
    }

    @Test
    void publisherDeletesAPublisherOnlyWithRequestWhileAnotherEnabledPublisherRemains() {
        AccountDto requested = OTHER_PUBLISHER.withDeletionRequestedAt(REQUESTED);
        assertThat(actions(PUBLISHER, requested)).containsExactly(DELETE);
        assertThat(actions(PUBLISHER, requested.withEnabled(false))).containsExactly(DELETE);
        assertThat(AccountPolicy.allowedActions(PUBLISHER, requested, Set.of("pub2"))).isEmpty();
        assertThat(AccountPolicy.allowedActions(PUBLISHER, requested, Set.of("third", "pub2"))).containsExactly(DELETE);
        for (Newsroom requester : List.of(CHIEF, SECTION_EDITOR, READER)) {
            assertThat(actions(requester, requested)).isEmpty();
        }
    }

    @Test
    void onlyPublishersDelete() {
        for (Newsroom requester : List.of(CHIEF, SECTION_EDITOR, REPORTER, READER, NOBODY)) {
            assertThat(permitted(DELETE, requester, PLAIN_READER.withDeletionRequestedAt(REQUESTED))).isFalse();
        }
        assertThat(permitted(DELETE, PUBLISHER, self(PUBLISHER).withDeletionRequestedAt(REQUESTED))).isFalse();
    }

    @Test
    void publisherMayLockLockedAndUnlockEnabledAccounts() {
        assertThat(permitted(LOCK, PUBLISHER, PLAIN_READER.withEnabled(false))).isTrue();
        assertThat(permitted(UNLOCK, PUBLISHER, PLAIN_READER)).isTrue();
        assertThat(permitted(LOCK, CHIEF, PLAIN_READER)).isFalse();
        assertThat(permitted(LOCK, PUBLISHER, OTHER_PUBLISHER)).isFalse();
        assertThat(permitted(UNLOCK, PUBLISHER, self(PUBLISHER).withEnabled(false))).isFalse();
    }

    @Test
    void editorInChiefEditsAndResetsBelowOwnRankButNeverLocks() {
        for (AccountDto target : List.of(PLAIN_READER, ROLELESS, SPORT_REPORTER, SPORT_SECTION_EDITOR)) {
            assertThat(actions(CHIEF, target)).containsExactly(EDIT_ROLES, RESET_PASSWORD);
            assertThat(actions(CHIEF, target.withEnabled(false)))
                    .containsExactly(EDIT_ROLES, RESET_PASSWORD);
        }
        assertThat(actions(CHIEF, OTHER_CHIEF)).isEmpty();
        assertThat(actions(CHIEF, CHIEF_REPORTING_IN_SPORT)).isEmpty();
    }

    @Test
    void sectionEditorEditsAndResetsReportersOfOwnSectionsOnly() {
        assertThat(actions(SECTION_EDITOR, SPORT_REPORTER))
                .containsExactly(EDIT_ROLES, RESET_PASSWORD);
        assertThat(actions(SECTION_EDITOR, SPORT_REPORTER.withEnabled(false)))
                .containsExactly(EDIT_ROLES, RESET_PASSWORD);
    }

    @Test
    void sectionEditorDoesNotResetReporterAlsoInForeignSection() {
        assertThat(actions(SECTION_EDITOR, SPORT_AND_KULTUR_REPORTER)).isEmpty();
        assertThat(actions(SECTION_EDITOR, KULTUR_REPORTER)).isEmpty();
    }

    @Test
    void sectionEditorDoesNotResetOtherSectionEditorsOrHigherRanks() {
        assertThat(actions(SECTION_EDITOR, SPORT_SECTION_EDITOR)).isEmpty();
        assertThat(actions(SECTION_EDITOR, CHIEF_REPORTING_IN_SPORT)).isEmpty();
        assertThat(actions(SECTION_EDITOR, OTHER_CHIEF)).isEmpty();
    }

    @Test
    void sectionEditorDoesNotResetAccountsWithoutSectionRoles() {
        assertThat(actions(SECTION_EDITOR, PLAIN_READER)).isEmpty();
        assertThat(actions(SECTION_EDITOR, ROLELESS)).isEmpty();
    }

    @Test
    void reportersReadersAndRolelessUsersDoNothing() {
        for (Newsroom requester : List.of(REPORTER, READER, NOBODY)) {
            for (AccountDto target : List.of(OTHER_CHIEF, PLAIN_READER, ROLELESS, SPORT_REPORTER)) {
                assertThat(actions(requester, target)).isEmpty();
                assertThat(actions(requester, target.withEnabled(false))).isEmpty();
            }
        }
    }

    @Test
    void editRolesFollowsThePasswordResetRule() {
        List<Newsroom> requesters = List.of(PUBLISHER, CHIEF, SECTION_EDITOR, REPORTER, READER, NOBODY);
        List<AccountDto> targets = List.of(OTHER_PUBLISHER, OTHER_CHIEF, PLAIN_READER, ROLELESS, SPORT_REPORTER,
                SPORT_AND_KULTUR_REPORTER, KULTUR_REPORTER, SPORT_SECTION_EDITOR, CHIEF_REPORTING_IN_SPORT);
        for (Newsroom requester : requesters) {
            for (AccountDto target : targets) {
                assertThat(permitted(EDIT_ROLES, requester, target))
                        .as("%s on %s", requester.user().sub(), target.id())
                        .isEqualTo(permitted(RESET_PASSWORD, requester, target));
            }
        }
    }

    @Test
    void editRolesOnLockedAccounts() {
        assertThat(permitted(EDIT_ROLES, PUBLISHER, PLAIN_READER.withEnabled(false))).isTrue();
        assertThat(permitted(EDIT_ROLES, CHIEF, PLAIN_READER.withEnabled(false))).isTrue();
        assertThat(permitted(EDIT_ROLES, SECTION_EDITOR, SPORT_REPORTER.withEnabled(false))).isTrue();
    }

    @Test
    void editRolesNeverOnOwnOrPublisherAccount() {
        for (Newsroom requester : List.of(PUBLISHER, CHIEF, SECTION_EDITOR)) {
            assertThat(permitted(EDIT_ROLES, requester, self(requester))).isFalse();
            assertThat(permitted(EDIT_ROLES, requester, OTHER_PUBLISHER)).isFalse();
        }
    }

    @Test
    void editRolesComesFirst() {
        assertThat(AccountAction.values()).containsExactly(EDIT_ROLES, RESET_PASSWORD, LOCK, UNLOCK, DELETE);
        assertThat(actions(CHIEF, PLAIN_READER)).containsExactly(EDIT_ROLES, RESET_PASSWORD);
    }
}
