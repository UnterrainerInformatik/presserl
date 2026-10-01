package info.unterrainer.presserl.admin.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.api.MeDto
import info.unterrainer.presserl.admin.resources.Res
import info.unterrainer.presserl.admin.resources.account_action_failed
import info.unterrainer.presserl.admin.resources.cancel
import info.unterrainer.presserl.admin.resources.confirm_request_deletion_text
import info.unterrainer.presserl.admin.resources.confirm_request_deletion_title
import info.unterrainer.presserl.admin.resources.confirm_withdraw_deletion_text
import info.unterrainer.presserl.admin.resources.confirm_withdraw_deletion_title
import info.unterrainer.presserl.admin.resources.deletion_explanation
import info.unterrainer.presserl.admin.resources.deletion_explanation_publisher
import info.unterrainer.presserl.admin.resources.deletion_pending_explanation
import info.unterrainer.presserl.admin.resources.deletion_requested_on
import info.unterrainer.presserl.admin.resources.my_account_title
import info.unterrainer.presserl.admin.resources.my_account_username
import info.unterrainer.presserl.admin.resources.request_deletion
import info.unterrainer.presserl.admin.resources.withdraw_deletion
import info.unterrainer.presserl.admin.ui.BackButton
import info.unterrainer.presserl.admin.ui.Banner
import info.unterrainer.presserl.admin.ui.formatTimestamp
import org.jetbrains.compose.resources.stringResource

/**
 * "My account": the user's names and their deletion request. [onChanged] gets the new request time, so the header can
 * mark it.
 */
@Composable
fun MyAccountScreen(api: ApiClient, me: MeDto, onBack: () -> Unit, onChanged: (String?) -> Unit) {
    val scope = rememberCoroutineScope()
    val model = remember { MyAccountModel(scope, me.deletionRequestedAt, api::requestDeletion, api::withdrawDeletionRequest, onChanged) }
    val state by model.state.collectAsState()

    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackButton(onBack)
        Text(stringResource(Res.string.my_account_title), style = MaterialTheme.typography.headlineSmall)
        Text(me.displayName, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(Res.string.my_account_username, me.username), style = MaterialTheme.typography.bodyMedium)
        state.error?.let { Banner(stringResource(Res.string.account_action_failed, it)) }
        val requestedAt = state.requestedAt
        if (requestedAt == null) {
            Text(stringResource(Res.string.deletion_explanation), style = MaterialTheme.typography.bodyMedium)
            if ("PUBLISHER" in me.roles) {
                Text(stringResource(Res.string.deletion_explanation_publisher), style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(onClick = model::ask, enabled = !state.busy) { Text(stringResource(Res.string.request_deletion)) }
        } else {
            Text(
                stringResource(Res.string.deletion_requested_on, formatTimestamp(requestedAt)),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(stringResource(Res.string.deletion_pending_explanation), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = model::ask, enabled = !state.busy) { Text(stringResource(Res.string.withdraw_deletion)) }
        }
    }

    state.pending?.let { step ->
        val (title, text, confirm) = when (step) {
            DeletionStep.REQUEST -> Triple(
                Res.string.confirm_request_deletion_title,
                Res.string.confirm_request_deletion_text,
                Res.string.request_deletion,
            )
            DeletionStep.WITHDRAW -> Triple(
                Res.string.confirm_withdraw_deletion_title,
                Res.string.confirm_withdraw_deletion_text,
                Res.string.withdraw_deletion,
            )
        }
        AlertDialog(
            onDismissRequest = model::cancel,
            title = { Text(stringResource(title)) },
            text = { Text(stringResource(text)) },
            confirmButton = { Button(onClick = model::confirm) { Text(stringResource(confirm)) } },
            dismissButton = { TextButton(onClick = model::cancel) { Text(stringResource(Res.string.cancel)) } },
        )
    }
}
