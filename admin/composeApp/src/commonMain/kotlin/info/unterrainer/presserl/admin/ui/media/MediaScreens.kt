package info.unterrainer.presserl.admin.ui.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.api.MediaDto
import info.unterrainer.presserl.admin.api.MediaFilter
import info.unterrainer.presserl.admin.api.MediaListItemDto
import info.unterrainer.presserl.admin.api.MediaTagDto
import info.unterrainer.presserl.admin.api.MediaUsageDto
import info.unterrainer.presserl.admin.api.MediaUseDto
import info.unterrainer.presserl.admin.api.asListItem
import info.unterrainer.presserl.admin.resources.Res
import info.unterrainer.presserl.admin.resources.back
import info.unterrainer.presserl.admin.resources.cancel
import info.unterrainer.presserl.admin.resources.cancel_edit
import info.unterrainer.presserl.admin.resources.lead_image_no_preview
import info.unterrainer.presserl.admin.resources.loading
import info.unterrainer.presserl.admin.resources.media_aspect_free
import info.unterrainer.presserl.admin.resources.media_confirm_text
import info.unterrainer.presserl.admin.resources.media_confirm_title
import info.unterrainer.presserl.admin.resources.media_confirm_unused
import info.unterrainer.presserl.admin.resources.media_confirm_usage
import info.unterrainer.presserl.admin.resources.media_crop_hint
import info.unterrainer.presserl.admin.resources.media_crop_reset
import info.unterrainer.presserl.admin.resources.media_description
import info.unterrainer.presserl.admin.resources.media_description_none
import info.unterrainer.presserl.admin.resources.media_detail_title
import info.unterrainer.presserl.admin.resources.media_details_discard_text
import info.unterrainer.presserl.admin.resources.media_details_edit
import info.unterrainer.presserl.admin.resources.media_details_forbidden
import info.unterrainer.presserl.admin.resources.media_details_invalid
import info.unterrainer.presserl.admin.resources.media_details_invalid_description
import info.unterrainer.presserl.admin.resources.media_details_invalid_tag
import info.unterrainer.presserl.admin.resources.media_details_invalid_tags
import info.unterrainer.presserl.admin.resources.media_details_title
import info.unterrainer.presserl.admin.resources.media_discard
import info.unterrainer.presserl.admin.resources.media_discard_text
import info.unterrainer.presserl.admin.resources.media_discard_title
import info.unterrainer.presserl.admin.resources.media_edit
import info.unterrainer.presserl.admin.resources.media_edit_title
import info.unterrainer.presserl.admin.resources.media_ellipse_remove
import info.unterrainer.presserl.admin.resources.media_ellipses_full
import info.unterrainer.presserl.admin.resources.media_error_conflict
import info.unterrainer.presserl.admin.resources.media_error_forbidden
import info.unterrainer.presserl.admin.resources.media_error_invalid
import info.unterrainer.presserl.admin.resources.media_error_other
import info.unterrainer.presserl.admin.resources.media_error_unreachable
import info.unterrainer.presserl.admin.resources.media_filter_clear
import info.unterrainer.presserl.admin.resources.media_filter_mine
import info.unterrainer.presserl.admin.resources.media_filter_tags
import info.unterrainer.presserl.admin.resources.media_filter_text
import info.unterrainer.presserl.admin.resources.media_filter_unused
import info.unterrainer.presserl.admin.resources.media_load_more
import info.unterrainer.presserl.admin.resources.media_none
import info.unterrainer.presserl.admin.resources.media_none_matching
import info.unterrainer.presserl.admin.resources.media_not_used
import info.unterrainer.presserl.admin.resources.media_pixelate_hint
import info.unterrainer.presserl.admin.resources.media_place_live
import info.unterrainer.presserl.admin.resources.media_place_older
import info.unterrainer.presserl.admin.resources.media_place_working
import info.unterrainer.presserl.admin.resources.media_published_on
import info.unterrainer.presserl.admin.resources.media_redo
import info.unterrainer.presserl.admin.resources.media_reload_image
import info.unterrainer.presserl.admin.resources.media_save
import info.unterrainer.presserl.admin.resources.media_saving
import info.unterrainer.presserl.admin.resources.media_size
import info.unterrainer.presserl.admin.resources.media_tag_show
import info.unterrainer.presserl.admin.resources.media_tags_none
import info.unterrainer.presserl.admin.resources.media_take_photo
import info.unterrainer.presserl.admin.resources.media_tool_crop
import info.unterrainer.presserl.admin.resources.media_tool_pixelate
import info.unterrainer.presserl.admin.resources.media_undo
import info.unterrainer.presserl.admin.resources.media_upload
import info.unterrainer.presserl.admin.resources.media_upload_close
import info.unterrainer.presserl.admin.resources.media_upload_description
import info.unterrainer.presserl.admin.resources.media_upload_discard_text
import info.unterrainer.presserl.admin.resources.media_upload_discard_title
import info.unterrainer.presserl.admin.resources.media_upload_done
import info.unterrainer.presserl.admin.resources.media_upload_retry
import info.unterrainer.presserl.admin.resources.media_upload_start
import info.unterrainer.presserl.admin.resources.media_upload_tags
import info.unterrainer.presserl.admin.resources.media_upload_uploading
import info.unterrainer.presserl.admin.resources.media_upload_waiting
import info.unterrainer.presserl.admin.resources.media_uploaded
import info.unterrainer.presserl.admin.resources.media_usage_none
import info.unterrainer.presserl.admin.resources.media_usage_title
import info.unterrainer.presserl.admin.resources.media_used_by
import info.unterrainer.presserl.admin.resources.media_used_by_one
import info.unterrainer.presserl.admin.resources.no_headline
import info.unterrainer.presserl.admin.resources.remove
import info.unterrainer.presserl.admin.ui.BackButton
import info.unterrainer.presserl.admin.ui.Banner
import info.unterrainer.presserl.admin.ui.LoadFailed
import info.unterrainer.presserl.admin.ui.attempt
import info.unterrainer.presserl.admin.ui.formatTimestamp
import info.unterrainer.presserl.admin.ui.section.ColorMarker
import info.unterrainer.presserl.admin.ui.statusText
import info.unterrainer.presserl.admin.ui.waitingText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The grid of the newspaper's images, newest first, with "Upload images", "Take photo" and the search bar above;
 * further pages load at the end. [maxUploadSize] is the newspaper's `media.max-size`, named when a file is too large.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaGridScreen(
    api: ApiClient,
    grid: MediaGridModel,
    thumbnails: Thumbnails,
    maxUploadSize: String?,
    onOpen: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val state = rememberLazyGridState()
    var upload by remember { mutableStateOf<MediaUploadModel?>(null) }
    val searchTags = remember(grid) { TagChips(grid.filter.tags, MAX_FILTER_TAGS) }
    var query by remember(grid) { mutableStateOf(grid.filter.q) }
    LaunchedEffect(grid) { if (!grid.loaded) grid.loadMore() }
    LaunchedEffect(grid, state) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index to state.layoutInfo.totalItemsCount }
            .distinctUntilChanged()
            .collect { (last, total) -> if (last != null && total > 0 && last >= total - 1 && grid.loaded) grid.loadMore() }
    }
    // reloads only after a short pause in typing
    LaunchedEffect(grid, query) {
        if (query == grid.filter.q) return@LaunchedEffect
        delay(SEARCH_DEBOUNCE_MILLIS)
        grid.setFilter(grid.filter.copy(q = query))
    }
    val choose = { camera: Boolean ->
        scope.launch {
            chooseForUpload({ pickImageFiles(camera) }, api::uploadMedia) { grid.prepend(it.asListItem()) }?.let { upload = it }
        }
    }
    val clearFilters = {
        searchTags.set(emptyList())
        query = ""
        scope.launch { grid.setFilter(MediaFilter()) }
    }
    val suggest: suspend (String) -> List<MediaTagDto> = { api.mediaTags(it) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { choose(false) }) { Text(stringResource(Res.string.media_upload)) }
            OutlinedButton(onClick = { choose(true) }) { Text(stringResource(Res.string.media_take_photo)) }
        }
        TagInput(
            searchTags,
            stringResource(Res.string.media_filter_tags),
            suggest,
            onChange = { tags -> scope.launch { grid.setFilter(grid.filter.copy(tags = tags)) } },
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
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
            if (grid.filter.active || query.isNotBlank()) {
                TextButton(onClick = { clearFilters() }) { Text(stringResource(Res.string.media_filter_clear)) }
            }
        }
        val error = grid.error
        when {
            !grid.loaded && error != null -> LoadFailed(error) { scope.launch { grid.reload() } }
            !grid.loaded -> Text(stringResource(Res.string.loading))
            grid.items.isEmpty() && grid.filter.active -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.media_none_matching))
                OutlinedButton(onClick = { clearFilters() }) { Text(stringResource(Res.string.media_filter_clear)) }
            }
            grid.items.isEmpty() -> Text(stringResource(Res.string.media_none))
            else -> LazyVerticalGrid(
                GridCells.Adaptive(170.dp),
                state = state,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(grid.items, key = { it.id }) { media -> MediaTile(media, thumbnails) { onOpen(media.id) } }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        error?.let { Banner(it) }
                        when {
                            grid.loading -> Text(stringResource(Res.string.loading))
                            grid.hasMore -> OutlinedButton(onClick = { scope.launch { grid.loadMore() } }) {
                                Text(stringResource(Res.string.media_load_more))
                            }
                        }
                    }
                }
            }
        }
    }
    upload?.let { model -> UploadDialog(model, maxUploadSize, suggest, onClose = { upload = null }) }
}

