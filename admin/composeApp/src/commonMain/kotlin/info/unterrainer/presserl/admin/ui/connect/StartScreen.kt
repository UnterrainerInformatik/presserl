package info.unterrainer.presserl.admin.ui.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import info.unterrainer.presserl.admin.resources.Res
import info.unterrainer.presserl.admin.resources.connect_address_example
import info.unterrainer.presserl.admin.resources.connect_address_label
import info.unterrainer.presserl.admin.resources.connect_address_submit
import info.unterrainer.presserl.admin.resources.connect_checking
import info.unterrainer.presserl.admin.resources.connect_error_http
import info.unterrainer.presserl.admin.resources.connect_error_no_presserl
import info.unterrainer.presserl.admin.resources.connect_error_not_a_slip
import info.unterrainer.presserl.admin.resources.connect_error_scanner
import info.unterrainer.presserl.admin.resources.connect_intro
import info.unterrainer.presserl.admin.resources.connect_or_address
import info.unterrainer.presserl.admin.resources.connect_scan
import info.unterrainer.presserl.admin.resources.privacy_policy
import info.unterrainer.presserl.admin.ui.Banner
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The first screen of the mobile app without a known server: scan the account slip or enter the newspaper's address;
 * while [state] is [ConnectionState.Checking] the inputs wait.
 */
@Composable
fun StartScreen(state: ConnectionState, onScan: () -> Unit, onAddress: (String) -> Unit, onPrivacyPolicy: () -> Unit) {
    var address by rememberSaveable { mutableStateOf("") }
    val checking = state is ConnectionState.Checking
    val error = (state as? ConnectionState.Start)?.error
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("presserl", style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(Res.string.connect_intro), style = MaterialTheme.typography.bodyLarge)
            if (error != null) Banner(stringResource(error.text))
            Button(onClick = onScan, enabled = !checking, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.connect_scan))
            }
            Text(stringResource(Res.string.connect_or_address), style = MaterialTheme.typography.bodyMedium)
            val submit = { if (address.isNotBlank()) onAddress(address) }
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                enabled = !checking,
                singleLine = true,
                label = { Text(stringResource(Res.string.connect_address_label)) },
                placeholder = { Text(stringResource(Res.string.connect_address_example)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = submit, enabled = !checking && address.isNotBlank()) {
                    Text(stringResource(Res.string.connect_address_submit))
                }
                if (checking) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Text(stringResource(Res.string.connect_checking), style = MaterialTheme.typography.bodyMedium)
                }
            }
            TextButton(onClick = onPrivacyPolicy) { Text(stringResource(Res.string.privacy_policy)) }
        }
    }
}

private val ConnectError.text: StringResource
    get() = when (this) {
        ConnectError.NO_PRESSERL_SERVER -> Res.string.connect_error_no_presserl
        ConnectError.HTTP_REFUSED -> Res.string.connect_error_http
        ConnectError.NOT_A_SLIP -> Res.string.connect_error_not_a_slip
        ConnectError.SCANNER_UNAVAILABLE -> Res.string.connect_error_scanner
    }
