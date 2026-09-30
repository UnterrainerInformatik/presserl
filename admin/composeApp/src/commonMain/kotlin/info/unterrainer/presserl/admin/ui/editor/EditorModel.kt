package info.unterrainer.presserl.admin.ui.editor

import info.unterrainer.presserl.admin.api.ArticleContent
import info.unterrainer.presserl.admin.api.ArticleDto
import info.unterrainer.presserl.admin.api.LeadImageDto
import info.unterrainer.presserl.admin.api.LeadImageRequest
import info.unterrainer.presserl.admin.api.MediaDto
import info.unterrainer.presserl.admin.api.RevisionDto
import info.unterrainer.presserl.admin.article.Block
import info.unterrainer.presserl.admin.article.Body
import info.unterrainer.presserl.admin.article.Run
import info.unterrainer.presserl.admin.article.singleLine
import info.unterrainer.presserl.admin.ui.media.PickedFile
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Header fields in hierarchy order; limits in code points as enforced by the server. */
enum class HeaderField(val maxLength: Int) {
    KICKER(200),
    HEADLINE(200),
    SUBHEADLINE(200),
    LEAD(1000),
}

/** Block types in the order the "Add block" menu offers them; [IMAGE] is inserted only after an upload. */
enum class BlockType { PARAGRAPH, SUBHEAD, QUOTE, LIST, IMAGE }

/** Longest caption (lead image and image blocks) in code points, as enforced by the server. */
const val CAPTION_MAX = 300

/** The lead image of the draft: an uploaded media, its caption and the stored image's size (for the layout). */
data class DraftLeadImage(val mediaId: Long, val caption: String, val width: Int, val height: Int)

/** Where an upload was started, so its busy indicator and error message show there. */
sealed interface UploadTarget {
    /** The lead-image field: sets or replaces the lead image. */
    data object LeadImage : UploadTarget

    /** The "Add block" menu after block [afterId] (`null`: at the start): inserts an image block there. */
    data class NewBlock(val afterId: Long?) : UploadTarget

    /** The replace button of image block [blockId]: replaces its image, keeps the caption. */
    data class Block(val blockId: Long) : UploadTarget
}

/** Why an upload was refused, as the editor explains it. */
sealed interface UploadError {
    /** `413`: above the newspaper's `media.max-size`. */
    data object TooLarge : UploadError

    /** `415`: not a JPEG, PNG or WebP image (HEIC, GIF, other files). */
    data object Unsupported : UploadError

    /** `400`: damaged, or too many pixels. */
    data object Damaged : UploadError

    /** `503`, other server errors and network failures. */
    data object Unreachable : UploadError

    /** Any other refusal, with the server's text. */
    data class Other(val message: String) : UploadError
}

/** Maps a failed upload to what the editor shows. */
fun uploadErrorOf(e: Throwable): UploadError {
    if (e !is ResponseException) return UploadError.Unreachable
    return when (val status = e.response.status.value) {
        413 -> UploadError.TooLarge
        415 -> UploadError.Unsupported
        400 -> UploadError.Damaged
        in 500..599 -> UploadError.Unreachable
        else -> UploadError.Other("$status ${e.response.status.description}")
    }
}

data class ListItem(val id: Long, val runs: List<Run>)

/** A body block with a local id that stays stable while blocks are added, moved and removed. */
sealed interface EditorBlock {
    val id: Long

    data class Paragraph(override val id: Long, val runs: List<Run>) : EditorBlock
    data class Subhead(override val id: Long, val text: String) : EditorBlock
    data class Quote(override val id: Long, val runs: List<Run>) : EditorBlock
    data class BulletList(override val id: Long, val items: List<ListItem>) : EditorBlock
    data class Image(override val id: Long, val mediaId: Long, val caption: String) : EditorBlock
}