/** Pause after the last keystroke in the search text before the grid reloads. */
private const val SEARCH_DEBOUNCE_MILLIS = 400L

/**
 * The chosen files with size and state, the shared tags and description, and "Upload"; closing while files are not
 * uploaded asks first. The uploads run in the dialog's scope, so discarding stops them.
 */
@Composable
private fun UploadDialog(
    model: MediaUploadModel,
    maxUploadSize: String?,
    suggest: suspend (String) -> List<MediaTagDto>,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var confirmDiscard by remember { mutableStateOf(false) }
    val close = { if (model.hasPending) confirmDiscard = true else onClose() }
    AlertDialog(
        onDismissRequest = { close() },
        title = { Text(stringResource(Res.string.media_upload)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                model.entries.forEach { entry -> UploadRow(model, entry, maxUploadSize) { scope.launch { model.retry(entry) } } }
                TagInput(model.tags, stringResource(Res.string.media_upload_tags), suggest, enabled = !model.started)
                OutlinedTextField(
                    value = model.description,
                    onValueChange = { if (it.length <= MAX_DESCRIPTION_LENGTH) model.description = it },
                    label = { Text(stringResource(Res.string.media_upload_description)) },
                    enabled = !model.started,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            if (model.allDone) {
                Button(onClick = onClose) { Text(stringResource(Res.string.media_upload_close)) }
            } else if (!model.started) {
                Button(onClick = { scope.launch { model.start() } }, enabled = model.entries.isNotEmpty()) {
                    Text(stringResource(Res.string.media_upload_start))
                }
            }
        },
        dismissButton = {
            if (!model.allDone) TextButton(onClick = { close() }) { Text(stringResource(Res.string.cancel)) }
        },
    )
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(Res.string.media_upload_discard_title)) },
            text = {
                Text(stringResource(Res.string.media_upload_discard_text,
                    model.entries.count { it.state !is MediaUploadModel.State.Done }))
            },
            confirmButton = {
                Button(onClick = {
                    confirmDiscard = false
                    onClose()
                }) { Text(stringResource(Res.string.media_discard)) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(Res.string.back)) } },
        )
    }
}

