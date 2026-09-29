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
import info.unterrainer.presserl.admin.resources.spell_help_default
import info.unterrainer.presserl.admin.resources.spell_help_effective
import info.unterrainer.presserl.admin.resources.spell_help_hint
import info.unterrainer.presserl.admin.resources.spell_help_marks
import info.unterrainer.presserl.admin.resources.spell_help_marks_description
import info.unterrainer.presserl.admin.resources.spell_help_messages
import info.unterrainer.presserl.admin.resources.spell_help_messages_description
import info.unterrainer.presserl.admin.resources.spell_help_publisher_only
import info.unterrainer.presserl.admin.resources.spell_help_suggestions
import info.unterrainer.presserl.admin.resources.spell_help_suggestions_description
import info.unterrainer.presserl.admin.resources.spell_help_title
import info.unterrainer.presserl.admin.resources.text_size_default
import info.unterrainer.presserl.admin.resources.text_size_l
import info.unterrainer.presserl.admin.resources.text_size_m
import info.unterrainer.presserl.admin.resources.text_size_s
import info.unterrainer.presserl.admin.resources.text_size_xl
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The newspaper settings, saved on selection: the default text size of the reader and, when the installation offers the
 * spell check ([spellCheck]), the spell-check help, which only users with [mayConfigureSpellCheck]
 * (`CONFIGURE_SPELL_CHECK`) change; everyone else sees it read-only.
 */
@Composable
fun NewspaperScreen(api: ApiClient, spellCheck: Boolean, mayConfigureSpellCheck: Boolean) {
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
                SettingChoice(stringResource(Res.string.text_size_default), null, state.textSize == null, !state.saving) {
                    model.choose(READER_TEXT_SIZE, null)
                }
                for ((size, px) in TEXT_SIZES) {
                    SettingChoice(stringResource(textSizeName(size), px), null, state.textSize == size, !state.saving) {
                        model.choose(READER_TEXT_SIZE, size)
                    }
                }
            }
            state.effectiveTextSize?.let { effective ->
                val px = TEXT_SIZES.firstOrNull { it.first == effective }?.second
                val name = if (px == null) effective else stringResource(textSizeName(effective), px)
                Text(stringResource(Res.string.newspaper_text_size_effective, name), style = MaterialTheme.typography.bodyMedium)
            }
            if (spellCheck) SpellCheckHelpSection(state, mayConfigureSpellCheck) { model.choose(SPELL_CHECK_HELP, it) }
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
private fun SpellCheckHelpSection(state: NewspaperSettingsState, editable: Boolean, onChoose: (String?) -> Unit) {
    val enabled = editable && !state.saving
    Text(stringResource(Res.string.spell_help_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(Res.string.spell_help_hint), style = MaterialTheme.typography.bodyMedium)
    Column(Modifier.selectableGroup()) {
        SettingChoice(stringResource(Res.string.spell_help_default), null, state.spellCheckHelp == null, enabled) {
            onChoose(null)
        }
        for (level in SPELL_CHECK_HELP_LEVELS) {
            val (name, description) = spellCheckHelpTexts(level)
            SettingChoice(stringResource(name), stringResource(description), state.spellCheckHelp == level, enabled) {
                onChoose(level)
            }
        }
    }
    state.effectiveSpellCheckHelp?.let { effective ->
        val name = if (effective in SPELL_CHECK_HELP_LEVELS) stringResource(spellCheckHelpTexts(effective).first) else effective
        Text(stringResource(Res.string.spell_help_effective, name), style = MaterialTheme.typography.bodyMedium)
    }
    if (!editable) Text(stringResource(Res.string.spell_help_publisher_only), style = MaterialTheme.typography.bodyMedium)
}

private fun spellCheckHelpTexts(level: String): Pair<StringResource, StringResource> = when (level) {
    "suggestions" -> Res.string.spell_help_suggestions to Res.string.spell_help_suggestions_description
    "messages" -> Res.string.spell_help_messages to Res.string.spell_help_messages_description
    else -> Res.string.spell_help_marks to Res.string.spell_help_marks_description
}

@Composable
private fun SettingChoice(label: String, description: String?, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
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
        Column(Modifier.padding(vertical = 4.dp)) {
            Text(label)
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}
