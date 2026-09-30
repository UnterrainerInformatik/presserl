package info.unterrainer.presserl.admin.ui

import androidx.compose.runtime.Composable
import info.unterrainer.presserl.admin.resources.Res
import info.unterrainer.presserl.admin.resources.color_blue
import info.unterrainer.presserl.admin.resources.color_green
import info.unterrainer.presserl.admin.resources.color_orange
import info.unterrainer.presserl.admin.resources.color_pink
import info.unterrainer.presserl.admin.resources.color_purple
import info.unterrainer.presserl.admin.resources.color_red
import info.unterrainer.presserl.admin.resources.color_teal
import info.unterrainer.presserl.admin.resources.color_yellow
import info.unterrainer.presserl.admin.resources.decision_approved
import info.unterrainer.presserl.admin.resources.decision_rejected
import info.unterrainer.presserl.admin.resources.front_page_marker
import info.unterrainer.presserl.admin.resources.in_no_issue
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
import info.unterrainer.presserl.admin.resources.waiting_for_approval
import info.unterrainer.presserl.admin.resources.waits_for_issue
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Label of a newspaper-wide role from `GET /api/me`; `null` for a role this app does not know. */
fun roleLabel(role: String): StringResource? = when (role) {
    "PUBLISHER" -> Res.string.role_publisher
    "EDITOR_IN_CHIEF" -> Res.string.role_editor_in_chief
    "READER" -> Res.string.role_reader
    else -> null
}

/** Label of the sectionless-reporter marker of `GET /api/me` and the account list. */
val sectionlessReporterLabel: StringResource get() = Res.string.role_sectionless_reporter

/** Label of a section role; `null` for a role this app does not know. */
fun sectionRoleLabel(role: String): StringResource? = when (role) {
    "SECTION_EDITOR" -> Res.string.role_section_editor
    "REPORTER" -> Res.string.role_reporter
    else -> null
}

/** Label of an approval level (`pendingLevel`, review level): the role that holds it; `null` for an unknown level. */
fun approvalLevelLabel(level: String): StringResource? = when (level) {
    "SECTION_EDITOR" -> Res.string.role_section_editor
    "EDITOR_IN_CHIEF" -> Res.string.role_editor_in_chief
    "PUBLISHER" -> Res.string.role_publisher
    else -> null
}

/** Label of a review decision; `null` for an unknown decision. */
fun decisionLabel(decision: String): StringResource? = when (decision) {
    "APPROVED" -> Res.string.decision_approved
    "REJECTED" -> Res.string.decision_rejected
    else -> null
}

/** Label of a section palette colour; `null` for a colour this app does not know. */
fun colorLabel(color: String): StringResource? = when (color) {
    "red" -> Res.string.color_red
    "orange" -> Res.string.color_orange
    "yellow" -> Res.string.color_yellow
    "green" -> Res.string.color_green
    "teal" -> Res.string.color_teal
    "blue" -> Res.string.color_blue
    "purple" -> Res.string.color_purple
    "pink" -> Res.string.color_pink
    else -> null
}

/** Label of an article status; `null` for a status this app does not know. */
fun statusLabel(status: String): StringResource? = when (status) {
    "DRAFT" -> Res.string.status_draft
    "SUBMITTED" -> Res.string.status_submitted
    "PUBLISHED" -> Res.string.status_published
    "OFFLINE" -> Res.string.status_offline
    else -> null
}

/** Unknown values are shown as they are. */
@Composable
fun roleText(role: String): String = roleLabel(role)?.let { stringResource(it) } ?: role

@Composable
fun statusText(status: String): String = statusLabel(status)?.let { stringResource(it) } ?: status

@Composable
fun sectionRoleText(role: String): String = sectionRoleLabel(role)?.let { stringResource(it) } ?: role

@Composable
fun approvalLevelText(level: String): String = approvalLevelLabel(level)?.let { stringResource(it) } ?: level

/** "Waits for approval by …" for an article with a pending level. */
@Composable
fun waitingText(level: String): String = stringResource(Res.string.waiting_for_approval, approvalLevelText(level))

@Composable
fun decisionText(decision: String): String = decisionLabel(decision)?.let { stringResource(it) } ?: decision

@Composable
fun colorText(color: String): String = colorLabel(color)?.let { stringResource(it) } ?: color

/** Why a published article is not shown to readers although it is published: its issue is not live, or it has none. */
sealed interface IssueWait {
    data class ForIssue(val number: Int) : IssueWait
    data object NoIssue : IssueWait
}

/** The [IssueWait] of a `PUBLISHED` article readers do not see; `null` for any other article. */
fun issueWait(status: String, readerVisible: Boolean, issueNumber: Int?): IssueWait? = when {
    status != "PUBLISHED" || readerVisible -> null
    issueNumber != null -> IssueWait.ForIssue(issueNumber)
    else -> IssueWait.NoIssue
}

/** "Waits for issue N" / "In no issue" for a published article readers do not see, `null` otherwise. */
@Composable
fun issueWaitText(status: String, readerVisible: Boolean, issueNumber: Int?): String? =
    when (val wait = issueWait(status, readerVisible, issueNumber)) {
        is IssueWait.ForIssue -> stringResource(Res.string.waits_for_issue, wait.number)
        IssueWait.NoIssue -> stringResource(Res.string.in_no_issue)
        null -> null
    }

/** "Front page N" for a weighted article, `null` without weight. */
@Composable
fun frontPageText(weight: Int?): String? = weight?.let { stringResource(Res.string.front_page_marker, it) }