@Composable
private fun UploadRow(model: MediaUploadModel, entry: MediaUploadModel.Entry, maxUploadSize: String?, onRetry: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(entry.file.name, style = MaterialTheme.typography.bodyMedium)
                val state = when (val current = entry.state) {
                    MediaUploadModel.State.Waiting -> stringResource(Res.string.media_upload_waiting)
                    MediaUploadModel.State.Uploading -> stringResource(Res.string.media_upload_uploading)
                    is MediaUploadModel.State.Done -> stringResource(Res.string.media_upload_done)
                    is MediaUploadModel.State.Failed -> uploadErrorText(current.error, maxUploadSize)
                }
                Text(
                    "${formatBytes(entry.file.size)} · $state",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (entry.state is MediaUploadModel.State.Failed) MaterialTheme.colorScheme.error else Color.Unspecified,
                )
            }
            if (entry.state is MediaUploadModel.State.Failed && !model.running) {
                TextButton(onClick = onRetry) { Text(stringResource(Res.string.media_upload_retry)) }
            }
            if (entry.state is MediaUploadModel.State.Failed || (entry.state == MediaUploadModel.State.Waiting && !model.started)) {
                TextButton(onClick = { model.remove(entry) }) { Text(stringResource(Res.string.remove)) }
            }
        }
    }
}

