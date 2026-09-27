package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.api.MeDto
import info.unterrainer.presserl.admin.api.MySectionRoleDto
import info.unterrainer.presserl.admin.resources.Res
import info.unterrainer.presserl.admin.resources.color_pink
import info.unterrainer.presserl.admin.resources.color_red
import info.unterrainer.presserl.admin.resources.role_editor_in_chief
import info.unterrainer.presserl.admin.resources.role_publisher
import info.unterrainer.presserl.admin.resources.role_reader
import info.unterrainer.presserl.admin.resources.role_reporter
import info.unterrainer.presserl.admin.resources.role_section_editor
import info.unterrainer.presserl.admin.resources.status_draft
import info.unterrainer.presserl.admin.resources.status_offline
import info.unterrainer.presserl.admin.resources.status_published
import info.unterrainer.presserl.admin.resources.status_submitted
import info.unterrainer.presserl.admin.ui.account.canAdministerAccounts
import info.unterrainer.presserl.admin.ui.colorLabel
import info.unterrainer.presserl.admin.ui.roleLabel
import info.unterrainer.presserl.admin.ui.section.SECTION_COLORS
import info.unterrainer.presserl.admin.ui.section.defaultSectionColor
import info.unterrainer.presserl.admin.ui.sectionRoleLabel
import info.unterrainer.presserl.admin.ui.statusLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LabelsTest {

    @Test
    fun everyNewspaperRoleHasALabel() {
        assertEquals(
            listOf(Res.string.role_publisher, Res.string.role_editor_in_chief, Res.string.role_reader),
            listOf("PUBLISHER", "EDITOR_IN_CHIEF", "READER").map(::roleLabel),
        )
    }

    @Test
    fun everyArticleStatusHasALabel() {
        assertEquals(
            listOf(Res.string.status_draft, Res.string.status_submitted, Res.string.status_published, Res.string.status_offline),
            listOf("DRAFT", "SUBMITTED", "PUBLISHED", "OFFLINE").map(::statusLabel),
        )
    }

    @Test
    fun unknownValuesHaveNoLabel() {
        assertNull(roleLabel("SECTION_EDITOR"))
        assertNull(statusLabel("ARCHIVED"))
    }

    @Test
    fun everySectionRoleHasALabel() {
        assertEquals(
            listOf(Res.string.role_section_editor, Res.string.role_reporter),
            listOf("SECTION_EDITOR", "REPORTER").map(::sectionRoleLabel),
        )
        assertNull(sectionRoleLabel("READER"))
    }

    @Test
    fun everyPaletteColourHasALabel() {
        assertEquals(8, SECTION_COLORS.mapNotNull(::colorLabel).toSet().size)
        assertEquals(Res.string.color_red, colorLabel("red"))
        assertEquals(Res.string.color_pink, colorLabel("pink"))
        assertNull(colorLabel("#ff0000"))
    }

    @Test
    fun defaultColourFollowsThePalette() {
        assertEquals("red", defaultSectionColor(0))
        assertEquals("orange", defaultSectionColor(1))
        assertEquals("red", defaultSectionColor(8))
    }

    @Test
    fun administratorsAndSectionEditorsAdministerAccounts() {
        assertTrue(canAdministerAccounts(me(listOf("PUBLISHER"))))
        assertTrue(canAdministerAccounts(me(listOf("EDITOR_IN_CHIEF", "READER"))))
        assertTrue(canAdministerAccounts(me(emptyList(), "SECTION_EDITOR")))
        assertFalse(canAdministerAccounts(me(emptyList(), "REPORTER")))
        assertFalse(canAdministerAccounts(me(listOf("READER"))))
        assertFalse(canAdministerAccounts(me(emptyList())))
    }

    private fun me(roles: List<String>, vararg sectionRoles: String) =
        MeDto("someone", "Someone", roles, sectionRoles.mapIndexed { index, role -> MySectionRoleDto(index + 1L, "Section", role) })
}
