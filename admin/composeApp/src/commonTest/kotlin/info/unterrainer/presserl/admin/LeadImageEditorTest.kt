package info.unterrainer.presserl.admin

import androidx.compose.ui.graphics.ImageBitmap
import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.api.ArticleDto
import info.unterrainer.presserl.admin.api.FieldErrorDto
import info.unterrainer.presserl.admin.api.LeadImageRequest
import info.unterrainer.presserl.admin.api.json
import info.unterrainer.presserl.admin.ui.editor.Autosaver
import info.unterrainer.presserl.admin.ui.editor.CAPTION_MAX
import info.unterrainer.presserl.admin.ui.editor.DraftLeadImage
import info.unterrainer.presserl.admin.ui.editor.EditorIntent
import info.unterrainer.presserl.admin.ui.editor.EditorModel
import info.unterrainer.presserl.admin.ui.editor.IdSource
import info.unterrainer.presserl.admin.ui.editor.UploadError
import info.unterrainer.presserl.admin.ui.editor.UploadTarget
import info.unterrainer.presserl.admin.ui.editor.draftOf
import info.unterrainer.presserl.admin.ui.editor.fieldErrors
import info.unterrainer.presserl.admin.ui.media.PickedFile
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

/** The lead image in the editor model: upload, errors, caption, remove, undo, autosave, previews. */
@OptIn(ExperimentalCoroutinesApi::class)
class LeadImageEditorTest {

    private var now = 0L
    private val ids = IdSource()
    private val file = PickedFile("minka.jpg", byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))

    private fun model(article: String = ARTICLE) =
        EditorModel(draftOf(json.decodeFromString<ArticleDto>(article), ids), ids, clock = { now })

    private fun EditorModel.at(afterMillis: Long = 5_000, intent: EditorIntent) {
        now += afterMillis
        dispatch(intent)
    }

    /** The media server: answers uploads with [status] (`201` answers media [mediaId]); records the uploads. */
    private class MediaServer(val status: HttpStatusCode = HttpStatusCode.Created, val mediaId: Long = 17) {
        val uploads = mutableListOf<String>()
        val api = ApiClient(
            HttpClient(MockEngine { request ->
                uploads += request.url.encodedPath
                if (status == HttpStatusCode.Created) {
                    respond(media(mediaId), status, headersOf(HttpHeaders.ContentType, "application/json"))
                } else {
                    respond("""{ "errors": [{ "field": "file", "message": "refused" }] }""", status,
                        headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }),
            "https://news.example.org",
        ) { "token" }

        private fun media(id: Long) = """{ "id": $id, "contentType": "image/jpeg", "width": 1600, "height": 1067, "size": 298114,
            "uploadedBy": { "username": "papa", "displayName": "Papa" }, "uploadedAt": "2026-09-27T14:03:11.402Z",
            "renditions": { "thumbnail": { "width": 480, "height": 320, "size": 31877 } } }"""
    }

    @Test
    fun uploadSetsTheLeadImage() = runTest {
        val model = model()
        val server = MediaServer()

        model.uploadLeadImage(file, server.api::uploadMedia)

        assertEquals(listOf("/api/media"), server.uploads)
        assertEquals(DraftLeadImage(17, "", 1600, 1067), model.draft.leadImage)
        assertEquals(LeadImageRequest(17, ""), model.draft.toContent().leadImage)
        assertFalse(model.uploading)
        assertNull(model.uploadErrorAt(UploadTarget.LeadImage))
        assertTrue(model.canUndo)
    }

    @Test
    fun uploadIsAutosavedWithTheLeadImage() = runTest {
        val model = model()
        val sent = mutableListOf<JsonElement?>()
        val engine = MockEngine(
            MockEngineConfig().apply {
                dispatcher = UnconfinedTestDispatcher(testScheduler)
                addHandler { request ->
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

        model.uploadLeadImage(file, MediaServer().api::uploadMedia)
        model.at(intent = EditorIntent.EditCaption("Our cat Minka"))
        saver.changed(model.draft.toContent())
        advanceTimeBy(2_000)
        runCurrent()

        assertEquals(listOf<JsonElement?>(buildJsonObject { put("mediaId", 17); put("caption", "Our cat Minka") }), sent)
    }

    @Test
    fun everyRefusalKeepsThePreviousImageAndIsExplained() = runTest {
        val expected = mapOf(
            HttpStatusCode.PayloadTooLarge to UploadError.TooLarge,
            HttpStatusCode.UnsupportedMediaType to UploadError.Unsupported,
            HttpStatusCode.BadRequest to UploadError.Damaged,
            HttpStatusCode.ServiceUnavailable to UploadError.Unreachable,
            HttpStatusCode.InternalServerError to UploadError.Unreachable,
        )
        expected.forEach { (status, error) ->
            val model = model()
            model.uploadLeadImage(file, MediaServer().api::uploadMedia)
            model.at(intent = EditorIntent.EditCaption("Minka"))

            model.uploadLeadImage(file, MediaServer(status).api::uploadMedia)

            assertEquals(error, model.uploadErrorAt(UploadTarget.LeadImage), status.toString())
            assertEquals(DraftLeadImage(17, "Minka", 1600, 1067), model.draft.leadImage, status.toString())
            assertFalse(model.uploading)
        }
    }

    @Test
    fun networkFailureIsUnreachable() = runTest {
        val model = model()

        model.uploadLeadImage(file) { _, _ -> throw Error("Fail to fetch") }

        assertEquals(UploadError.Unreachable, model.uploadErrorAt(UploadTarget.LeadImage))
        assertNull(model.draft.leadImage)
    }

    @Test
    fun otherRefusalsNameTheStatus() = runTest {
        val model = model()

        model.uploadLeadImage(file, MediaServer(HttpStatusCode.Forbidden).api::uploadMedia)

        assertEquals(UploadError.Other("403 Forbidden"), model.uploadErrorAt(UploadTarget.LeadImage))
    }

    @Test
    fun aNewUploadClearsTheError() = runTest {
        val model = model()
        model.uploadLeadImage(file, MediaServer(HttpStatusCode.UnsupportedMediaType).api::uploadMedia)

        model.uploadLeadImage(file, MediaServer().api::uploadMedia)

        assertNull(model.uploadErrorAt(UploadTarget.LeadImage))
        assertEquals(17, model.draft.leadImage?.mediaId)
    }

    @Test
    fun replacingKeepsTheCaption() = runTest {
        val model = model()
        model.uploadLeadImage(file, MediaServer(mediaId = 17).api::uploadMedia)
        model.at(intent = EditorIntent.EditCaption("Our cat Minka"))

        model.uploadLeadImage(file, MediaServer(mediaId = 18).api::uploadMedia)

        assertEquals(LeadImageRequest(18, "Our cat Minka"), model.draft.toContent().leadImage)
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
        val image = ImageBitmap(3, 2)
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
