package info.unterrainer.presserl.section;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import info.unterrainer.presserl.auth.CurrentUser;
import info.unterrainer.presserl.auth.NewspaperAction;
import info.unterrainer.presserl.auth.NewspaperRole;

class NewsroomTest {

    @Test
    void publisherAndEditorInChiefAreAdministrators() {
        assertThat(newsroom(Map.of(), NewspaperRole.PUBLISHER).isAdministrator()).isTrue();
        assertThat(newsroom(Map.of(), NewspaperRole.EDITOR_IN_CHIEF).isAdministrator()).isTrue();
    }

    @Test
    void readerAndSectionEditorAreNoAdministrators() {
        assertThat(newsroom(Map.of(), NewspaperRole.READER).isAdministrator()).isFalse();
        assertThat(newsroom(Map.of(1L, SectionRole.SECTION_EDITOR)).isAdministrator()).isFalse();
    }

    @Test
    void sectionEditorAnywhere() {
        assertThat(newsroom(Map.of(1L, SectionRole.REPORTER, 2L, SectionRole.SECTION_EDITOR))
                .isSectionEditorAnywhere()).isTrue();
        assertThat(newsroom(Map.of(1L, SectionRole.REPORTER)).isSectionEditorAnywhere()).isFalse();
        assertThat(newsroom(Map.of(), NewspaperRole.PUBLISHER).isSectionEditorAnywhere()).isFalse();
    }

    @Test
    void accountAdministrationForAdministratorsAndSectionEditors() {
        assertThat(newsroom(Map.of(), NewspaperRole.EDITOR_IN_CHIEF).mayAdministerAccounts()).isTrue();
        assertThat(newsroom(Map.of(1L, SectionRole.SECTION_EDITOR)).mayAdministerAccounts()).isTrue();
        assertThat(newsroom(Map.of(1L, SectionRole.REPORTER), NewspaperRole.READER).mayAdministerAccounts())
                .isFalse();
    }

    @Test
    void roleInSection() {
        Newsroom newsroom = newsroom(Map.of(1L, SectionRole.REPORTER));
        assertThat(newsroom.roleIn(1L)).contains(SectionRole.REPORTER);
        assertThat(newsroom.roleIn(2L)).isEmpty();
    }

    @Test
    void writersAreAdministratorsAndSectionMembers() {
        assertThat(newsroom(Map.of(), NewspaperRole.PUBLISHER).isWriter()).isTrue();
        assertThat(newsroom(Map.of(), NewspaperRole.EDITOR_IN_CHIEF).isWriter()).isTrue();
        assertThat(newsroom(Map.of(1L, SectionRole.REPORTER), NewspaperRole.READER).isWriter()).isTrue();
        assertThat(newsroom(Map.of(1L, SectionRole.SECTION_EDITOR)).isWriter()).isTrue();
        assertThat(newsroom(Map.of(), NewspaperRole.READER).isWriter()).isFalse();
    }

    @Test
    void administratorsWriteEverywhereMembersInTheirSections() {
        assertThat(newsroom(Map.of(), NewspaperRole.EDITOR_IN_CHIEF).mayWriteIn(7L)).isTrue();
        Newsroom member = newsroom(Map.of(1L, SectionRole.REPORTER, 2L, SectionRole.SECTION_EDITOR));
        assertThat(member.mayWriteIn(1L)).isTrue();
        assertThat(member.mayWriteIn(2L)).isTrue();
        assertThat(member.mayWriteIn(3L)).isFalse();
    }

    @Test
    void sectionEditorOfASection() {
        Newsroom member = newsroom(Map.of(1L, SectionRole.REPORTER, 2L, SectionRole.SECTION_EDITOR));
        assertThat(member.isSectionEditorOf(1L)).isFalse();
        assertThat(member.isSectionEditorOf(2L)).isTrue();
        assertThat(member.isSectionEditorOf(3L)).isFalse();
        assertThat(newsroom(Map.of(), NewspaperRole.PUBLISHER).isSectionEditorOf(1L)).isFalse();
    }

    @Test
    void sectionManagementForAdministratorsOnly() {
        assertThat(newsroom(Map.of(), NewspaperRole.PUBLISHER).mayManageSections()).isTrue();
        assertThat(newsroom(Map.of(), NewspaperRole.EDITOR_IN_CHIEF).mayManageSections()).isTrue();
        assertThat(newsroom(Map.of(1L, SectionRole.SECTION_EDITOR)).mayManageSections()).isFalse();
    }