/** The undoable document state; [sectionId] is part of it, so moving an article is autosaved and undone like text. */
data class Draft(
    val sectionId: Long? = null,
    val kicker: String = "",
    val headline: String = "",
    val subheadline: String = "",
    val lead: String = "",
    val blocks: List<EditorBlock> = emptyList(),
    val leadImage: DraftLeadImage? = null,
) {
    operator fun get(field: HeaderField): String = when (field) {
        HeaderField.KICKER -> kicker
        HeaderField.HEADLINE -> headline
        HeaderField.SUBHEADLINE -> subheadline
        HeaderField.LEAD -> lead
    }

    fun with(field: HeaderField, value: String): Draft = when (field) {
        HeaderField.KICKER -> copy(kicker = value)
        HeaderField.HEADLINE -> copy(headline = value)
        HeaderField.SUBHEADLINE -> copy(subheadline = value)
        HeaderField.LEAD -> copy(lead = value)
    }

    fun body(): Body = Body(
        blocks = blocks.map { block ->
            when (block) {
                is EditorBlock.Paragraph -> Block.Paragraph(block.runs)
                is EditorBlock.Subhead -> Block.Subhead(block.text)
                is EditorBlock.Quote -> Block.Quote(block.runs)
                is EditorBlock.BulletList -> Block.BulletList(block.items.map { it.runs })
                is EditorBlock.Image -> Block.Image(block.mediaId, block.caption)
            }
        },
    )

    fun toContent(): ArticleContent = ArticleContent(
        kicker,
        headline,
        subheadline,
        lead,
        body().toJson(),
        sectionId,
        leadImage?.let { LeadImageRequest(it.mediaId, it.caption) },
    )
}

/** Hands out the local ids of blocks and list items. */
class IdSource {
    private var next = 1L

    fun next(): Long = next++
}

fun draftOf(article: ArticleDto, ids: IdSource): Draft =
    draftOf(article.kicker, article.headline, article.subheadline, article.lead, Body.fromJson(article.body), ids)
        .copy(sectionId = article.section?.id, leadImage = article.leadImage?.toDraft())

fun draftOf(revision: RevisionDto, ids: IdSource): Draft =
    draftOf(revision.kicker, revision.headline, revision.subheadline, revision.lead, Body.fromJson(revision.body), ids)
        .copy(leadImage = revision.leadImage?.toDraft())

private fun LeadImageDto.toDraft() = DraftLeadImage(mediaId, caption, width, height)

private fun draftOf(kicker: String, headline: String, subheadline: String, lead: String, body: Body, ids: IdSource) = Draft(
    kicker = kicker,
    headline = headline,
    subheadline = subheadline,
    lead = lead,
    blocks = body.blocks.map { block ->
        when (block) {
            is Block.Paragraph -> EditorBlock.Paragraph(ids.next(), block.content)
            is Block.Subhead -> EditorBlock.Subhead(ids.next(), block.text)
            is Block.Quote -> EditorBlock.Quote(ids.next(), block.content)
            is Block.BulletList -> EditorBlock.BulletList(ids.next(), block.items.map { ListItem(ids.next(), it) })
            is Block.Image -> EditorBlock.Image(ids.next(), block.mediaId, block.caption)
        }
    },
)

sealed interface EditorIntent {
    /** Moves the article to another section; its own undo step. */
    data class ChooseSection(val sectionId: Long) : EditorIntent
    /** [ownStep]: a chosen spell-check suggestion, recorded as its own undo step instead of joining the typing. */
    data class EditHeader(val field: HeaderField, val value: String, val ownStep: Boolean = false) : EditorIntent

    /** Sets (or replaces) the lead image; a replaced image's caption is kept. Its own undo step. */
    data class SetLeadImage(val mediaId: Long, val width: Int, val height: Int) : EditorIntent
    data class EditCaption(val value: String, val ownStep: Boolean = false) : EditorIntent
    data object RemoveLeadImage : EditorIntent
    data class EditSubhead(val blockId: Long, val text: String, val ownStep: Boolean = false) : EditorIntent

