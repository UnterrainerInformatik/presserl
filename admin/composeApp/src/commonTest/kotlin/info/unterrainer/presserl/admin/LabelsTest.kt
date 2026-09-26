package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.resources.Res
import info.unterrainer.presserl.admin.resources.role_editor_in_chief
import info.unterrainer.presserl.admin.resources.role_publisher
import info.unterrainer.presserl.admin.resources.role_reader
import info.unterrainer.presserl.admin.resources.status_draft
import info.unterrainer.presserl.admin.resources.status_offline
import info.unterrainer.presserl.admin.resources.status_published
import info.unterrainer.presserl.admin.resources.status_submitted
import info.unterrainer.presserl.admin.ui.account.canManageAccounts
import info.unterrainer.presserl.admin.ui.roleLabel
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
    fun onlyPublishersAndEditorsInChiefManageAccounts() {
        assertTrue(canManageAccounts(listOf("PUBLISHER")))
        assertTrue(canManageAccounts(listOf("EDITOR_IN_CHIEF", "READER")))
        assertFalse(canManageAccounts(listOf("READER")))
        assertFalse(canManageAccounts(emptyList()))
    }
}
