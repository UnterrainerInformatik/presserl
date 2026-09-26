package info.unterrainer.presserl.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class NewspaperRoleTest {

    @Test
    void mapsNewspaperGroupsAndIgnoresOthers() {
        assertThat(NewspaperRole.fromGroups(Set.of("reader", "offline_access", "publisher", "editor-in-chief")))
                .containsExactly(NewspaperRole.PUBLISHER, NewspaperRole.EDITOR_IN_CHIEF, NewspaperRole.READER);
    }

    @Test
    void noGroupsMeansNoRoles() {
        assertThat(NewspaperRole.fromGroups(List.of())).isEmpty();
    }
}