    /** Typed text of a paragraph or quote ([itemId] `null`) or of a list item. */
    data class EditRuns(val blockId: Long, val itemId: Long?, val runs: List<Run>, val ownStep: Boolean = false) : EditorIntent

    /** Like [EditRuns], but recorded as its own undo step: the result of toggling bold. */
    data class ToggleBold(val blockId: Long, val itemId: Long?, val runs: List<Run>) : EditorIntent

    /** Adds an empty block after [afterId], or at the start if it is `null`; not for [BlockType.IMAGE]. */
    data class AddBlock(val afterId: Long?, val type: BlockType) : EditorIntent

    /** Adds an image block with an uploaded media and an empty caption after [afterId], or at the start if it is `null`. */
    data class AddImageBlock(val afterId: Long?, val mediaId: Long) : EditorIntent

    /** Replaces the image of an image block; its caption is kept. Its own undo step. */
    data class SetBlockImage(val blockId: Long, val mediaId: Long) : EditorIntent
    /** [ownStep]: a chosen spell-check suggestion, recorded as its own undo step instead of joining the typing. */
    data class SetImageCaption(val blockId: Long, val value: String, val ownStep: Boolean = false) : EditorIntent
    data class MoveBlock(val blockId: Long, val delta: Int) : EditorIntent
    data class RemoveBlock(val blockId: Long) : EditorIntent
    data class AddListItem(val blockId: Long, val afterItemId: Long) : EditorIntent

    /** Removing the last item removes the list. */
    data class RemoveListItem(val blockId: Long, val itemId: Long) : EditorIntent
    data object Undo : EditorIntent
    data object Redo : EditorIntent
}

/**
 * Editor state outside the composables (design D3/D4): the [draft] and its undo/redo history of
 * snapshots. Typing in one field within [coalesceMillis] forms a single undo step; every structural
 * change is its own step. [clock] returns milliseconds. State is Compose snapshot state, so text
 * fields read it synchronously. [uploadingAt] and [uploadFailure] describe the running and the last
 * failed upload with where it was started; they are not part of the draft.
 */
