package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.api.ArticleDto
import info.unterrainer.presserl.admin.api.AuthorDto
import info.unterrainer.presserl.admin.api.FieldErrorDto
import info.unterrainer.presserl.admin.api.LeadImageRequest
import info.unterrainer.presserl.admin.api.MediaListItemDto
import info.unterrainer.presserl.admin.api.json
import info.unterrainer.presserl.admin.ui.editor.Autosaver
import info.unterrainer.presserl.admin.ui.editor.CAPTION_MAX
import info.unterrainer.presserl.admin.ui.editor.DraftLeadImage
import info.unterrainer.presserl.admin.ui.editor.EditorIntent
import info.unterrainer.presserl.admin.ui.editor.EditorModel
import info.unterrainer.presserl.admin.ui.editor.IdSource
import info.unterrainer.presserl.admin.ui.editor.ImageTarget
import info.unterrainer.presserl.admin.ui.editor.draftOf
import info.unterrainer.presserl.admin.ui.editor.fieldErrors
import info.unterrainer.presserl.admin.ui.media.Thumbnails
import info.unterrainer.presserl.admin.ui.media.formatMaxSize
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The lead image in the editor model: pick, caption, remove, undo, autosave, previews. */
@OptIn(ExperimentalCoroutinesApi::class)
class LeadImageEditorTest {

    private var now = 0L
    private val ids = IdSource()

    private fun model(article: String = ARTICLE) =
        EditorModel(draftOf(json.decodeFromString<ArticleDto>(article), ids), ids, clock = { now })

    /** A media of the newspaper as the media picker hands it over. */
    private fun picked(id: Long, width: Int = 1600, height: Int = 1067) =
        MediaListItemDto(id, 0, "image/jpeg", width, height, 298114, AuthorDto("papa", "Papa"), "2026-09-27T14:03:11.402Z")

    private fun EditorModel.at(afterMillis: Long = 5_000, intent: EditorIntent) {
        now += afterMillis
        dispatch(intent)
    }

    @Test
    fun pickSetsTheLeadImage() {
        val model = model()

        model.useImage(ImageTarget.LeadImage, picked(17))

        assertEquals(DraftLeadImage(17, "", 1600, 1067), model.draft.leadImage)
        assertEquals(LeadImageRequest(17, ""), model.draft.toContent().leadImage)
        assertTrue(model.canUndo)
    }