    @Test
    void sectionRolesAreAssignedByAdministratorsAndSectionEditorsOfTheSection() {
        assertThat(newsroom(Map.of(), NewspaperRole.PUBLISHER).mayAssignSectionRolesIn(7L)).isTrue();
        Newsroom member = newsroom(Map.of(1L, SectionRole.REPORTER, 2L, SectionRole.SECTION_EDITOR));
        assertThat(member.mayAssignSectionRolesIn(1L)).isFalse();
        assertThat(member.mayAssignSectionRolesIn(2L)).isTrue();
        assertThat(member.mayAssignSectionRolesAnywhere()).isTrue();
        assertThat(newsroom(Map.of(1L, SectionRole.REPORTER)).mayAssignSectionRolesAnywhere()).isFalse();
    }

    // --- allowedActions ---------------------------------------------------------------------

    @Test
    void publisherMayDoEverything() {
        assertThat(newsroom(Map.of(), NewspaperRole.PUBLISHER).allowedActions()).containsExactly(
                NewspaperAction.WRITE_ARTICLES, NewspaperAction.USE_MEDIA, NewspaperAction.MANAGE_SECTIONS,
                NewspaperAction.ASSIGN_SECTION_ROLES, NewspaperAction.MANAGE_ISSUES,
                NewspaperAction.ADMINISTER_ACCOUNTS, NewspaperAction.CONFIGURE_NEWSPAPER,
                NewspaperAction.CONFIGURE_SPELL_CHECK, NewspaperAction.CONFIGURE_CORRECTIONS);
    }

    @Test
    void editorInChiefMayDoEverythingButChooseTheSpellCheckHelp() {
        assertThat(newsroom(Map.of(), NewspaperRole.EDITOR_IN_CHIEF).allowedActions()).containsExactly(
                NewspaperAction.WRITE_ARTICLES, NewspaperAction.USE_MEDIA, NewspaperAction.MANAGE_SECTIONS,
                NewspaperAction.ASSIGN_SECTION_ROLES, NewspaperAction.MANAGE_ISSUES,
                NewspaperAction.ADMINISTER_ACCOUNTS, NewspaperAction.CONFIGURE_NEWSPAPER);
    }

    @Test
    void readerOnlyMayDoNothing() {
        assertThat(newsroom(Map.of(), NewspaperRole.READER).allowedActions()).isEmpty();
    }

    @Test
    void userWithoutRolesMayDoNothing() {
        assertThat(newsroom(Map.of()).allowedActions()).isEmpty();
    }

    @Test
    void reporterMayOnlyWrite() {
        assertThat(newsroom(Map.of(1L, SectionRole.REPORTER)).allowedActions())
                .containsExactly(NewspaperAction.WRITE_ARTICLES, NewspaperAction.USE_MEDIA);
    }

    @Test
    void sectionlessReporterUsesMediaOnly() {
        Newsroom marked = new Newsroom(new CurrentUser("sub", "pia", "Pia", List.of()), Map.of(), true);
        assertThat(marked.allowedActions()).containsExactly(NewspaperAction.USE_MEDIA);
        assertThat(marked.isWriter()).isFalse();
        assertThat(marked.mayUseMedia()).isTrue();
    }

    @Test
    void sectionEditorMayWriteAssignAndAdministerAccounts() {
        assertThat(newsroom(Map.of(1L, SectionRole.SECTION_EDITOR)).allowedActions()).containsExactly(
                NewspaperAction.WRITE_ARTICLES, NewspaperAction.USE_MEDIA, NewspaperAction.ASSIGN_SECTION_ROLES,
                NewspaperAction.ADMINISTER_ACCOUNTS);
    }

    @Test
    void reporterInOneSectionAndSectionEditorInAnother() {
        assertThat(newsroom(Map.of(1L, SectionRole.REPORTER, 2L, SectionRole.SECTION_EDITOR)).allowedActions())
                .containsExactly(NewspaperAction.WRITE_ARTICLES, NewspaperAction.USE_MEDIA,
                        NewspaperAction.ASSIGN_SECTION_ROLES,
                        NewspaperAction.ADMINISTER_ACCOUNTS);
    }

    static Newsroom newsroom(Map<Long, SectionRole> sectionRoles, NewspaperRole... roles) {
        return new Newsroom(new CurrentUser("sub", "someone", "Someone", List.of(roles)), sectionRoles);
    }
}