class EditorModel(
    initial: Draft,
    private val ids: IdSource,
    private val clock: () -> Long,
    private val historyLimit: Int = 100,
    private val coalesceMillis: Long = 1_000,
) {
    var draft by mutableStateOf(initial)
        private set
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    /** Counts undo and redo steps; fields that keep their own editor state reload when it changes. */
    var restored by mutableStateOf(0)
        private set

    /** Where the running upload was started; `null` while none runs. At most one upload runs at a time. */
    var uploadingAt by mutableStateOf<UploadTarget?>(null)
        private set

    /** The last failed upload with where it was started; a new upload clears it. */
    var uploadFailure by mutableStateOf<Pair<UploadTarget, UploadError>?>(null)
        private set

    val uploading: Boolean get() = uploadingAt != null

    /** Why the last upload started at [target] failed; `null` if it did not. */
    fun uploadErrorAt(target: UploadTarget): UploadError? = uploadFailure?.takeIf { it.first == target }?.second

    private val undoStack = ArrayDeque<Draft>()
    private val redoStack = ArrayDeque<Draft>()

    private var typingKey: Any? = null
    private var typingAt = 0L

    fun dispatch(intent: EditorIntent) {
        when (intent) {
            is EditorIntent.ChooseSection -> change(draft.copy(sectionId = intent.sectionId))
            is EditorIntent.SetLeadImage -> change(
                draft.copy(
                    leadImage = DraftLeadImage(intent.mediaId, draft.leadImage?.caption.orEmpty(), intent.width, intent.height),
                ),
            )
            is EditorIntent.EditCaption -> {
                val image = draft.leadImage ?: return
                change(draft.copy(leadImage = image.copy(caption = singleLine(intent.value, CAPTION_MAX))), typing = CAPTION_KEY.unless(intent.ownStep))
            }
            EditorIntent.RemoveLeadImage -> change(draft.copy(leadImage = null))
            is EditorIntent.EditHeader ->
                change(draft.with(intent.field, singleLine(intent.value, intent.field.maxLength)), typing = intent.field.unless(intent.ownStep))
            is EditorIntent.EditSubhead ->
                change(draft.mapBlock(intent.blockId) { (it as? EditorBlock.Subhead)?.copy(text = singleLine(intent.text, Int.MAX_VALUE)) ?: it }, typing = intent.blockId.unless(intent.ownStep))
            is EditorIntent.EditRuns -> change(draft.withRuns(intent.blockId, intent.itemId, intent.runs), typing = (intent.blockId to intent.itemId).unless(intent.ownStep))
            is EditorIntent.ToggleBold -> change(draft.withRuns(intent.blockId, intent.itemId, intent.runs))
            is EditorIntent.AddBlock -> {
                // An image block needs its media first, see uploadImage
                if (intent.type == BlockType.IMAGE) return
                insert(intent.afterId) { emptyBlock(intent.type) }
            }
            is EditorIntent.AddImageBlock -> insert(intent.afterId) { EditorBlock.Image(ids.next(), intent.mediaId, "") }
            is EditorIntent.SetBlockImage ->
                change(draft.mapBlock(intent.blockId) { (it as? EditorBlock.Image)?.copy(mediaId = intent.mediaId) ?: it })
            is EditorIntent.SetImageCaption -> change(
                draft.mapBlock(intent.blockId) { (it as? EditorBlock.Image)?.copy(caption = singleLine(intent.value, CAPTION_MAX)) ?: it },
                typing = (CAPTION_KEY to intent.blockId).unless(intent.ownStep),
            )
            is EditorIntent.MoveBlock -> {
                val from = draft.indexOf(intent.blockId)
                val to = from + intent.delta
                if (from < 0 || to !in draft.blocks.indices) return
                change(draft.copy(blocks = draft.blocks.toMutableList().apply { add(to, removeAt(from)) }))
            }
            is EditorIntent.RemoveBlock -> change(draft.copy(blocks = draft.blocks.filter { it.id != intent.blockId }))
            is EditorIntent.AddListItem -> change(
                draft.mapBlock(intent.blockId) { block ->
                    if (block !is EditorBlock.BulletList) return@mapBlock block
                    val at = block.items.indexOfFirst { it.id == intent.afterItemId } + 1
                    block.copy(items = block.items.toMutableList().apply { add(at, ListItem(ids.next(), emptyList())) })
                },
            )
            is EditorIntent.RemoveListItem -> {
                val list = draft.blocks.firstOrNull { it.id == intent.blockId } as? EditorBlock.BulletList ?: return
                val items = list.items.filter { it.id != intent.itemId }
                change(
                    if (items.isEmpty()) draft.copy(blocks = draft.blocks.filter { it.id != list.id })
                    else draft.mapBlock(list.id) { list.copy(items = items) },
                )
            }
            EditorIntent.Undo -> step(from = undoStack, to = redoStack)
            EditorIntent.Redo -> step(from = redoStack, to = undoStack)
        }
    }

    /** Uploads [file] with [upload] and makes it the lead image, see [uploadImage]. */
    suspend fun uploadLeadImage(file: PickedFile, upload: suspend (bytes: ByteArray, fileName: String) -> MediaDto) =
        uploadImage(UploadTarget.LeadImage, file, upload)

    /**
     * Uploads [file] with [upload] and uses it at [target]: as lead image, as a new image block or as the new image
     * of an image block. A refused or failed upload changes nothing and sets [uploadFailure]; a new upload clears it.
     * While an upload runs, another one is ignored.
     */
    suspend fun uploadImage(target: UploadTarget, file: PickedFile, upload: suspend (bytes: ByteArray, fileName: String) -> MediaDto) {
        if (uploading) return
        uploadingAt = target
        uploadFailure = null
        try {
            val media = upload(file.bytes, file.name)
            dispatch(
                when (target) {
                    UploadTarget.LeadImage -> EditorIntent.SetLeadImage(media.id, media.width, media.height)
                    is UploadTarget.NewBlock -> EditorIntent.AddImageBlock(target.afterId, media.id)
                    is UploadTarget.Block -> EditorIntent.SetBlockImage(target.blockId, media.id)
                },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Browser fetch failures surface as kotlin.Error, not Exception
            uploadFailure = target to uploadErrorOf(e)
        } finally {
            uploadingAt = null
        }
    }

    /** Lets the user [pick] a file and uploads it for [target], see [uploadImage]; a cancelled pick changes nothing. */
    suspend fun pickAndUpload(
        target: UploadTarget,
        pick: suspend () -> PickedFile?,
        upload: suspend (bytes: ByteArray, fileName: String) -> MediaDto,
    ) {
        pick()?.let { uploadImage(target, it, upload) }
    }

    /** Inserts [block] after [afterId], or at the start if it is `null`; nothing if [afterId] is gone. */
    private fun insert(afterId: Long?, block: () -> EditorBlock) {
        val at = if (afterId == null) 0 else draft.indexOf(afterId) + 1
        if (afterId != null && at == 0) return
        change(draft.copy(blocks = draft.blocks.toMutableList().apply { add(at, block()) }))
    }

    private fun change(next: Draft, typing: Any? = null) {
        val current = draft
        if (next == current) return
        val now = clock()
        val coalesce = typing != null && typing == typingKey && now - typingAt <= coalesceMillis
        if (!coalesce) push(undoStack, current)
        redoStack.clear()
        typingKey = typing
        typingAt = now
        draft = next
        updateFlags()
    }

    private fun step(from: ArrayDeque<Draft>, to: ArrayDeque<Draft>) {
        val target = from.removeLastOrNull() ?: return
        push(to, draft)
        typingKey = null
        draft = target
        restored++
        updateFlags()
    }

    private fun push(stack: ArrayDeque<Draft>, draft: Draft) {
        stack.addLast(draft)
        if (stack.size > historyLimit) stack.removeFirst()
    }

    private fun updateFlags() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    private companion object {
        const val CAPTION_KEY = "caption"
    }

    private fun emptyBlock(type: BlockType): EditorBlock = when (type) {
        BlockType.PARAGRAPH -> EditorBlock.Paragraph(ids.next(), emptyList())
        BlockType.SUBHEAD -> EditorBlock.Subhead(ids.next(), "")
        BlockType.QUOTE -> EditorBlock.Quote(ids.next(), emptyList())
        BlockType.LIST -> EditorBlock.BulletList(ids.next(), listOf(ListItem(ids.next(), emptyList())))
        BlockType.IMAGE -> error("an image block is added with its media, see uploadImage")
    }
}

/** No typing key for an edit that is its own undo step. */
private fun Any.unless(ownStep: Boolean): Any? = if (ownStep) null else this

private fun Draft.indexOf(blockId: Long): Int = blocks.indexOfFirst { it.id == blockId }

private fun Draft.mapBlock(blockId: Long, transform: (EditorBlock) -> EditorBlock): Draft =
    copy(blocks = blocks.map { if (it.id == blockId) transform(it) else it })

private fun Draft.withRuns(blockId: Long, itemId: Long?, runs: List<Run>): Draft = mapBlock(blockId) { block ->
    when (block) {
        is EditorBlock.Paragraph -> block.copy(runs = runs)
        is EditorBlock.Quote -> block.copy(runs = runs)
        is EditorBlock.BulletList -> block.copy(items = block.items.map { if (it.id == itemId) it.copy(runs = runs) else it })
        is EditorBlock.Subhead, is EditorBlock.Image -> block
    }
}
