package info.unterrainer.presserl.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import info.unterrainer.presserl.api.FieldError;
import info.unterrainer.presserl.auth.CurrentUser;
import info.unterrainer.presserl.auth.NewspaperRole;
import info.unterrainer.presserl.section.Newsroom;
import info.unterrainer.presserl.section.SectionRole;
import info.unterrainer.presserl.section.SectionRoleDto;
import jakarta.ws.rs.core.Response.Status;

/**
 * {@link AccountRoleEdit#check}: only the difference to the current roles needs permission.
 */
class AccountRoleEditTest {

    private static final long SPORT = 1L;
    private static final long KULTUR = 2L;
    private static final List<Long> SECTIONS = List.of(SPORT, KULTUR);

    private static final Newsroom PUBLISHER = new Newsroom(
            new CurrentUser("pub", "pub", "pub", List.of(NewspaperRole.PUBLISHER)), Map.of());
    private static final Newsroom CHIEF = new Newsroom(
            new CurrentUser("chief", "chief", "chief", List.of(NewspaperRole.EDITOR_IN_CHIEF)), Map.of());
    private static final Newsroom SPORT_EDITOR = new Newsroom(new CurrentUser("sed", "sed", "sed", List.of()),
            Map.of(SPORT, SectionRole.SECTION_EDITOR));

    private static final AccountDto SPORT_REPORTER = new AccountDto("rep", "rep", "Rep", "",
            List.of(NewspaperRole.READER), List.of(sectionRole(SPORT, SectionRole.REPORTER)), false, true, null, List.of(),
            List.of(), List.of());

    @Test
    void sectionEditorKeepsUnchangedReaderRole() {
        AccountRoleEdit.Change change = AccountRoleEdit.check(SPORT_EDITOR, SPORT_REPORTER,
                request(List.of(NewspaperRole.READER), sectionRole(SPORT, SectionRole.SECTION_EDITOR)), SECTIONS);

        assertThat(change.added()).isEmpty();
        assertThat(change.removed()).isEmpty();
        assertThat(change.sectionRolesChanged()).isTrue();
        assertThat(change.sectionRoles()).containsExactly(sectionRole(SPORT, SectionRole.SECTION_EDITOR));
    }

    @Test
    void sectionEditorMayNotRemoveReaderRole() {
        assertRefused(SPORT_EDITOR, request(List.of(), sectionRole(SPORT, SectionRole.REPORTER)), "roles");
    }

    @Test
    void sectionEditorMayNotChangeOutsideTheirSection() {
        assertRefused(SPORT_EDITOR, request(List.of(NewspaperRole.READER), sectionRole(SPORT, SectionRole.REPORTER),
                sectionRole(KULTUR, SectionRole.REPORTER)), "sectionRoles");
    }

    @Test
    void sectionEditorRemovesTheirReporter() {
        AccountRoleEdit.Change change = AccountRoleEdit.check(SPORT_EDITOR, SPORT_REPORTER,
                request(List.of(NewspaperRole.READER)), SECTIONS);

        assertThat(change.sectionRoles()).isEmpty();
        assertThat(change.isEmpty()).isFalse();
        // the last section role goes without the field: the account becomes a sectionless reporter
        assertThat(change.marker()).isTrue();
        assertThat(change.markerChanged()).isTrue();
        assertThat(change.automaticMarker()).isTrue();
    }

    @Test
    void explicitFalseRemovesEveryWritingRight() {
        AccountRoleEdit.Change change = AccountRoleEdit.check(PUBLISHER, SPORT_REPORTER,
                markerRequest(List.of(NewspaperRole.READER), false), SECTIONS);

        assertThat(change.marker()).isFalse();
        assertThat(change.markerChanged()).isFalse();
        assertThat(change.automaticMarker()).isFalse();
    }

    @Test
    void editorInChiefKeepsNoMarker() {
        AccountRoleEdit.Change change = AccountRoleEdit.check(PUBLISHER, SPORT_REPORTER,
                request(List.of(NewspaperRole.EDITOR_IN_CHIEF)), SECTIONS);

        assertThat(change.marker()).isFalse();
        assertThat(change.automaticMarker()).isFalse();
    }

    @Test
    void markerAloneIsARole() {
        AccountRoleEdit.Change change = AccountRoleEdit.check(PUBLISHER, SPORT_REPORTER,
                markerRequest(List.of(), true), SECTIONS);

        assertThat(change.marker()).isTrue();
        assertThat(change.markerChanged()).isTrue();
        assertThat(change.removed()).containsExactly(NewspaperRole.READER);
    }

