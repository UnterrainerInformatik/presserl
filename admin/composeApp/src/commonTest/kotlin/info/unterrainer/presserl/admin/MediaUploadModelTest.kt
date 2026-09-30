package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.api.AuthorDto
import info.unterrainer.presserl.admin.api.MediaDetailsRequest
import info.unterrainer.presserl.admin.api.MediaDto
import info.unterrainer.presserl.admin.api.MediaFilter
import info.unterrainer.presserl.admin.api.MediaListItemDto
import info.unterrainer.presserl.admin.api.MediaPage
import info.unterrainer.presserl.admin.api.asListItem
import info.unterrainer.presserl.admin.ui.media.MediaDetailsError
import info.unterrainer.presserl.admin.ui.media.MediaDetailsModel
import info.unterrainer.presserl.admin.ui.media.MediaGridModel
import info.unterrainer.presserl.admin.ui.media.MediaUploadModel
import info.unterrainer.presserl.admin.ui.media.MediaUploadModel.State
import info.unterrainer.presserl.admin.ui.media.PickableFile
import info.unterrainer.presserl.admin.ui.media.TagChips
import info.unterrainer.presserl.admin.ui.media.TagProblem
import info.unterrainer.presserl.admin.ui.media.UploadError
import info.unterrainer.presserl.admin.ui.media.chooseForUpload
import info.unterrainer.presserl.admin.ui.media.normalizeTag
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaUploadModelTest {

    private data class Upload(val name: String, val description: String?, val tags: List<String>)

    /** Answers uploads with ids from 100 on; files whose name is in [refused] get [status]. */
    private class MediaServer(val refused: Set<String> = emptySet(), val status: HttpStatusCode = HttpStatusCode.PayloadTooLarge) {
        val requests = mutableListOf<String>()
        private var nextId = 100L
        val api = ApiClient(
            HttpClient(MockEngine { request ->
                val body = request.body.toByteArray().decodeToString()
                val name = Regex("filename=\"([^\"]*)\"").find(body)!!.groupValues[1]
                requests += name
                if (name in refused) {
                    respond("""{ "errors": [{ "field": "file", "message": "refused" }] }""", status,
                        headersOf(HttpHeaders.ContentType, "application/json"))
                } else {
                    respond(media(nextId++), HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }),
            "https://news.example.org",
        ) { "token" }

        private fun media(id: Long) = """{ "id": $id, "contentType": "image/jpeg", "width": 1600, "height": 1067, "size": 298114,
            "uploadedBy": { "username": "anna", "displayName": "Anna" }, "uploadedAt": "2026-09-30T10:00:00Z",
            "tags": ["Sportfest"] }"""
    }

    private fun file(name: String, size: Long = 3_000_000, read: MutableList<String>? = null) =
        PickableFile(name, size) {
            read?.add(name)
            byteArrayOf(1, 2, 3)
        }

    private fun item(id: Long) =
        MediaListItemDto(id, 0, "image/jpeg", 1600, 1067, 1000, AuthorDto("papa", "Papa"), "2026-09-27T14:03:11Z", usageCount = 2)

    private fun media(id: Long, description: String? = null, tags: List<String> = emptyList()) =
        MediaDto(id, "image/jpeg", 1600, 1067, 1000, AuthorDto("papa", "Papa"), "t", description = description, tags = tags)

    // --- upload

    @Test
    fun threePhotosWithTags() = runTest {
        val server = MediaServer()
        val sent = mutableListOf<Upload>()
        val read = mutableListOf<String>()
        val grid = MediaGridModel { _, _ -> MediaPage(listOf(item(5)), null) }
        grid.setFilter(MediaFilter(tags = listOf("Feuerwehr")))
        val model = MediaUploadModel(
            listOf(file("a.jpg", read = read), file("b.jpg", read = read), file("c.jpg", read = read)),
            { bytes, name, description, tags ->
                sent += Upload(name, description, tags)
                server.api.uploadMedia(bytes, name, description, tags)
            },
        ) { grid.prepend(it.asListItem()) }
        model.tags.text = "Sportfest"
        model.description = " Foto: Anna "

        model.start()

        assertEquals(listOf("a.jpg", "b.jpg", "c.jpg"), server.requests)
        assertEquals(listOf("a.jpg", "b.jpg", "c.jpg"), read)
        assertEquals(List(3) { i -> Upload("abc"[i] + ".jpg", "Foto: Anna", listOf("Sportfest")) }, sent)
        assertTrue(model.entries.all { it.state is State.Done })
        assertTrue(model.allDone)
        assertFalse(model.hasPending)
        assertEquals(listOf(102L, 101L, 100L, 5L), grid.items.map { it.id })
        assertEquals(listOf(0L, 0L, 0L, 2L), grid.items.map { it.usageCount })
    }

    @Test
    fun oneFileTooLarge() = runTest {
        val server = MediaServer(refused = setOf("big.jpg"))
        val uploaded = mutableListOf<Long>()
        val model = MediaUploadModel(listOf(file("a.jpg"), file("big.jpg", 12_000_000), file("c.jpg")), server.api::uploadMedia) {
            uploaded += it.id
        }

        model.start()

        assertEquals(listOf("a.jpg", "big.jpg", "c.jpg"), server.requests)
        assertEquals(listOf(100L, 101L), uploaded)
        val big = model.entries[1]
        assertEquals(State.Failed(UploadError.TooLarge), big.state)
        assertTrue(model.hasPending)
        assertFalse(model.allDone)

        model.retry(big)
        assertEquals(State.Failed(UploadError.TooLarge), big.state)
        assertEquals(listOf("a.jpg", "big.jpg", "c.jpg", "big.jpg"), server.requests)

        model.remove(big)
        assertEquals(listOf("a.jpg", "c.jpg"), model.entries.map { it.file.name })
        assertTrue(model.allDone)
        assertFalse(model.hasPending)
    }

    @Test
    fun retryUploadsAFailedFileAgain() = runTest {
        var down = true
        val model = MediaUploadModel(listOf(file("a.jpg")), { _, _, _, _ -> if (down) error("offline") else media(7) }) {}

        model.start()
        assertEquals(State.Failed(UploadError.Unreachable), model.entries.single().state)
        down = false
        model.retry(model.entries.single())

        assertEquals(State.Done(media(7)), model.entries.single().state)
    }

    @Test
    fun cancelThePicker() = runTest {
        val server = MediaServer()

        assertNull(chooseForUpload({ null }, server.api::uploadMedia) {})
        assertNull(chooseForUpload({ emptyList() }, server.api::uploadMedia) {})
        assertEquals(emptyList(), server.requests)
        val chosen = chooseForUpload({ listOf(file("a.jpg"), file("b.jpg")) }, server.api::uploadMedia) {}
        assertEquals(listOf("a.jpg", "b.jpg"), chosen!!.entries.map { it.file.name })
        assertTrue(chosen.entries.all { it.state == State.Waiting })
        assertEquals(emptyList(), server.requests)
    }

    @Test
    fun invalidTypedTagStopsTheStart() = runTest {
        val server = MediaServer()
        val model = MediaUploadModel(listOf(file("a.jpg")), server.api::uploadMedia) {}
        model.tags.text = "x".repeat(41)

        model.start()

        assertFalse(model.started)
        assertEquals(TagProblem.TOO_LONG, model.tags.problem)
        assertEquals(emptyList(), server.requests)
    }

    @Test
    fun waitingFilesCanBeRemovedBeforeTheStart() = runTest {
        val model = MediaUploadModel(listOf(file("a.jpg"), file("b.jpg")), { _, _, _, _ -> media(1) }) {}

        model.remove(model.entries.first())

        assertEquals(listOf("b.jpg"), model.entries.map { it.file.name })
        assertTrue(model.hasPending)
    }

    // --- tag chips

    @Test
    fun chipsAreAddedNormalisedAndOnlyOnce() {
        val chips = TagChips()

        chips.text = "  Freiwillige   Feuerwehr "
        assertTrue(chips.add())
        assertTrue(chips.add("feuerwehr, Einsatz,,"))
        assertTrue(chips.add("FREIWILLIGE FEUERWEHR"))

        assertEquals(listOf("Freiwillige Feuerwehr", "feuerwehr", "Einsatz"), chips.tags)
        assertEquals("", chips.text)
    }

    @Test
    fun chipsCanBeRemoved() {
        val chips = TagChips(listOf("Sport", "Schule"))

        chips.remove("Sport")

        assertEquals(listOf("Schule"), chips.tags)
    }

    @Test
    fun invalidChipsAreRefused() {
        val chips = TagChips(maxTags = 2)
        chips.text = "x".repeat(41)

        assertFalse(chips.add())
        assertEquals(TagProblem.TOO_LONG, chips.problem)
        assertEquals("x".repeat(41), chips.text)
        assertFalse(chips.add("Feuer\u0007wehr"))
        assertEquals(TagProblem.CONTROL, chips.problem)
        assertTrue(chips.add("ä".repeat(40)))
        assertFalse(chips.add("a, b"))
        assertEquals(TagProblem.TOO_MANY, chips.problem)
        assertEquals(listOf("ä".repeat(40)), chips.tags)
    }

    @Test
    fun tagsAreNormalisedLikeTheServer() {
        assertEquals("Hochwasser 2026", normalizeTag(" Hochwasser \t 2026 "))
        assertEquals("", normalizeTag("   "))
    }

    // --- details in the media detail

    @Test
    fun detailsAreSavedAndShownAsStored() = runTest {
        val sent = mutableListOf<MediaDetailsRequest>()
        val model = MediaDetailsModel(media(20, tags = listOf("Hochwasser 2026"))) { request ->
            sent += request
            media(20, request.description, listOf("Feuerwehr", "Hochwasser 2026"))
        }
        assertFalse(model.dirty)

        model.edit()
        assertFalse(model.dirty)
        model.tags.text = "feuerwehr"
        assertTrue(model.dirty)
        model.description = "  "
        val saved = model.save()

        assertEquals(listOf(MediaDetailsRequest(null, listOf("Hochwasser 2026", "feuerwehr"))), sent)
        assertEquals(listOf("Feuerwehr", "Hochwasser 2026"), saved!!.tags)
        assertEquals(listOf("Feuerwehr", "Hochwasser 2026"), model.media.tags)
        assertFalse(model.editing)
        assertFalse(model.dirty)
    }

    @Test
    fun failedSaveKeepsTheInput() = runTest {
        val server = ApiClient(
            HttpClient(MockEngine {
                respond("""{ "errors": [{ "field": "tags[0]", "message": "must not contain a comma" }] }""",
                    HttpStatusCode.BadRequest, headersOf(HttpHeaders.ContentType, "application/json"))
            }),
            "https://news.example.org",
        ) { "token" }
        val model = MediaDetailsModel(media(20)) { server.setMediaDetails(20, it) }
        model.edit()
        model.description = "Foto: Anna"
        model.tags.add("Sport")

        assertNull(model.save())

        assertEquals(MediaDetailsError.Invalid("tags[0]"), model.error)
        assertTrue(model.editing)
        assertTrue(model.dirty)
        assertEquals("Foto: Anna", model.description)
        assertEquals(listOf("Sport"), model.tags.tags)
    }

    @Test
    fun saveErrorsInPlainWords() = runTest {
        for ((status, expected) in listOf(
            HttpStatusCode.Forbidden to MediaDetailsError.Forbidden,
            HttpStatusCode.ServiceUnavailable to MediaDetailsError.Unreachable,
            HttpStatusCode.NotFound to MediaDetailsError.Other("404 Not Found"),
        )) {
            val api = ApiClient(HttpClient(MockEngine { respond("", status) }), "https://news.example.org") { "token" }
            val model = MediaDetailsModel(media(20)) { api.setMediaDetails(20, it) }
            model.edit()

            model.save()

            assertEquals(expected, model.error)
        }
        val offline = MediaDetailsModel(media(20)) { error("offline") }
        offline.edit()
        offline.save()
        assertEquals(MediaDetailsError.Unreachable, offline.error)
    }

    @Test
    fun cancelDropsTheInput() {
        val model = MediaDetailsModel(media(20, "Foto: Anna")) { it -> media(20, it.description) }
        model.edit()
        assertEquals("Foto: Anna", model.description)
        model.description = "anders"
        assertTrue(model.dirty)

        model.cancel()

        assertFalse(model.dirty)
        assertEquals("Foto: Anna", model.media.description)
    }
}