@Composable
private fun MediaTile(media: MediaListItemDto, thumbnails: Thumbnails, onClick: () -> Unit) {
    val hasThumbnail = "thumbnail" in media.renditions
    LaunchedEffect(media.id, media.version) { if (hasThumbnail) thumbnails.fetch(media.id, media.version) }
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column {
            val frame = Modifier.fillMaxWidth().aspectRatio(1.5f)
            when (val thumbnail = if (hasThumbnail) thumbnails[media.id] else Thumbnails.Result.Missing) {
                is Thumbnails.Result.Loaded -> Image(thumbnail.image, contentDescription = null, modifier = frame,
                    contentScale = ContentScale.Crop)
                else -> Box(frame.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    if (thumbnail == Thumbnails.Result.Missing) {
                        Text(stringResource(Res.string.lead_image_no_preview), style = MaterialTheme.typography.bodySmall)
                    } else {
                        PictureIcon(32.dp)
                    }
                }
            }
            Column(Modifier.padding(8.dp)) {
                Text(media.uploadedBy.displayName, style = MaterialTheme.typography.bodyMedium)
                Text(formatTimestamp(media.uploadedAt), style = MaterialTheme.typography.bodySmall)
                Text(usageText(media.usageCount), style = MaterialTheme.typography.bodySmall)
                if (media.tags.isNotEmpty()) {
                    Text(
                        media.tags.take(TILE_TAGS).joinToString(" · ") + if (media.tags.size > TILE_TAGS) " …" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Tags a grid tile shows at most. */
private const val TILE_TAGS = 3

@Composable
private fun usageText(count: Long): String = when (count) {
    0L -> stringResource(Res.string.media_not_used)
    1L -> stringResource(Res.string.media_used_by_one)
    else -> stringResource(Res.string.media_used_by, count.toInt())
}

/** A rendition decoded for display, or `null` while loading or when it cannot be loaded. */
private suspend fun loadRendition(api: ApiClient, id: Long, kinds: List<String>): ImageBitmap? {
    for (kind in kinds) {
        val bitmap = try {
            decodeImage(api.mediaRendition(id, kind))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
        if (bitmap != null) return bitmap
    }
    return null
}

/**
 * One image: preview, size, uploader, description and tags (editable by every user of the view) and the articles
 * using it; "Edit" when the server allows it. [onSaved] receives the media after description and tags were saved,
 * [onTag] a tag the user selected to browse by.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaDetailScreen(
    api: ApiClient,
    mediaId: Long,
    onBack: () -> Unit,
    onEdit: (MediaDto, MediaUsageDto) -> Unit,
    onOpenArticle: (Long) -> Unit,
    onSaved: (MediaDto) -> Unit = {},
    onTag: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var details by remember { mutableStateOf<MediaDetailsModel?>(null) }
    var usage by remember { mutableStateOf<MediaUsageDto?>(null) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loads by remember { mutableStateOf(0) }
    var confirmLeave by remember { mutableStateOf(false) }
    LaunchedEffect(loads) {
        error = null
        attempt({ error = it }) {
            val loaded = api.media(mediaId)
            details = MediaDetailsModel(loaded) { api.setMediaDetails(mediaId, it) }
            usage = api.mediaUsage(mediaId)
        }
        preview = loadRendition(api, mediaId, listOf("web"))
    }
    val leave = { if (details?.dirty == true) confirmLeave = true else onBack() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton { leave() }
            Text(stringResource(Res.string.media_detail_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            val current = details?.media
            val currentUsage = usage
            if (current != null && mayEdit(currentUsage)) {
                Button(onClick = { onEdit(current, currentUsage!!) }) { Text(stringResource(Res.string.media_edit)) }
            }
        }
        error?.let { LoadFailed(it) { loads++ } }
        val model = details ?: run {
            if (error == null) Text(stringResource(Res.string.loading))
            return@Column
        }
        val current = model.media
        val frame = Modifier.fillMaxWidth().aspectRatio(current.width.toFloat() / current.height)
        preview?.let { Image(it, contentDescription = null, modifier = frame, contentScale = ContentScale.Fit) }
            ?: Box(frame.background(MaterialTheme.colorScheme.surfaceVariant))
        Text(stringResource(Res.string.media_size, current.width, current.height, formatBytes(current.size)))
        Text(stringResource(Res.string.media_uploaded, current.uploadedBy.displayName, formatTimestamp(current.uploadedAt)))
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.media_details_title), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f))
            if (!model.editing) TextButton(onClick = { model.edit() }) { Text(stringResource(Res.string.media_details_edit)) }
        }
        if (model.editing) {
            OutlinedTextField(
                value = model.description,
                onValueChange = { if (it.length <= MAX_DESCRIPTION_LENGTH) model.description = it },
                label = { Text(stringResource(Res.string.media_description)) },
                enabled = !model.saving,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            TagInput(model.tags, stringResource(Res.string.media_filter_tags), { api.mediaTags(it) }, enabled = !model.saving)
            model.error?.let { Text(detailsErrorText(it), color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { scope.launch { model.save()?.let(onSaved) } },
                    enabled = !model.saving,
                ) { Text(stringResource(Res.string.media_save)) }
                TextButton(onClick = { model.cancel() }, enabled = !model.saving) { Text(stringResource(Res.string.cancel)) }
                if (model.saving) Text(stringResource(Res.string.media_saving), style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Text(
                current.description ?: stringResource(Res.string.media_description_none),
                style = MaterialTheme.typography.bodyMedium,
                color = if (current.description == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
            )
            if (current.tags.isEmpty()) {
                Text(stringResource(Res.string.media_tags_none), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    current.tags.forEach { tag ->
                        val show = stringResource(Res.string.media_tag_show, tag)
                        AssistChip(
                            onClick = { onTag(tag) },
                            label = { Text(tag) },
                            modifier = Modifier.semantics { contentDescription = show },
                        )
                    }
                }
            }
        }
        HorizontalDivider()
        Text(stringResource(Res.string.media_usage_title), style = MaterialTheme.typography.titleMedium)
        val articles = usage?.articles.orEmpty()
        if (usage != null && articles.isEmpty()) Text(stringResource(Res.string.media_usage_none))
        articles.forEach { use -> UsageRow(use) { onOpenArticle(use.id) } }
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(Res.string.media_discard_title)) },
            text = { Text(stringResource(Res.string.media_details_discard_text)) },
            confirmButton = {
                Button(onClick = {
                    confirmLeave = false
                    onBack()
                }) { Text(stringResource(Res.string.media_discard)) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(Res.string.cancel_edit)) } },
        )
    }
}

@Composable
private fun detailsErrorText(error: MediaDetailsError): String = when (error) {
    is MediaDetailsError.Invalid -> stringResource(
        when {
            error.field == "description" -> Res.string.media_details_invalid_description
            error.field == "tags" -> Res.string.media_details_invalid_tags
            error.field?.startsWith("tags[") == true -> Res.string.media_details_invalid_tag
            else -> Res.string.media_details_invalid
        },
    )
    MediaDetailsError.Forbidden -> stringResource(Res.string.media_details_forbidden)
    MediaDetailsError.Unreachable -> stringResource(Res.string.media_error_unreachable)
    is MediaDetailsError.Other -> stringResource(Res.string.media_error_other, error.message)
}

@Composable
private fun UsageRow(use: MediaUseDto, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                use.section?.let { ColorMarker(it.color, size = 12.dp) }
                Text(use.headline.ifEmpty { stringResource(Res.string.no_headline) }, style = MaterialTheme.typography.titleSmall)
            }
            val facts = buildList {
                use.section?.let { add(it.name) }
                add(use.author.displayName)
                add(statusText(use.status))
            }
            Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            use.pendingLevel?.let { Text(waitingText(it), style = MaterialTheme.typography.bodySmall) }
            use.publishedAt?.let {
                Text(stringResource(Res.string.media_published_on, formatTimestamp(it)), style = MaterialTheme.typography.bodySmall)
            }
            Text(usagePlaces(use).map { placeText(it) }.joinToString(", "), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun placeText(place: UsagePlace): String = stringResource(
    when (place) {
        UsagePlace.LIVE -> Res.string.media_place_live
        UsagePlace.WORKING -> Res.string.media_place_working
        UsagePlace.OLDER -> Res.string.media_place_older
    },
)

private enum class Tool { CROP, PIXELATE }

/** What a drag on the edit canvas changes. */
private sealed interface Drag {
    data class Crop(val start: PixelRect, val handle: Handle) : Drag
    data class NewCrop(val x: Int, val y: Int) : Drag
    data class Ellipse(val index: Int, val start: PixelEllipse, val handle: Handle) : Drag
    data class NewEllipse(val index: Int, val x: Int, val y: Int) : Drag
}

/** Touch tolerance of handles on screen. */
private val HANDLE_TOLERANCE = 14.dp

/**
 * Crops and pixelates [initial] on its `print` rendition and saves after confirmation; [usage] names the articles
 * the confirmation counts. [onSaved] receives the edited media.
 */
@Composable
fun MediaEditScreen(
    api: ApiClient,
    initial: MediaDto,
    usage: MediaUsageDto,
    onBack: () -> Unit,
    onSaved: (MediaDto) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var media by remember { mutableStateOf(initial) }
    var model by remember { mutableStateOf(MediaEditModel(initial.width, initial.height)) }
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var loads by remember { mutableStateOf(0) }
    var tool by remember { mutableStateOf(Tool.PIXELATE) }
    var confirmSave by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<MediaEditError?>(null) }
    var full by remember { mutableStateOf(false) }

    LaunchedEffect(loads) {
        bitmap = null
        bitmap = loadRendition(api, media.id, listOf("print", "web"))
    }
    val leave = { if (model.dirty && !saving) confirmLeave = true else onBack() }
    val reload = {
        scope.launch {
            val fresh = attempt({}) { api.media(media.id) } ?: return@launch
            if (fresh.width != media.width || fresh.height != media.height) model = MediaEditModel(fresh.width, fresh.height)
            media = fresh
            saveError = null
            loads++
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton { leave() }
            Text(stringResource(Res.string.media_edit_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(onClick = { confirmSave = true }, enabled = model.dirty && !saving) {
                Text(stringResource(if (saving) Res.string.media_saving else Res.string.media_save))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(tool == Tool.PIXELATE, onClick = { tool = Tool.PIXELATE }, label = { Text(stringResource(Res.string.media_tool_pixelate)) })
            FilterChip(tool == Tool.CROP, onClick = { tool = Tool.CROP; model.select(null) }, label = { Text(stringResource(Res.string.media_tool_crop)) })
            TextButton(onClick = model::undo, enabled = model.canUndo && !saving) { Text(stringResource(Res.string.media_undo)) }
            TextButton(onClick = model::redo, enabled = model.canRedo && !saving) { Text(stringResource(Res.string.media_redo)) }
        }
        when (tool) {
            Tool.CROP -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Aspect.entries.forEach { aspect ->
                    FilterChip(model.aspect == aspect, onClick = { model.chooseAspect(aspect) }, label = { Text(aspectText(aspect)) })
                }
                TextButton(onClick = model::resetCrop, enabled = model.state.crop != null) { Text(stringResource(Res.string.media_crop_reset)) }
            }
            Tool.PIXELATE -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = model::removeSelected, enabled = model.selected != null) {
                    Text(stringResource(Res.string.media_ellipse_remove))
                }
            }
        }
        Text(stringResource(if (tool == Tool.CROP) Res.string.media_crop_hint else Res.string.media_pixelate_hint),
            style = MaterialTheme.typography.bodySmall)
        if (full) Banner(stringResource(Res.string.media_ellipses_full, MAX_ELLIPSES))
        saveError?.let { error ->
            Banner(editErrorText(error), action = if (error == MediaEditError.Conflict) {
                { TextButton(onClick = { reload() }) { Text(stringResource(Res.string.media_reload_image)) } }
            } else {
                null
            })
        }
        val image = bitmap
        if (image == null) {
            Text(stringResource(Res.string.loading))
        } else {
            EditCanvas(image, model, tool, onFull = { full = it })
        }
    }

    if (confirmSave) {
        val impact = editImpact(usage)
        AlertDialog(
            onDismissRequest = { confirmSave = false },
            title = { Text(stringResource(Res.string.media_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.media_confirm_text))
                    Text(
                        if (impact.articles == 0) stringResource(Res.string.media_confirm_unused)
                        else stringResource(Res.string.media_confirm_usage, impact.articles, impact.published),
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    confirmSave = false
                    saving = true
                    saveError = null
                    scope.launch {
                        try {
                            val saved = api.editMedia(media.id, model.request(media.version))
                            saving = false
                            onSaved(saved)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            // Browser fetch failures surface as kotlin.Error, not Exception
                            saving = false
                            saveError = mediaEditErrorOf(e)
                        }
                    }
                }) { Text(stringResource(Res.string.media_save)) }
            },
            dismissButton = { TextButton(onClick = { confirmSave = false }) { Text(stringResource(Res.string.cancel_edit)) } },
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(Res.string.media_discard_title)) },
            text = { Text(stringResource(Res.string.media_discard_text)) },
            confirmButton = { Button(onClick = { confirmLeave = false; onBack() }) { Text(stringResource(Res.string.media_discard)) } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(Res.string.cancel_edit)) } },
        )
    }
}

