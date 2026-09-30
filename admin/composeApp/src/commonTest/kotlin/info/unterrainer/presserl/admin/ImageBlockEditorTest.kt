package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.api.ArticleDto
import info.unterrainer.presserl.admin.api.RevisionDto
import info.unterrainer.presserl.admin.api.json
import info.unterrainer.presserl.admin.article.Block
import info.unterrainer.presserl.admin.article.Body
import info.unterrainer.presserl.admin.ui.editor.Autosaver
import info.unterrainer.presserl.admin.ui.editor.BlockType
import info.unterrainer.presserl.admin.ui.editor.CAPTION_MAX
import info.unterrainer.presserl.admin.ui.editor.EditorBlock
import info.unterrainer.presserl.admin.ui.editor.EditorIntent
import info.unterrainer.presserl.admin.ui.editor.EditorModel
import info.unterrainer.presserl.admin.ui.editor.IdSource
import info.unterrainer.presserl.admin.ui.editor.SaveState
import info.unterrainer.presserl.admin.ui.editor.UploadError
import info.unterrainer.presserl.admin.ui.editor.UploadTarget
import info.unterrainer.presserl.admin.ui.editor.draftOf
import info.unterrainer.presserl.admin.ui.media.PickedFile
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Image blocks in the editor model: insert by upload, replace, caption, undo, move, remove, errors, read-only. */
@OptIn(ExperimentalCoroutinesApi::class)
class ImageBlockEditorTest {

