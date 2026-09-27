package info.unterrainer.presserl.admin.ui.newspaper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.resources.Res
import info.unterrainer.presserl.admin.resources.loading
import info.unterrainer.presserl.admin.resources.newspaper_save_failed
import info.unterrainer.presserl.admin.resources.newspaper_settings_title
import info.unterrainer.presserl.admin.resources.newspaper_text_size
import info.unterrainer.presserl.admin.resources.newspaper_text_size_effective
import info.unterrainer.presserl.admin.resources.newspaper_text_size_hint
import info.unterrainer.presserl.admin.resources.save_saving
import info.unterrainer.presserl.admin.resources.text_size_default
import info.unterrainer.presserl.admin.resources.text_size_l
import info.unterrainer.presserl.admin.resources.text_size_m
import info.unterrainer.presserl.admin.resources.text_size_s
import info.unterrainer.presserl.admin.resources.text_size_xl
import org.jetbrains.compose.resources.stringResource

/** The newspaper settings: the default text size of the reader, saved on selection. */
@Composable
fun NewspaperScreen(api: ApiClient) {
    val scope = rememberCoroutineScope()
    val model = remember { NewspaperSettingsModel(scope, load = api::newspaper, save = api::updateNewspaperSettings) }
    val state by model.state.collectAsState()
    LaunchedEffect(model) { model.load() }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.newspaper_settings_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(Res.string.newspaper_text_size), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(Res.string.newspaper_text_size_hint), style = MaterialTheme.typography.bodyMedium)
        if (state.loading) {
            Text(stringResource(Res.string.loading))
        } else {
            Column(Modifier.selectableGroup()) {
                TextSizeChoice(stringResource(Res.string.text_size_default), state.textSize == null, !state.saving) {
                    model.textSize(null)
                }
                for ((size, px) in TEXT_SIZES) {
                    TextSizeChoice(stringResource(textSizeName(size), px), state.textSize == size, !state.saving) {
                        model.textSize(size)
                    }
                }
            }
            state.effectiveTextSize?.let { effective ->
                val px = TEXT_SIZES.firstOrNull { it.first == effective }?.second
                val name = if (px == null) effective else stringResource(textSizeName(effective), px)
                Text(stringResource(Res.string.newspaper_text_size_effective, name), style = MaterialTheme.typography.bodyMedium)
            }
            if (state.saving) Text(stringResource(Res.string.save_saving), style = MaterialTheme.typography.bodySmall)
        }
        state.error?.let {
            Text(stringResource(Res.string.newspaper_save_failed, it), color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun textSizeName(size: String) = when (size) {
    "s" -> Res.string.text_size_s
    "m" -> Res.string.text_size_m
    "l" -> Res.string.text_size_l
    else -> Res.string.text_size_xl
}

@Composable
private fun TextSizeChoice(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(label)
    }
}
