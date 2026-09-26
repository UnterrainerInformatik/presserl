package info.unterrainer.presserl.admin.ui

import androidx.compose.runtime.Composable
import info.unterrainer.presserl.admin.resources.Res
import info.unterrainer.presserl.admin.resources.role_editor_in_chief
import info.unterrainer.presserl.admin.resources.role_publisher
import info.unterrainer.presserl.admin.resources.role_reader
import info.unterrainer.presserl.admin.resources.status_draft
import info.unterrainer.presserl.admin.resources.status_offline
import info.unterrainer.presserl.admin.resources.status_published
import info.unterrainer.presserl.admin.resources.status_submitted
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Label of a newspaper-wide role from `GET /api/me`; `null` for a role this app does not know. */
fun roleLabel(role: String): StringResource? = when (role) {
    "PUBLISHER" -> Res.string.role_publisher
    "EDITOR_IN_CHIEF" -> Res.string.role_editor_in_chief
    "READER" -> Res.string.role_reader
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
