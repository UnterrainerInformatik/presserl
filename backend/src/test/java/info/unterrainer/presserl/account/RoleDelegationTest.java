package info.unterrainer.presserl.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import info.unterrainer.presserl.auth.CurrentUser;
import info.unterrainer.presserl.auth.NewspaperRole;

class RoleDelegationTest {

    @Test
    void publisherAssignsAllNewspaperRoles() {
        assertThat(RoleDelegation.assignableBy(user(NewspaperRole.PUBLISHER)))
                .containsExactly(NewspaperRole.PUBLISHER, NewspaperRole.EDITOR_IN_CHIEF, NewspaperRole.READER);
    }

    @Test
    void publisherWhoIsAlsoEditorInChiefAssignsAllNewspaperRoles() {
        assertThat(RoleDelegation.assignableBy(user(NewspaperRole.PUBLISHER, NewspaperRole.EDITOR_IN_CHIEF)))
                .containsExactly(NewspaperRole.PUBLISHER, NewspaperRole.EDITOR_IN_CHIEF, NewspaperRole.READER);
    }

    @Test
    void editorInChiefAssignsOwnLevelAndBelow() {
        assertThat(RoleDelegation.assignableBy(user(NewspaperRole.EDITOR_IN_CHIEF, NewspaperRole.READER)))
                .containsExactly(NewspaperRole.EDITOR_IN_CHIEF, NewspaperRole.READER);
    }

    @Test
    void readerAssignsNothing() {
        assertThat(RoleDelegation.assignableBy(user(NewspaperRole.READER))).isEmpty();
    }

    @Test
    void userWithoutRolesAssignsNothing() {
        assertThat(RoleDelegation.assignableBy(user())).isEmpty();
    }

    private static CurrentUser user(NewspaperRole... roles) {
        return new CurrentUser("sub", "someone", "Someone", List.of(roles));
    }
}
