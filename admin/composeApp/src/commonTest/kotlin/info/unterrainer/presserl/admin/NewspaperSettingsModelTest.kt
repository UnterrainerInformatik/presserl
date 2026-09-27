package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.api.NewspaperDto
import info.unterrainer.presserl.admin.ui.newspaper.NewspaperSettingsModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NewspaperSettingsModelTest {

    private val sent = mutableListOf<Map<String, String?>>()

    /** The deployment's text size, used when the newspaper has no override. */
    private var installationDefault = "m"
    private var override: String? = null

    /** Answers the save request; the default applies it like the server. */
    private var save: suspend (Map<String, String?>) -> NewspaperDto = { changes ->
        override = changes["reader.text-size"]
        newspaper()
    }

    private fun newspaper() = NewspaperDto(
        "My Newspaper",
        "",
        "public",
        JsonObject(mapOf("reader.text-size" to JsonPrimitive(override ?: installationDefault))),
        JsonObject(override?.let { mapOf("reader.text-size" to JsonPrimitive(it)) } ?: emptyMap()),
    )

    private fun TestScope.model() =
        NewspaperSettingsModel(backgroundScope, load = { newspaper() }) { changes -> sent += changes; save(changes) }
            .also { it.load(); runCurrent() }

    @Test
    fun freshInstallationSelectsTheInstallationDefault() = runTest {
        val state = model().state.value

        assertFalse(state.loading)
        assertNull(state.textSize)
        assertEquals("m", state.effectiveTextSize)
    }

    @Test
    fun choosingLSavesItAndShowsTheResponse() = runTest {
        val model = model()

        model.textSize("l")
        runCurrent()

        assertEquals(listOf(mapOf<String, String?>("reader.text-size" to "l")), sent)
        assertEquals("l", model.state.value.textSize)
        assertEquals("l", model.state.value.effectiveTextSize)
        assertFalse(model.state.value.saving)
    }

    @Test
    fun backToTheInstallationDefaultSendsNull() = runTest {
        override = "l"
        installationDefault = "xl"
        val model = model()
        assertEquals("l", model.state.value.textSize)

        model.textSize(null)
        runCurrent()

        assertEquals(listOf(mapOf<String, String?>("reader.text-size" to null)), sent)
        assertNull(model.state.value.textSize)
        assertEquals("xl", model.state.value.effectiveTextSize)
    }

    @Test
    fun choiceIsShownWhileSavingAndFurtherChoicesWait() = runTest {
        val answer = CompletableDeferred<NewspaperDto>()
        save = { answer.await() }
        val model = model()

        model.textSize("xl")
        runCurrent()
        model.textSize("s")

        assertEquals("xl", model.state.value.textSize)
        assertTrue(model.state.value.saving)
        assertEquals(1, sent.size)
        override = "xl"
        answer.complete(newspaper())
        runCurrent()
        assertFalse(model.state.value.saving)
    }

    @Test
    fun refusedSaveKeepsThePreviousChoiceAndShowsAMessage() = runTest {
        val api = api(HttpStatusCode.Forbidden, "")
        save = { api.updateNewspaperSettings(it) }
        val model = model()

        model.textSize("l")
        runCurrent()

        assertNull(model.state.value.textSize)
        assertEquals("m", model.state.value.effectiveTextSize)
        assertTrue(model.state.value.error!!.isNotEmpty())
        assertFalse(model.state.value.saving)
    }

    @Test
    fun invalidValueShowsTheServersMessage() = runTest {
        val api = api(HttpStatusCode.BadRequest,
            """{ "errors": [{ "field": "reader.text-size", "message": "must be one of s, m, l, xl" }] }""")
        save = { api.updateNewspaperSettings(it) }
        val model = model()

        model.textSize("l")
        runCurrent()

        assertEquals("must be one of s, m, l, xl", model.state.value.error)
        assertNull(model.state.value.textSize)
    }

    private fun TestScope.api(status: HttpStatusCode, body: String) = ApiClient(
        HttpClient(
            MockEngine(
                MockEngineConfig().apply {
                    dispatcher = UnconfinedTestDispatcher(testScheduler)
                    addHandler { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) }
                },
            ),
        ),
        "https://news.example.org",
    ) { "token" }
}