    @Test
    void noRoleAndNoMarkerLeft() {
        assertThatThrownBy(() -> AccountRoleEdit.check(PUBLISHER, SPORT_REPORTER, markerRequest(List.of(), false),
                SECTIONS)).isInstanceOfSatisfying(AccountException.class, e -> {
                    assertThat(e.status()).isEqualTo(Status.BAD_REQUEST);
                    assertThat(e.errors()).extracting(FieldError::field).containsExactly("roles");
                });
    }

    @Test
    void sectionEditorMayNotAssignTheMarker() {
        assertRefused(SPORT_EDITOR, new EditRolesRequest(List.of(NewspaperRole.READER),
                List.of(sectionRole(SPORT, SectionRole.REPORTER)), true), "sectionlessReporter");
    }

    @Test
    void sectionEditorSendingTheUnchangedMarkerIsNoChange() {
        AccountRoleEdit.Change change = AccountRoleEdit.check(SPORT_EDITOR, SPORT_REPORTER,
                new EditRolesRequest(List.of(NewspaperRole.READER), List.of(sectionRole(SPORT, SectionRole.REPORTER)),
                        false), SECTIONS);

        assertThat(change.isEmpty()).isTrue();
    }

    @Test
    void editorInChiefMayNotAddPublisher() {
        assertRefused(CHIEF, request(List.of(NewspaperRole.PUBLISHER, NewspaperRole.READER),
                sectionRole(SPORT, SectionRole.REPORTER)), "roles");
    }

    @Test
    void editorInChiefPromotesToEditorInChief() {
        AccountRoleEdit.Change change = AccountRoleEdit.check(CHIEF, SPORT_REPORTER,
                request(List.of(NewspaperRole.EDITOR_IN_CHIEF), sectionRole(SPORT, SectionRole.REPORTER)), SECTIONS);

        assertThat(change.added()).containsExactly(NewspaperRole.EDITOR_IN_CHIEF);
        assertThat(change.removed()).containsExactly(NewspaperRole.READER);
        assertThat(change.sectionRolesChanged()).isFalse();
    }

    @Test
    void publisherMovesReporterAndSectionRolesAreOrderedByPosition() {
        AccountRoleEdit.Change change = AccountRoleEdit.check(PUBLISHER, SPORT_REPORTER,
                request(List.of(NewspaperRole.READER), sectionRole(KULTUR, SectionRole.REPORTER),
                        sectionRole(SPORT, SectionRole.SECTION_EDITOR)), SECTIONS);

        assertThat(change.sectionRoles()).containsExactly(sectionRole(SPORT, SectionRole.SECTION_EDITOR),
                sectionRole(KULTUR, SectionRole.REPORTER));
    }

    @Test
    void unchangedRolesAreAnEmptyChange() {
        AccountRoleEdit.Change change = AccountRoleEdit.check(SPORT_EDITOR, SPORT_REPORTER,
                request(List.of(NewspaperRole.READER), sectionRole(SPORT, SectionRole.REPORTER)), SECTIONS);

        assertThat(change.isEmpty()).isTrue();
    }

    @Test
    void unknownSectionIsInvalidBeforePermissions() {
        assertThatThrownBy(() -> AccountRoleEdit.check(SPORT_EDITOR, SPORT_REPORTER,
                request(List.of(NewspaperRole.PUBLISHER), sectionRole(999L, SectionRole.REPORTER)), SECTIONS))
                .isInstanceOfSatisfying(AccountException.class, e -> {
                    assertThat(e.status()).isEqualTo(Status.BAD_REQUEST);
                    assertThat(e.errors()).extracting(FieldError::field).containsExactly("sectionRoles");
                });
    }

    private static void assertRefused(Newsroom requester, EditRolesRequest request, String field) {
        assertThatThrownBy(() -> AccountRoleEdit.check(requester, SPORT_REPORTER, request, SECTIONS))
                .isInstanceOfSatisfying(AccountException.class, e -> {
                    assertThat(e.status()).isEqualTo(Status.FORBIDDEN);
                    assertThat(e.errors()).extracting(FieldError::field).containsExactly(field);
                });
    }

    private static EditRolesRequest request(List<NewspaperRole> roles, SectionRoleDto... sectionRoles) {
        return new EditRolesRequest(roles, List.of(sectionRoles), null);
    }

    /**
     * No section roles, the marker as given.
     */
    private static EditRolesRequest markerRequest(List<NewspaperRole> roles, boolean marker) {
        return new EditRolesRequest(roles, List.of(), marker);
    }

    private static SectionRoleDto sectionRole(long section, SectionRole role) {
        return new SectionRoleDto(section, role);
    }
}
