package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.resources.Res
import info.unterrainer.presserl.admin.resources.color_pink
import info.unterrainer.presserl.admin.resources.color_red
import info.unterrainer.presserl.admin.resources.decision_approved
import info.unterrainer.presserl.admin.resources.decision_rejected
import info.unterrainer.presserl.admin.resources.role_editor_in_chief
import info.unterrainer.presserl.admin.resources.role_publisher
import info.unterrainer.presserl.admin.resources.role_reader
import info.unterrainer.presserl.admin.resources.role_reporter
import info.unterrainer.presserl.admin.resources.role_section_editor
import info.unterrainer.presserl.admin.resources.role_sectionless_reporter
import info.unterrainer.presserl.admin.resources.status_draft
import info.unterrainer.presserl.admin.resources.status_offline
import info.unterrainer.presserl.admin.resources.status_published
import info.unterrainer.presserl.admin.resources.status_submitted
import info.unterrainer.presserl.admin.api.AuthorDto
import info.unterrainer.presserl.admin.ui.approvalLevelLabel
import info.unterrainer.presserl.admin.ui.authorName
import info.unterrainer.presserl.admin.ui.colorLabel
import info.unterrainer.presserl.admin.ui.decisionLabel
import info.unterrainer.presserl.admin.ui.roleLabel
import info.unterrainer.presserl.admin.ui.section.SECTION_COLORS
import info.unterrainer.presserl.admin.ui.section.defaultSectionColor
import info.unterrainer.presserl.admin.ui.sectionRoleLabel
import info.unterrainer.presserl.admin.ui.sectionlessReporterLabel
import info.unterrainer.presserl.admin.ui.statusLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LabelsTest {

    @Test
    fun authorNameIsDisplayNameElseUsernameAndNullForADeletedAccount() {
        assertEquals("Anna Berger", authorName(AuthorDto("anna", "Anna Berger")))
        assertEquals("anna", authorName(AuthorDto("anna", " ")))
        assertNull(authorName(AuthorDto(null, null)))
    }

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
    fun sectionlessReporterHasItsOwnLabel() {
        assertEquals(Res.string.role_sectionless_reporter, sectionlessReporterLabel)
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
    fun everyApprovalLevelIsLabelledWithItsRole() {
        assertEquals(
            listOf(Res.string.role_section_editor, Res.string.role_editor_in_chief, Res.string.role_publisher),
            listOf("SECTION_EDITOR", "EDITOR_IN_CHIEF", "PUBLISHER").map(::approvalLevelLabel),
        )
        assertNull(approvalLevelLabel("REPORTER"))
    }

    @Test
    fun everyReviewDecisionHasALabel() {
        assertEquals(
            listOf(Res.string.decision_approved, Res.string.decision_rejected),
            listOf("APPROVED", "REJECTED").map(::decisionLabel),
        )
        assertNull(decisionLabel("WITHDRAWN"))
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
}
