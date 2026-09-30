package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.api.ArticleDto
import info.unterrainer.presserl.admin.api.AuthorDto
import info.unterrainer.presserl.admin.api.MediaListItemDto
import info.unterrainer.presserl.admin.api.RevisionDto
import info.unterrainer.presserl.admin.api.json
import info.unterrainer.presserl.admin.article.Block
import info.unterrainer.presserl.admin.article.Body
import info.unterrainer.presserl.admin.ui.editor.imageCaptionKey
import info.unterrainer.presserl.admin.ui.editor.Autosaver
import info.unterrainer.presserl.admin.ui.editor.BlockType
import info.unterrainer.presserl.admin.ui.editor.CAPTION_MAX
import info.unterrainer.presserl.admin.ui.editor.EditorBlock
import info.unterrainer.presserl.admin.ui.editor.EditorIntent
import info.unterrainer.presserl.admin.ui.editor.EditorModel
import info.unterrainer.presserl.admin.ui.editor.IdSource
import info.unterrainer.presserl.admin.ui.editor.ImageTarget
import info.unterrainer.presserl.admin.ui.editor.SaveState
import info.unterrainer.presserl.admin.ui.editor.draftOf
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
import kotlin.test.assertTrue

/** Image blocks in the editor model: insert by pick, replace, caption, undo, move, remove, errors, read-only. */
@OptIn(ExperimentalCoroutinesApi::class)
class ImageBlockEditorTest {

    private var now = 0L
    private val ids = IdSource()
    private val model = EditorModel(draftOf(json.decodeFromString<ArticleDto>(ARTICLE), ids), ids, clock = { now })

    /** Ids of the example's blocks: paragraph, subhead, quote, list. */
    private val paragraph = model.draft.blocks[0].id
    private val subhead = model.draft.blocks[1].id

    private fun dispatch(intent: EditorIntent, afterMillis: Long = 5_000) {
        now += afterMillis
        model.dispatch(intent)
    }

    /** A media of the newspaper as the media picker hands it over. */
    private fun picked(id: Long) =
        MediaListItemDto(id, 0, "image/jpeg", 1600, 1067, 298114, AuthorDto("papa", "Papa"), "2026-09-27T14:03:11.402Z")

    private fun types() = model.draft.body().blocks.map { it::class.simpleName }

    private fun image(index: Int) = model.draft.blocks[index] as EditorBlock.Image

    @Test
    fun pickInsertsAnImageBlockWhereTheMenuWasOpened() {
        model.useImage(ImageTarget.NewBlock(paragraph), picked(17))

        assertEquals(listOf("Paragraph", "Image", "Subhead", "Quote", "BulletList"), types())
        assertEquals(Block.Image(17), model.draft.body().blocks[1])
        assertTrue(model.canUndo)
    }

    @Test
    fun pickAtTheStart() {
        model.useImage(ImageTarget.NewBlock(null), picked(18))

        assertEquals(Block.Image(18), model.draft.body().blocks.first())
    }

    @Test
    fun undoRemovesAnInsertedImage() {
        val before = model.draft.body()
        model.useImage(ImageTarget.NewBlock(paragraph), picked(17))

        dispatch(EditorIntent.Undo)

        assertEquals(before, model.draft.body())
        assertFalse(model.canUndo)
    }

    @Test
    fun theSameMediaAsLeadImageAndInABlock() {
        model.useImage(ImageTarget.LeadImage, picked(17))
        model.useImage(ImageTarget.NewBlock(paragraph), picked(17))

        val content = model.draft.toContent()
        assertEquals(17, content.leadImage?.mediaId)
        assertEquals(Block.Image(17), model.draft.body().blocks[1])
    }

    @Test
    fun theMenuDoesNotAddAnEmptyImageBlock() {
        dispatch(EditorIntent.AddBlock(paragraph, BlockType.IMAGE))

        assertEquals(4, model.draft.blocks.size)
        assertFalse(model.canUndo)
    }

    @Test
    fun replacingKeepsTheCaption() {
        dispatch(EditorIntent.AddImageBlock(paragraph, 17))
        val block = image(1).id
        dispatch(EditorIntent.SetImageCaption(block, "Our class"))

        model.useImage(ImageTarget.Block(block), picked(18))

        assertEquals(Block.Image(18, "Our class"), model.draft.body().blocks[1])
        assertEquals(block, image(1).id)
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
    fun aChosenCaptionSuggestionIsItsOwnUndoStep() {
        dispatch(EditorIntent.AddImageBlock(paragraph, 17))
        val block = image(1).id
        dispatch(EditorIntent.SetImageCaption(block, "Unser"))
        dispatch(EditorIntent.SetImageCaption(block, "Unser Klasenfoto"), afterMillis = 200)
        // the suggestion right after typing still starts its own step
        dispatch(EditorIntent.SetImageCaption(block, "Unser Klassenfoto", ownStep = true), afterMillis = 200)
        assertEquals("Unser Klassenfoto", image(1).caption)

        dispatch(EditorIntent.Undo)
        assertEquals("Unser Klasenfoto", image(1).caption)
        dispatch(EditorIntent.Undo)
        assertEquals("", image(1).caption)
    }

    @Test
    fun captionSpellCheckKeyIsItsOwn() {
        assertEquals("caption:7", imageCaptionKey(7))
        assertFalse(imageCaptionKey(7) == "block:7" || imageCaptionKey(7) == "caption")
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

        model.useImage(ImageTarget.NewBlock(paragraph), picked(17))
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
