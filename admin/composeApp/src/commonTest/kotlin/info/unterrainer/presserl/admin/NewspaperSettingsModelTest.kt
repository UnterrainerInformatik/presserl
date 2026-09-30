package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.api.NewspaperDto
import info.unterrainer.presserl.admin.ui.newspaper.ARTICLE_CORRECTIONS
import info.unterrainer.presserl.admin.ui.newspaper.NewspaperSettingsModel
import info.unterrainer.presserl.admin.ui.newspaper.READER_TEXT_SIZE
import info.unterrainer.presserl.admin.ui.newspaper.SPELL_CHECK_HELP
import info.unterrainer.presserl.admin.ui.newspaper.SwitchView
import info.unterrainer.presserl.admin.ui.newspaper.correctionsView
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NewspaperSettingsModelTest {

    private val sent = mutableListOf<Map<String, JsonElement>>()

    /** The deployment's values, used when the newspaper has no override. */
    private var installationDefault = "m"
    private var installationHelp = "suggestions"
    private var installationCorrections = true
    private var override: String? = null
    private var helpOverride: String? = null
    private var correctionsOverride: Boolean? = null

    /** Answers the save request; the default applies it like the server. */
    private var save: suspend (Map<String, JsonElement>) -> NewspaperDto = { changes ->
        if (READER_TEXT_SIZE in changes) override = changes.getValue(READER_TEXT_SIZE).jsonPrimitive.contentOrNull
        if (SPELL_CHECK_HELP in changes) helpOverride = changes.getValue(SPELL_CHECK_HELP).jsonPrimitive.contentOrNull
        if (ARTICLE_CORRECTIONS in changes) correctionsOverride = changes.getValue(ARTICLE_CORRECTIONS).jsonPrimitive.booleanOrNull
        newspaper()
    }

    private fun newspaper() = NewspaperDto(
        "My Newspaper",
        "",
        "public",
        JsonObject(
            mapOf(
                READER_TEXT_SIZE to JsonPrimitive(override ?: installationDefault),
                SPELL_CHECK_HELP to JsonPrimitive(helpOverride ?: installationHelp),
                ARTICLE_CORRECTIONS to JsonPrimitive(correctionsOverride ?: installationCorrections),
            ),
        ),
        JsonObject(
            listOfNotNull(
                override?.let { READER_TEXT_SIZE to JsonPrimitive(it) },
                helpOverride?.let { SPELL_CHECK_HELP to JsonPrimitive(it) },
                correctionsOverride?.let { ARTICLE_CORRECTIONS to JsonPrimitive(it) },
            ).toMap(),
        ),
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
        assertNull(state.spellCheckHelp)
        assertEquals("suggestions", state.effectiveSpellCheckHelp)
    }

    @Test
    fun loadReadsBothOverrides() = runTest {
        override = "l"
        helpOverride = "messages"
        installationHelp = "marks"

        val state = model().state.value

        assertEquals("l", state.textSize)
        assertEquals("messages", state.spellCheckHelp)
        assertEquals("messages", state.effectiveSpellCheckHelp)
    }

    @Test
    fun publisherSwitchesCorrectionsOffAsJsonFalse() = runTest {
        val model = model()
        assertEquals(SwitchView(selected = null, effective = true, enabled = true), model.state.value.correctionsView(true))

        model.chooseSwitch(ARTICLE_CORRECTIONS, false)
        runCurrent()

        assertEquals(listOf(mapOf<String, JsonElement>(ARTICLE_CORRECTIONS to JsonPrimitive(false))), sent)
        assertEquals(SwitchView(selected = false, effective = false, enabled = true), model.state.value.correctionsView(true))
    }

    @Test
    fun correctionsBackToTheInstallationDefaultSendsNull() = runTest {
        correctionsOverride = false
        val model = model()
        assertEquals(false, model.state.value.corrections)

        model.chooseSwitch(ARTICLE_CORRECTIONS, null)
        runCurrent()

        assertEquals(listOf(mapOf<String, JsonElement>(ARTICLE_CORRECTIONS to JsonNull)), sent)
        assertNull(model.state.value.corrections)
        assertEquals(true, model.state.value.effectiveCorrections)
    }

    @Test
    fun editorInChiefSeesTheCorrectionsSwitchReadOnly() = runTest {
        installationCorrections = false
        val view = model().state.value.correctionsView(mayConfigure = false)

        assertEquals(SwitchView(selected = null, effective = false, enabled = false), view)
    }

    @Test
    fun choosingMarksSendsOnlyTheSpellCheckHelp() = runTest {
        override = "l"
        val model = model()

        model.choose(SPELL_CHECK_HELP, "marks")
        runCurrent()

        assertEquals(listOf(mapOf<String, JsonElement>(SPELL_CHECK_HELP to JsonPrimitive("marks"))), sent)
        assertEquals("marks", model.state.value.spellCheckHelp)
        assertEquals("marks", model.state.value.effectiveSpellCheckHelp)
        assertEquals("l", model.state.value.textSize)
    }

    @Test
    fun spellCheckHelpBackToTheInstallationDefaultSendsNull() = runTest {
        helpOverride = "marks"
        installationHelp = "messages"
        val model = model()

        model.choose(SPELL_CHECK_HELP, null)
        runCurrent()

        assertEquals(listOf(mapOf<String, JsonElement>(SPELL_CHECK_HELP to JsonNull)), sent)
        assertNull(model.state.value.spellCheckHelp)
        assertEquals("messages", model.state.value.effectiveSpellCheckHelp)
    }

    @Test
    fun refusedSpellCheckHelpRestoresThePreviousLevelAndKeepsTheTextSize() = runTest {
        override = "xl"
        helpOverride = "messages"
        val api = api(HttpStatusCode.Forbidden, "")
        save = { api.updateNewspaperSettings(it) }
        val model = model()

        model.choose(SPELL_CHECK_HELP, "marks")
        runCurrent()

        assertEquals("messages", model.state.value.spellCheckHelp)
        assertEquals("messages", model.state.value.effectiveSpellCheckHelp)
        assertEquals("xl", model.state.value.textSize)
        assertEquals("xl", model.state.value.effectiveTextSize)
        assertTrue(model.state.value.error!!.isNotEmpty())
        assertFalse(model.state.value.saving)
    }

    @Test
    fun choosingLSavesItAndShowsTheResponse() = runTest {
        val model = model()

        model.choose(READER_TEXT_SIZE, "l")
        runCurrent()

        assertEquals(listOf(mapOf<String, JsonElement>(READER_TEXT_SIZE to JsonPrimitive("l"))), sent)
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

        model.choose(READER_TEXT_SIZE, null)
        runCurrent()

        assertEquals(listOf(mapOf<String, JsonElement>(READER_TEXT_SIZE to JsonNull)), sent)
        assertNull(model.state.value.textSize)
        assertEquals("xl", model.state.value.effectiveTextSize)
    }

    @Test
    fun choiceIsShownWhileSavingAndFurtherChoicesWait() = runTest {
        val answer = CompletableDeferred<NewspaperDto>()
        save = { answer.await() }
        val model = model()

        model.choose(READER_TEXT_SIZE, "xl")
        runCurrent()
        model.choose(READER_TEXT_SIZE, "s")

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

        model.choose(READER_TEXT_SIZE, "l")
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

        model.choose(READER_TEXT_SIZE, "l")
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
