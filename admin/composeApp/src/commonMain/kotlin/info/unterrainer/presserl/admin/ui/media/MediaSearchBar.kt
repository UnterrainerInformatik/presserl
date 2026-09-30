package info.unterrainer.presserl.admin.ui.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import info.unterrainer.presserl.admin.api.MediaFilter
import info.unterrainer.presserl.admin.api.MediaTagDto
import info.unterrainer.presserl.admin.resources.Res
import info.unterrainer.presserl.admin.resources.media_filter_clear
import info.unterrainer.presserl.admin.resources.media_filter_mine
import info.unterrainer.presserl.admin.resources.media_filter_tags
import info.unterrainer.presserl.admin.resources.media_filter_text
import info.unterrainer.presserl.admin.resources.media_filter_unused
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** What the search inputs above a media [grid] hold: the tag chips and the typed text, taken from the grid's filter. */
internal class MediaSearch(val grid: MediaGridModel) {
    val tags = TagChips(grid.filter.tags, MAX_FILTER_TAGS)
    var query by mutableStateOf(grid.filter.q)

    /** Whether a filter is applied or text is typed. */
    val active: Boolean get() = grid.filter.active || query.isNotBlank()

    /** Empties every input and reloads the grid without filters. */
    suspend fun clear() {
        tags.set(emptyList())
        query = ""
        grid.setFilter(MediaFilter())
    }
}

@Composable
internal fun rememberMediaSearch(grid: MediaGridModel): MediaSearch = remember(grid) { MediaSearch(grid) }

/**
 * Tag input, search text (applied after a short pause in typing), "Unused" and "Mine", and "Clear filters" while
 * anything is set; every change reloads [search]'s grid. [suggest] completes tags.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MediaSearchBar(search: MediaSearch, suggest: suspend (String) -> List<MediaTagDto>) {
    val scope = rememberCoroutineScope()
    val grid = search.grid
    // reloads only after a short pause in typing
    LaunchedEffect(search, search.query) {
        if (search.query == grid.filter.q) return@LaunchedEffect
        delay(SEARCH_DEBOUNCE_MILLIS)
        grid.setFilter(grid.filter.copy(q = search.query))
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TagInput(
            search.tags,
            stringResource(Res.string.media_filter_tags),
            suggest,
            onChange = { tags -> scope.launch { grid.setFilter(grid.filter.copy(tags = tags)) } },
        )
        OutlinedTextField(
            value = search.query,
            onValueChange = { search.query = it },
            label = { Text(stringResource(Res.string.media_filter_text)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(
                selected = grid.filter.unused,
                onClick = { scope.launch { grid.setFilter(grid.filter.copy(unused = !grid.filter.unused)) } },
                label = { Text(stringResource(Res.string.media_filter_unused)) },
            )
            FilterChip(
                selected = grid.filter.mine,
                onClick = { scope.launch { grid.setFilter(grid.filter.copy(mine = !grid.filter.mine)) } },
                label = { Text(stringResource(Res.string.media_filter_mine)) },
            )
            if (search.active) {
                TextButton(onClick = { scope.launch { search.clear() } }) { Text(stringResource(Res.string.media_filter_clear)) }
            }
        }
    }
}

/** Pause after the last keystroke in the search text before the grid reloads. */
private const val SEARCH_DEBOUNCE_MILLIS = 400L