    @Test
    fun pickIsAutosavedWithTheLeadImageAndNothingIsUploaded() = runTest {
        val model = model()
        val requests = mutableListOf<String>()
        val sent = mutableListOf<JsonElement?>()
        val engine = MockEngine(
            MockEngineConfig().apply {
                dispatcher = UnconfinedTestDispatcher(testScheduler)
                addHandler { request ->
                    requests += "${request.method.value} ${request.url.encodedPath}"
                    val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                    sent += Json.parseToJsonElement(body).jsonObject["leadImage"]
                    respond(ARTICLE.replace("\"version\": 5", "\"version\": 6"), HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"))
                }
            },
        )
        val api = ApiClient(HttpClient(engine), "https://news.example.org") { "token" }
        val saver = Autosaver(backgroundScope, model.draft.toContent(), version = 5,
            save = { content, version -> api.updateArticle(42, content, version) })

        model.useImage(ImageTarget.LeadImage, picked(17))
        model.at(intent = EditorIntent.EditCaption("Our cat Minka"))
        saver.changed(model.draft.toContent())
        advanceTimeBy(2_000)
        runCurrent()

        assertEquals(listOf("PUT /api/articles/42"), requests)
        assertEquals(listOf<JsonElement?>(buildJsonObject { put("mediaId", 17); put("caption", "Our cat Minka") }), sent)
    }

    @Test
    fun replacingKeepsTheCaptionAndIsUndoable() {
        val model = model()
        model.useImage(ImageTarget.LeadImage, picked(17))
        model.at(intent = EditorIntent.EditCaption("Our class"))

        model.useImage(ImageTarget.LeadImage, picked(18, width = 800, height = 1200))

        assertEquals(DraftLeadImage(18, "Our class", 800, 1200), model.draft.leadImage)
        assertEquals(LeadImageRequest(18, "Our class"), model.draft.toContent().leadImage)

        model.at(intent = EditorIntent.Undo)
        assertEquals(DraftLeadImage(17, "Our class", 1600, 1067), model.draft.leadImage)
    }

    @Test
    fun removeSendsNullAndIsUndoable() = runTest {
        val model = model(withLeadImage())
        model.at(intent = EditorIntent.RemoveLeadImage)

        assertNull(model.draft.leadImage)
        assertNull(model.draft.toContent().leadImage)

        model.at(intent = EditorIntent.Undo)
        assertEquals(LeadImageRequest(17, "Our cat Minka"), model.draft.toContent().leadImage)
        model.at(intent = EditorIntent.Redo)
        assertNull(model.draft.leadImage)
    }

    @Test
    fun reopeningSendsTheSameLeadImage() {
        val article = json.decodeFromString<ArticleDto>(withLeadImage())
        val model = EditorModel(draftOf(article, ids), ids, clock = { now })

        assertEquals(DraftLeadImage(17, "Our cat Minka", 4096, 2731), model.draft.leadImage)
        assertEquals(LeadImageRequest(17, "Our cat Minka"), model.draft.toContent().leadImage)
        assertEquals(draftOf(article, IdSource()).toContent(), model.draft.toContent())
        assertFalse(model.canUndo)
    }

    @Test
    fun captionTypingIsOneUndoStep() {
        val model = model(withLeadImage())
        model.at(intent = EditorIntent.EditCaption("Our cat Minka!"))
        model.at(afterMillis = 200, intent = EditorIntent.EditCaption("Our cat Minka!!"))

        model.at(intent = EditorIntent.Undo)

        assertEquals("Our cat Minka", model.draft.leadImage?.caption)
    }

    @Test
    fun captionLimitsAndLineBreaks() {
        val model = model(withLeadImage())

        model.at(intent = EditorIntent.EditCaption("x".repeat(CAPTION_MAX + 5)))
        assertEquals(CAPTION_MAX, model.draft.leadImage?.caption?.length)

        model.at(intent = EditorIntent.EditCaption("Our\ncat\tMinka"))
        assertEquals("Our cat Minka", model.draft.leadImage?.caption)
    }

    @Test
    fun captionWithoutImageIsIgnored() {
        val model = model()

        model.at(intent = EditorIntent.EditCaption("Nothing to caption"))

        assertNull(model.draft.leadImage)
        assertFalse(model.canUndo)
    }

    @Test
    fun serverErrorsOfTheLeadImageBelongToItsField() {
        val errors = fieldErrors(listOf(FieldErrorDto("leadImage.mediaId", "media 99 does not exist"),
            FieldErrorDto("leadImage.caption", "must be at most 300 characters")))

        assertEquals("media 99 does not exist", errors.leadImage)
        assertEquals(emptyList(), errors.general)
    }

    @Test
    fun thumbnailsAreFetchedOncePerMedia() = runTest {
        val fetched = mutableListOf<Long>()
        val image = FakeImageBitmap(3, 2)
        val thumbnails = Thumbnails(decode = { if (it.isEmpty()) null else image }) { id ->
            fetched += id
            if (id == 99L) error("404") else byteArrayOf(1)
        }

        thumbnails.fetch(17)
        thumbnails.fetch(17)
        thumbnails.fetch(99)
        thumbnails.fetch(99)

        assertEquals(listOf(17L, 99L), fetched)
        assertEquals(Thumbnails.Result.Loaded(image), thumbnails[17])
        assertEquals(Thumbnails.Result.Missing, thumbnails[99])
        assertNull(thumbnails[18])
    }

    @Test
    fun maxSizeForPeople() {
        assertEquals("10 MB", formatMaxSize("10M"))
        assertEquals("512 KB", formatMaxSize("512k"))
        assertEquals("1 GB", formatMaxSize(" 1G "))
        assertEquals("1048576", formatMaxSize("1048576"))
    }

    private fun withLeadImage() = ARTICLE.trimEnd().removeSuffix("}") +
        """, "leadImage": { "mediaId": 17, "caption": "Our cat Minka", "width": 4096, "height": 2731 } }"""

}