    private var now = 0L
    private val ids = IdSource()
    private val file = PickedFile("finish.jpg", byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
    private val model = EditorModel(draftOf(json.decodeFromString<ArticleDto>(ARTICLE), ids), ids, clock = { now })

    /** Ids of the example's blocks: paragraph, subhead, quote, list. */
    private val paragraph = model.draft.blocks[0].id
    private val subhead = model.draft.blocks[1].id

    private fun dispatch(intent: EditorIntent, afterMillis: Long = 5_000) {
        now += afterMillis
        model.dispatch(intent)
    }

    private fun types() = model.draft.body().blocks.map { it::class.simpleName }

    private fun image(index: Int) = model.draft.blocks[index] as EditorBlock.Image

    /** Answers uploads with [status] (`201` answers media [mediaId]); records the upload requests. */
    private class MediaServer(val status: HttpStatusCode = HttpStatusCode.Created, val mediaId: Long = 17) {
        val uploads = mutableListOf<String>()
        val api = ApiClient(
            HttpClient(MockEngine { request ->
                uploads += request.url.encodedPath
                if (status == HttpStatusCode.Created) {
                    respond("""{ "id": $mediaId, "contentType": "image/jpeg", "width": 1600, "height": 1067, "size": 298114,
                        "uploadedBy": { "username": "papa", "displayName": "Papa" }, "uploadedAt": "2026-09-27T14:03:11.402Z",
                        "renditions": { "thumbnail": { "width": 480, "height": 320, "size": 31877 } } }""",
                        status, headersOf(HttpHeaders.ContentType, "application/json"))
                } else {
                    respond("""{ "errors": [{ "field": "file", "message": "refused" }] }""", status,
                        headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }),
            "https://news.example.org",
        ) { "token" }
    }

    @Test
    fun uploadInsertsAnImageBlockWhereTheMenuWasOpened() = runTest {
        val server = MediaServer()

        model.pickAndUpload(UploadTarget.NewBlock(paragraph), { file }, server.api::uploadMedia)

        assertEquals(listOf("/api/media"), server.uploads)
        assertEquals(listOf("Paragraph", "Image", "Subhead", "Quote", "BulletList"), types())
        assertEquals(Block.Image(17), model.draft.body().blocks[1])
        assertFalse(model.uploading)
        assertNull(model.uploadFailure)
        assertTrue(model.canUndo)
    }

    @Test
    fun uploadAtTheStart() = runTest {
        model.uploadImage(UploadTarget.NewBlock(null), file, MediaServer(mediaId = 18).api::uploadMedia)

        assertEquals(Block.Image(18), model.draft.body().blocks.first())
    }

    @Test
    fun cancellingThePickerInsertsNothing() = runTest {
        val server = MediaServer()
        val before = model.draft

        model.pickAndUpload(UploadTarget.NewBlock(paragraph), { null }, server.api::uploadMedia)

        assertEquals(before, model.draft)
        assertEquals(emptyList(), server.uploads)
        assertFalse(model.canUndo)
    }

    @Test
    fun refusedUploadInsertsNothingAndIsExplainedAtTheMenu() = runTest {
        val before = model.draft
        val target = UploadTarget.NewBlock(paragraph)

        model.pickAndUpload(target, { file }, MediaServer(HttpStatusCode.UnsupportedMediaType).api::uploadMedia)

        assertEquals(before, model.draft)
        assertEquals(UploadError.Unsupported, model.uploadErrorAt(target))
        assertNull(model.uploadErrorAt(UploadTarget.LeadImage))
        assertNull(model.uploadErrorAt(UploadTarget.NewBlock(subhead)))
        assertFalse(model.uploading)
    }

    @Test
    fun theMenuDoesNotAddAnEmptyImageBlock() {
        dispatch(EditorIntent.AddBlock(paragraph, BlockType.IMAGE))

        assertEquals(4, model.draft.blocks.size)
        assertFalse(model.canUndo)
    }

    @Test
    fun replacingKeepsTheCaption() = runTest {
        dispatch(EditorIntent.AddImageBlock(paragraph, 17))
        val block = image(1).id
        dispatch(EditorIntent.SetImageCaption(block, "Our class"))

        model.uploadImage(UploadTarget.Block(block), file, MediaServer(mediaId = 18).api::uploadMedia)

        assertEquals(Block.Image(18, "Our class"), model.draft.body().blocks[1])
        assertEquals(block, image(1).id)
    }

    @Test
    fun failedReplacementKeepsTheImageAndIsExplainedAtTheBlock() = runTest {
        dispatch(EditorIntent.AddImageBlock(paragraph, 17))
        val target = UploadTarget.Block(image(1).id)

        model.uploadImage(target, file, MediaServer(HttpStatusCode.PayloadTooLarge).api::uploadMedia)

        assertEquals(17, image(1).mediaId)
        assertEquals(UploadError.TooLarge, model.uploadErrorAt(target))
    }

    @Test
    fun captionIsSingleLineAndLimited() {
        dispatch(EditorIntent.AddImageBlock(paragraph, 17))
        val block = image(1).id

        dispatch(EditorIntent.SetImageCaption(block, "x".repeat(CAPTION_MAX + 5)))
        assertEquals(CAPTION_MAX, image(1).caption.length)

        dispatch(EditorIntent.SetImageCaption(block, "The\nfinish\tline"))
        assertEquals("The finish line", image(1).caption)
        assertEquals(Block.Image(17, "The finish line"), model.draft.body().blocks[1])
    }

    @Test
    fun captionTypingIsOneUndoStepAndInsertIsAnother() {
        dispatch(EditorIntent.AddImageBlock(paragraph, 17))
        val block = image(1).id
        dispatch(EditorIntent.SetImageCaption(block, "The"))
        dispatch(EditorIntent.SetImageCaption(block, "The finish"), afterMillis = 200)
        dispatch(EditorIntent.SetImageCaption(block, "The finish line"), afterMillis = 200)

        dispatch(EditorIntent.Undo)
        assertEquals("", image(1).caption)
        dispatch(EditorIntent.Undo)
        assertEquals(4, model.draft.blocks.size)

        dispatch(EditorIntent.Redo)
        assertEquals(Block.Image(17), model.draft.body().blocks[1])
        dispatch(EditorIntent.Redo)
        assertEquals("The finish line", image(1).caption)
    }

    @Test
    fun imageBlocksMoveAndAreRemovedLikeOthers() {
        dispatch(EditorIntent.AddImageBlock(subhead, 17))
        val block = image(2).id

        dispatch(EditorIntent.MoveBlock(block, -1))
        assertEquals(listOf("Paragraph", "Image", "Subhead", "Quote", "BulletList"), types())

        dispatch(EditorIntent.RemoveBlock(block))
        assertEquals(listOf("Paragraph", "Subhead", "Quote", "BulletList"), types())

        dispatch(EditorIntent.Undo)
        assertEquals(Block.Image(17), model.draft.body().blocks[1])
    }

    @Test
    fun theAutosaveSendsTheImageBlockAtItsPosition() = runTest {
        val sent = mutableListOf<JsonElement>()
        val engine = MockEngine(
            MockEngineConfig().apply {
                dispatcher = UnconfinedTestDispatcher(testScheduler)
                addHandler { request ->
                    val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                    sent += Json.parseToJsonElement(body).jsonObject.getValue("body")
                    respond(ARTICLE.replace("\"version\": 5", "\"version\": 6"), HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"))
                }
            },
        )
        val api = ApiClient(HttpClient(engine), "https://news.example.org") { "token" }
        val saver = Autosaver(backgroundScope, model.draft.toContent(), version = 5,
            save = { content, version -> api.updateArticle(42, content, version) })

        model.uploadImage(UploadTarget.NewBlock(paragraph), file, MediaServer(mediaId = 17).api::uploadMedia)
        dispatch(EditorIntent.SetImageCaption(image(1).id, "The finish line"))
        saver.changed(model.draft.toContent())
        advanceTimeBy(2_000)
        runCurrent()

        assertEquals(1, sent.size)
        assertEquals(
            Json.parseToJsonElement("""{ "type": "image", "mediaId": 17, "caption": "The finish line" }"""),
            sent.single().jsonObject.getValue("blocks").jsonArray[1],
        )
    }

    @Test
    fun aRefusedMediaIdShowsAtItsBlock() = runTest {
        val engine = MockEngine(
            MockEngineConfig().apply {
                dispatcher = UnconfinedTestDispatcher(testScheduler)
                addHandler {
                    respond("""{ "errors": [ { "field": "body.blocks[1].mediaId", "message": "media 17 does not exist" } ] }""",
                        HttpStatusCode.BadRequest, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            },
        )
        val api = ApiClient(HttpClient(engine), "https://news.example.org") { "token" }
        val saver = Autosaver(backgroundScope, model.draft.toContent(), version = 5,
            save = { content, version -> api.updateArticle(42, content, version) })

        dispatch(EditorIntent.AddImageBlock(paragraph, 17))
        saver.changed(model.draft.toContent())
        advanceTimeBy(2_000)
        runCurrent()

        val state = assertIs<SaveState.Invalid>(saver.state.value)
        assertEquals(mapOf(1 to "media 17 does not exist"), state.errors.blocks)
        assertIs<EditorBlock.Image>(model.draft.blocks[1])
    }

    @Test
    fun reopeningAndEarlierRevisionsKeepImageBlocks() {
        val body = """{ "version": 1, "blocks": [
            { "type": "paragraph", "content": [ { "text": "We went to the zoo." } ] },
            { "type": "image", "mediaId": 19, "caption": "Our class" },
            { "type": "image", "mediaId": 20 } ] }"""
        val revision = json.decodeFromString<RevisionDto>(
            """{ "number": 1, "live": true, "createdAt": "2026-09-26T10:00:00Z", "updatedAt": "2026-09-26T10:05:00Z",
                "publishedAt": "2026-09-26T10:01:00Z", "kicker": "", "headline": "Zoo", "subheadline": "", "lead": "",
                "body": $body }""",
        )

        val draft = draftOf(revision, IdSource())

        assertEquals(EditorBlock.Image(draft.blocks[1].id, 19, "Our class"), draft.blocks[1])
        assertEquals(EditorBlock.Image(draft.blocks[2].id, 20, ""), draft.blocks[2])
        assertEquals(Json.parseToJsonElement(body).jsonObject, draft.body().toJson())
        assertEquals(Body.fromJson(Json.parseToJsonElement(body).jsonObject), draft.body())
    }
}