@Composable
private fun aspectText(aspect: Aspect): String = when (aspect) {
    Aspect.FREE -> stringResource(Res.string.media_aspect_free)
    Aspect.RATIO_3_2 -> "3:2"
    Aspect.RATIO_4_3 -> "4:3"
    Aspect.RATIO_16_9 -> "16:9"
    Aspect.RATIO_1_1 -> "1:1"
}

@Composable
private fun editErrorText(error: MediaEditError): String = when (error) {
    MediaEditError.Conflict -> stringResource(Res.string.media_error_conflict)
    MediaEditError.Forbidden -> stringResource(Res.string.media_error_forbidden)
    MediaEditError.Invalid -> stringResource(Res.string.media_error_invalid)
    MediaEditError.Unreachable -> stringResource(Res.string.media_error_unreachable)
    is MediaEditError.Other -> stringResource(Res.string.media_error_other, error.message)
}

/**
 * The preview with the crop (outside dimmed, handles) and the ellipses drawn pixelated from the preview's pixels (the
 * server's block rule; the server's result is authoritative).
 */
@Composable
private fun EditCanvas(image: ImageBitmap, model: MediaEditModel, tool: Tool, onFull: (Boolean) -> Unit) {
    val density = LocalDensity.current
    // block colours per ellipse, sampled from the preview once per ellipse geometry
    val colours = remember(image) { mutableStateMapOf<PixelEllipse, List<Pair<PixelRect, Color>>>() }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val ratio = model.width.toFloat() / model.height
        val maxHeight = 640.dp
        val viewWidth = min(maxWidth.value, maxHeight.value * ratio).dp
        val viewHeight = (viewWidth.value / ratio).dp
        val widthPx = with(density) { viewWidth.toPx() }
        val heightPx = with(density) { viewHeight.toPx() }
        val mapping = PreviewMapping(model.width, model.height, widthPx, heightPx)
        val tolerance = mapping.toStored(with(density) { HANDLE_TOLERANCE.toPx() })
        var drag by remember { mutableStateOf<Drag?>(null) }
        var dragOrigin by remember { mutableStateOf(Offset.Zero) }

        Canvas(
            Modifier.size(viewWidth, viewHeight)
                .pointerInput(tool, mapping.viewWidth, mapping.viewHeight) {
                    detectTapGestures { offset ->
                        if (tool == Tool.PIXELATE) {
                            val x = mapping.toStoredX(offset.x)
                            val y = mapping.toStoredY(offset.y)
                            model.select(model.state.ellipses.indexOfLast { it.contains(x, y) }.takeIf { it >= 0 })
                        }
                    }
                }
                .pointerInput(tool, mapping.viewWidth, mapping.viewHeight) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragOrigin = offset
                            val x = mapping.toStoredX(offset.x)
                            val y = mapping.toStoredY(offset.y)
                            model.begin()
                            drag = when (tool) {
                                Tool.CROP -> model.state.crop?.let { crop -> hitRect(crop, x, y, tolerance)?.let { Drag.Crop(crop, it) } }
                                    ?: Drag.NewCrop(x, y)
                                Tool.PIXELATE -> {
                                    val ellipses = model.state.ellipses
                                    val selected = model.selected?.let { index -> ellipses.getOrNull(index)?.let { index to it } }
                                    val handle = selected?.let { (_, e) -> hitRect(e.bounds, x, y, tolerance)?.takeIf { it != Handle.MOVE } }
                                    val inside = ellipses.indexOfLast { it.contains(x, y) }
                                    when {
                                        selected != null && handle != null -> Drag.Ellipse(selected.first, selected.second, handle)
                                        inside >= 0 -> {
                                            model.select(inside)
                                            Drag.Ellipse(inside, ellipses[inside], Handle.MOVE)
                                        }
                                        model.addEllipse(model.drawEllipse(x, y, x, y)) -> {
                                            onFull(false)
                                            Drag.NewEllipse(model.state.ellipses.lastIndex, x, y)
                                        }
                                        else -> {
                                            onFull(true)
                                            null
                                        }
                                    }
                                }
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val position = change.position
                            val dx = mapping.toStored(position.x - dragOrigin.x)
                            val dy = mapping.toStored(position.y - dragOrigin.y)
                            val x = mapping.toStoredX(position.x)
                            val y = mapping.toStoredY(position.y)
                            when (val current = drag) {
                                is Drag.Crop -> model.update(model.state.copy(crop = model.dragCrop(current.start, current.handle, dx, dy)))
                                is Drag.NewCrop -> model.update(model.state.copy(crop = model.drawCrop(current.x, current.y, x, y)))
                                is Drag.Ellipse -> model.setEllipse(current.index, model.resizeEllipse(current.start, current.handle, dx, dy))
                                is Drag.NewEllipse -> model.setEllipse(current.index, model.drawEllipse(current.x, current.y, x, y))
                                null -> {}
                            }
                        },
                        onDragEnd = { drag = null; model.end() },
                        onDragCancel = { drag = null; model.end() },
                    )
                },
        ) {
            drawImage(image, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
            val live = model.state.ellipses.toSet()
            colours.keys.retainAll(live)
            model.state.ellipses.forEachIndexed { index, ellipse ->
                val blocks = colours.getOrPut(ellipse) { sampleBlocks(image, ellipse, model.width, model.height) }
                drawPixelated(ellipse, blocks, mapping)
                val bounds = ellipse.bounds.toView(mapping)
                drawOval(Color.White, bounds.topLeft, bounds.size, style = Stroke(1.dp.toPx()))
                if (index == model.selected) {
                    drawRect(Color.White, bounds.topLeft, bounds.size, style = Stroke(1.dp.toPx()))
                    drawHandles(bounds)
                }
            }
            model.state.crop?.let { crop ->
                val rect = crop.toView(mapping)
                val dim = Color.Black.copy(alpha = if (tool == Tool.CROP) 0.55f else 0.35f)
                drawRect(dim, Offset.Zero, Size(size.width, rect.top))
                drawRect(dim, Offset(0f, rect.bottom), Size(size.width, size.height - rect.bottom))
                drawRect(dim, Offset(0f, rect.top), Size(rect.left, rect.height))
                drawRect(dim, Offset(rect.right, rect.top), Size(size.width - rect.right, rect.height))
                drawRect(Color.White, rect.topLeft, rect.size, style = Stroke(1.5.dp.toPx()))
                if (tool == Tool.CROP) drawHandles(rect)
            }
        }
    }
}

