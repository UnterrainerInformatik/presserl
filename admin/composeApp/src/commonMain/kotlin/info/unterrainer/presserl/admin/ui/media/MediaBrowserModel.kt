package info.unterrainer.presserl.admin.ui.media

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import info.unterrainer.presserl.admin.api.MediaDto
import info.unterrainer.presserl.admin.api.MediaListItemDto
import info.unterrainer.presserl.admin.api.MediaPage
import info.unterrainer.presserl.admin.api.MediaUsageDto
import info.unterrainer.presserl.admin.api.MediaUseDto
import info.unterrainer.presserl.admin.ui.attempt
import io.ktor.client.plugins.ResponseException

/** Media per page of the grid. */
const val MEDIA_PAGE_SIZE = 60

/**
 * The media grid: pages of `GET /api/media` loaded one after another with [loadMore] (called when the user scrolls
 * to the end). Kept by the caller while detail and edit views are open, so the grid keeps its pages.
 */
class MediaGridModel(private val load: suspend (before: Long?) -> MediaPage) {
    var items by mutableStateOf<List<MediaListItemDto>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Whether the first page arrived. */
    var loaded by mutableStateOf(false)
        private set
    private var next: Long? = null

    /** Whether another page can be loaded. */
    val hasMore: Boolean get() = !loaded || next != null

    /** Loads the next page, or the first one after [reload]; nothing while a page is loading or all are loaded. */
    suspend fun loadMore() {
        if (loading || !hasMore) return
        loading = true
        error = null
        try {
            attempt({ error = it }) { load(next) }?.let { page ->
                items = items + page.items.filter { new -> items.none { it.id == new.id } }
                next = page.next
                loaded = true
            }
        } finally {
            loading = false
        }
    }

    /** Starts again with the first page. */
    suspend fun reload() {
        items = emptyList()
        next = null
        loaded = false
        loadMore()
    }

    /** Shows an edited media (new version, size, renditions) in place; its usage count stays. */
    fun replace(media: MediaDto) {
        items = items.map {
            if (it.id != media.id) it else it.copy(version = media.version, contentType = media.contentType, width = media.width,
                height = media.height, size = media.size, renditions = media.renditions)
        }
    }
}

/** Where an article uses the image, as the media detail names it. */
enum class UsagePlace {
    /** The version readers see. */
    LIVE,

    /** The current working version, not (yet) live. */
    WORKING,

    /** Only versions that are neither live nor current. */
    OLDER,
}

/** The places of [use], in this order; the live version is named once even when it is also the current one. */
fun usagePlaces(use: MediaUseDto): List<UsagePlace> = buildList {
    if (use.live) add(UsagePlace.LIVE)
    if (use.latest && !use.live) add(UsagePlace.WORKING)
    if (use.older) add(UsagePlace.OLDER)
}

/** Whether the detail offers "Edit": only when the server says so. */
fun mayEdit(usage: MediaUsageDto?): Boolean = usage?.mayEdit == true

/** How many articles an edit changes and how many of them are published, for the confirmation. */
data class EditImpact(val articles: Int, val published: Int)

fun editImpact(usage: MediaUsageDto?): EditImpact =
    EditImpact(usage?.articles?.size ?: 0, usage?.articles?.count { it.status == "PUBLISHED" } ?: 0)

/** Why saving an edit failed, as the edit view explains it; the unsaved edit is kept in every case. */
sealed interface MediaEditError {
    /** `409`: someone else changed the image meanwhile; the view offers to reload it. */
    data object Conflict : MediaEditError

    /** `403`: the user may not (or no longer) edit the image. */
    data object Forbidden : MediaEditError

    /** `400`: the area is not valid for the image. */
    data object Invalid : MediaEditError

    /** `503`, other server errors and network failures. */
    data object Unreachable : MediaEditError

    /** Any other refusal, with the status. */
    data class Other(val message: String) : MediaEditError
}

/** Maps a failed `POST /api/media/{id}/edit`. */
fun mediaEditErrorOf(e: Throwable): MediaEditError {
    if (e !is ResponseException) return MediaEditError.Unreachable
    return when (val status = e.response.status.value) {
        409 -> MediaEditError.Conflict
        403 -> MediaEditError.Forbidden
        400 -> MediaEditError.Invalid
        in 500..599 -> MediaEditError.Unreachable
        else -> MediaEditError.Other("$status ${e.response.status.description}")
    }
}

/** A file size for people: `1.8 MB`, `320 KB`, `900 B`. */
fun formatBytes(size: Long): String = when {
    size >= 1_000_000 -> "${size / 100_000 / 10}.${size / 100_000 % 10} MB"
    size >= 1_000 -> "${(size + 500) / 1_000} KB"
    else -> "$size B"
}
