package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.ui.roleLabel
import kotlin.test.Test
import kotlin.test.assertEquals

class RolesTest {

    @Test
    fun newspaperRolesHaveLabels() {
        assertEquals("Publisher", roleLabel("PUBLISHER"))
        assertEquals("Editor-in-chief", roleLabel("EDITOR_IN_CHIEF"))
        assertEquals("Reader", roleLabel("READER"))
    }

    @Test
    fun unknownRoleIsShownAsIs() {
        assertEquals("SECTION_EDITOR", roleLabel("SECTION_EDITOR"))
    }
}