private fun PixelRect.toView(mapping: PreviewMapping): Rect =
    Rect(mapping.toView(x), mapping.toView(y), mapping.toView(right), mapping.toView(bottom))

private fun DrawScope.drawHandles(rect: Rect) {
    val side = 8.dp.toPx()
    val points = listOf(
        rect.topLeft, rect.topCenter, rect.topRight, rect.centerLeft, rect.centerRight, rect.bottomLeft, rect.bottomCenter,
        rect.bottomRight,
    )
    for (point in points) {
        val topLeft = Offset(point.x - side / 2, point.y - side / 2)
        drawRect(Color.White, topLeft, Size(side, side))
        drawRect(Color.Black, topLeft, Size(side, side), style = Stroke(1.dp.toPx()))
    }
}

/** Fills the ellipse with its blocks' colours, clipped to the ellipse. */
private fun DrawScope.drawPixelated(ellipse: PixelEllipse, blocks: List<Pair<PixelRect, Color>>, mapping: PreviewMapping) {
    val oval = Path().apply { addOval(ellipse.bounds.toView(mapping)) }
    clipPath(oval) {
        for ((block, colour) in blocks) {
            val rect = block.toView(mapping)
            // a hair wider, so no seams show between neighbouring blocks
            drawRect(colour, rect.topLeft, Size(rect.width + 0.5f, rect.height + 0.5f))
        }
    }
}

