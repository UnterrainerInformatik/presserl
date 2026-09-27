package info.unterrainer.presserl.section;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import info.unterrainer.presserl.auth.CurrentUser;
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

    static Newsroom newsroom(Map<Long, SectionRole> sectionRoles, NewspaperRole... roles) {
        return new Newsroom(new CurrentUser("sub", "someone", "Someone", List.of(roles)), sectionRoles);
    }
}
