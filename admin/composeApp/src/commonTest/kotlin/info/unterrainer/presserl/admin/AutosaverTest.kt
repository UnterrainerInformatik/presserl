package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.api.ArticleContent
import info.unterrainer.presserl.admin.api.FieldErrorDto
import info.unterrainer.presserl.admin.ui.editor.Autosaver
import info.unterrainer.presserl.admin.ui.editor.FieldErrors
import info.unterrainer.presserl.admin.ui.editor.HeaderField
import info.unterrainer.presserl.admin.ui.editor.SaveState
import info.unterrainer.presserl.admin.ui.editor.fieldErrors
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AutosaverTest {

    private data class Put(val headline: String, val version: Long)

    private val puts = mutableListOf<Put>()

    /** Answers the next PUT; the default saves and returns the next version. */
    private var answer: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { saved(it) }

    private fun MockRequestHandleScope.saved(request: HttpRequestData): HttpResponseData {
        val version = puts.last().version + 1
        return respond(
            ARTICLE.replace("\"version\": 5", "\"version\": $version"),
            HttpStatusCode.OK,
            headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }

    private fun MockRequestHandleScope.refused(status: HttpStatusCode, body: String) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun TestScope.autosaver(): Autosaver {
        val engine = MockEngine(
            MockEngineConfig().apply {
                dispatcher = UnconfinedTestDispatcher(testScheduler)
                addHandler { request ->
                    val sent = Json.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject
                    puts += Put(sent.getValue("headline").jsonPrimitive.content, sent.getValue("version").jsonPrimitive.long)
                    answer(request)
                }
            },
        )
        val api = ApiClient(HttpClient(engine), "https://news.example.org") { "token" }
        return Autosaver(backgroundScope, ArticleContent(), version = 5, save = { content, version -> api.updateArticle(42, content, version) })
    }

    private fun content(headline: String) = ArticleContent(headline = headline)

    @Test
    fun aBurstOfEditsIsSavedOnce() = runTest {
        val saver = autosaver()
        saver.changed(content("T"))
        advanceTimeBy(500)
        saver.changed(content("Th"))
        advanceTimeBy(500)
        saver.changed(content("The"))
        assertEquals(SaveState.Pending, saver.state.value)

        advanceTimeBy(1_499)
        assertEquals(emptyList(), puts)
        advanceTimeBy(2)
        runCurrent()

        assertEquals(listOf(Put("The", 5)), puts)
        assertEquals(SaveState.Saved, saver.state.value)
    }

    @Test
    fun theReceivedVersionIsSentWithTheNextSave() = runTest {
        val saver = autosaver()
        saver.changed(content("A"))
        advanceTimeBy(2_000)
        saver.changed(content("B"))
        advanceTimeBy(2_000)

        assertEquals(listOf(Put("A", 5), Put("B", 6)), puts)
    }

    @Test
    fun editsDuringAnInFlightSaveAreSavedAfterwards() = runTest {
        val saver = autosaver()
        val gate = CompletableDeferred<Unit>()
        answer = { gate.await(); saved(it) }
        saver.changed(content("A"))
        advanceTimeBy(1_501)
        runCurrent()
        assertEquals(SaveState.Saving, saver.state.value)

        saver.changed(content("AB"))
        assertEquals(SaveState.Saving, saver.state.value)
        assertEquals(1, puts.size)
        gate.complete(Unit)
        runCurrent()
        assertEquals(SaveState.Pending, saver.state.value)
        advanceTimeBy(1_501)
        runCurrent()

        assertEquals(listOf(Put("A", 5), Put("AB", 6)), puts)
        assertEquals(SaveState.Saved, saver.state.value)
    }

    @Test
    fun flushSavesImmediately() = runTest {
        val saver = autosaver()
        saver.changed(content("Publish me"))

        assertTrue(saver.flush())
        assertEquals(listOf(Put("Publish me", 5)), puts)
        advanceTimeBy(10_000)
        assertEquals(1, puts.size)
    }

    @Test
    fun flushWithoutChangesSendsNothing() = runTest {
        val saver = autosaver()

        assertTrue(saver.flush())
        assertEquals(emptyList(), puts)
    }

    @Test
    fun refusedContentShowsFieldMessagesAndIsSentAgainOnlyAfterTheNextChange() = runTest {
        val saver = autosaver()
        answer = {
            refused(
                HttpStatusCode.BadRequest,
                """{ "errors": [ { "field": "headline", "message": "too long" },
                    { "field": "body.blocks[1].content[0].text", "message": "must not be empty" },
                    { "field": null, "message": "something else" } ] }""",
            )
        }
        saver.changed(content("Bad"))
        advanceTimeBy(1_501)
        runCurrent()

        assertEquals(
            SaveState.Invalid(FieldErrors(mapOf(HeaderField.HEADLINE to "too long"), mapOf(1 to "must not be empty"), listOf("something else"))),
            saver.state.value,
        )
        advanceTimeBy(60_000)
        assertFalse(saver.flush())
        assertEquals(1, puts.size)

        answer = { saved(it) }
        saver.changed(content("Good"))
        advanceTimeBy(1_501)
        runCurrent()
        assertEquals(listOf(Put("Bad", 5), Put("Good", 5)), puts)
        assertEquals(SaveState.Saved, saver.state.value)
    }

    @Test
    fun aConflictStopsSaving() = runTest {
        val saver = autosaver()
        answer = { refused(HttpStatusCode.Conflict, """{ "errors": [ { "message": "changed elsewhere" } ] }""") }
        saver.changed(content("Mine"))
        advanceTimeBy(1_501)
        runCurrent()
        assertEquals(SaveState.Conflict, saver.state.value)

        saver.changed(content("Mine, more"))
        advanceTimeBy(60_000)
        assertFalse(saver.flush())

        assertEquals(1, puts.size)
        assertEquals(SaveState.Conflict, saver.state.value)
    }

    @Test
    fun networkErrorsAreRetriedWithBackoff() = runTest {
        val saver = autosaver()
        var offline = 2
        answer = { if (offline-- > 0) throw IllegalStateException("network down") else saved(it) }
        saver.changed(content("Offline"))
        advanceTimeBy(1_501)
        runCurrent()
        assertEquals(SaveState.Failed(2), saver.state.value)

        advanceTimeBy(2_001)
        runCurrent()
        assertEquals(SaveState.Failed(4), saver.state.value)

        advanceTimeBy(4_001)
        runCurrent()
        assertEquals(SaveState.Saved, saver.state.value)
        assertEquals(List(3) { Put("Offline", 5) }, puts)
    }

    @Test
    fun serverErrorsAreRetried() = runTest {
        val saver = autosaver()
        answer = { answer = { saved(it) }; refused(HttpStatusCode.ServiceUnavailable, "") }
        saver.changed(content("Later"))
        advanceTimeBy(1_501)
        runCurrent()
        assertEquals(SaveState.Failed(2), saver.state.value)

        advanceTimeBy(2_001)
        runCurrent()
        assertEquals(SaveState.Saved, saver.state.value)
    }

    @Test
    fun fieldPathsAreMappedToFields() {
        assertEquals(
            FieldErrors(
                header = mapOf(HeaderField.LEAD to "too long", HeaderField.KICKER to "control character"),
                blocks = mapOf(0 to "unknown block type 'html'", 12 to "must contain at least one item"),
                general = listOf("body text too long", "blocks.x"),
                section = "you may not write in this section",
            ),
            fieldErrors(
                listOf(
                    FieldErrorDto("sectionId", "you may not write in this section"),
                    FieldErrorDto("lead", "too long"),
                    FieldErrorDto("kicker", "control character"),
                    FieldErrorDto("body.blocks[0].type", "unknown block type 'html'"),
                    FieldErrorDto("body.blocks[12].items", "must contain at least one item"),
                    FieldErrorDto("body", "body text too long"),
                    FieldErrorDto("blocks[3]", "blocks.x"),
                ),
            ),
        )
    }
}