/**
 * The average colour of every block of [ellipse] (stored pixels of an image [storedWidth] wide), sampled from the
 * preview [image], which may be smaller than the stored image.
 */
private fun sampleBlocks(image: ImageBitmap, ellipse: PixelEllipse, storedWidth: Int, storedHeight: Int): List<Pair<PixelRect, Color>> {
    val scale = image.width.toFloat() / storedWidth
    return pixelBlocks(ellipse, storedWidth, storedHeight).map { block ->
        val x0 = (block.x * scale).toInt().coerceIn(0, image.width - 1)
        val y0 = (block.y * scale).toInt().coerceIn(0, image.height - 1)
        val w = max(1, (block.width * scale).roundToInt()).coerceAtMost(image.width - x0)
        val h = max(1, (block.height * scale).roundToInt()).coerceAtMost(image.height - y0)
        val pixels = IntArray(w * h)
        image.readPixels(pixels, x0, y0, w, h)
        var a = 0L
        var r = 0L
        var g = 0L
        var b = 0L
        for (argb in pixels) {
            a += (argb ushr 24) and 0xFF
            r += (argb shr 16) and 0xFF
            g += (argb shr 8) and 0xFF
            b += argb and 0xFF
        }
        val n = pixels.size
        block to Color((r / n).toInt(), (g / n).toInt(), (b / n).toInt(), (a / n).toInt())
    }
}
