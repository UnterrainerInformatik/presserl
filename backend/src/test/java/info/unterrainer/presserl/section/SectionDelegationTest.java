package info.unterrainer.presserl.section;

import static info.unterrainer.presserl.section.NewsroomTest.newsroom;
import static info.unterrainer.presserl.section.SectionRole.REPORTER;
import static info.unterrainer.presserl.section.SectionRole.SECTION_EDITOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import info.unterrainer.presserl.auth.NewspaperRole;

class SectionDelegationTest {

    private static final long SPORT = 1L;
    private static final long KULTUR = 2L;

    @Test
    void publisherAssignsBothRolesEverywhere() {
        Newsroom publisher = newsroom(Map.of(), NewspaperRole.PUBLISHER);
        assertThat(SectionDelegation.assignable(publisher, SPORT)).containsExactly(SECTION_EDITOR, REPORTER);
        assertThat(SectionDelegation.assignable(publisher, KULTUR)).containsExactly(SECTION_EDITOR, REPORTER);
    }

    @Test
    void editorInChiefAssignsBothRolesEverywhere() {
        assertThat(SectionDelegation.assignable(newsroom(Map.of(), NewspaperRole.EDITOR_IN_CHIEF), KULTUR))
                .containsExactly(SECTION_EDITOR, REPORTER);
    }

    @Test
    void sectionEditorAssignsBothRolesInOwnSectionOnly() {
        Newsroom editor = newsroom(Map.of(SPORT, SECTION_EDITOR));
        assertThat(SectionDelegation.assignable(editor, SPORT)).containsExactly(SECTION_EDITOR, REPORTER);
        assertThat(SectionDelegation.assignable(editor, KULTUR)).isEmpty();
    }

    @Test
    void reporterAssignsNothing() {
        assertThat(SectionDelegation.assignable(newsroom(Map.of(SPORT, REPORTER)), SPORT)).isEmpty();
    }

    @Test
    void readerAssignsNothing() {
        assertThat(SectionDelegation.assignable(newsroom(Map.of(), NewspaperRole.READER), SPORT)).isEmpty();
    }

    @Test
    void changeNeedsOldAndNewRole() {
        Newsroom editor = newsroom(Map.of(SPORT, SECTION_EDITOR));
        assertThat(SectionDelegation.mayChange(editor, SPORT, null, REPORTER)).isTrue();
        assertThat(SectionDelegation.mayChange(editor, SPORT, REPORTER, SECTION_EDITOR)).isTrue();
        assertThat(SectionDelegation.mayChange(editor, KULTUR, null, REPORTER)).isFalse();
        assertThat(SectionDelegation.mayChange(newsroom(Map.of(SPORT, REPORTER)), SPORT, null, REPORTER)).isFalse();
    }

    @Test
    void removalNeedsTheCurrentRole() {
        assertThat(SectionDelegation.mayRemove(newsroom(Map.of(SPORT, SECTION_EDITOR)), SPORT, SECTION_EDITOR))
                .isTrue();
        assertThat(SectionDelegation.mayRemove(newsroom(Map.of(SPORT, SECTION_EDITOR)), KULTUR, REPORTER)).isFalse();
        assertThat(SectionDelegation.mayRemove(newsroom(Map.of(SPORT, REPORTER)), SPORT, REPORTER)).isFalse();
    }
}
