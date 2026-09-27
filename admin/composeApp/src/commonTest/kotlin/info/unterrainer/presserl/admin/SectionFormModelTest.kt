package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.api.SectionDto
import info.unterrainer.presserl.admin.api.SectionRequest
import info.unterrainer.presserl.admin.ui.section.SectionField
import info.unterrainer.presserl.admin.ui.section.SectionFormModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SectionFormModelTest {

    private val sent = mutableListOf<SectionRequest>()

    /** Answers the save request; the default saves the section. */
    private var save: suspend (SectionRequest) -> SectionDto = { request ->
        SectionDto(1, request.name, "slug", request.color ?: "red", 0, emptyList())
    }

    private fun TestScope.model(section: SectionDto? = null, defaultColor: String = "orange") =
        SectionFormModel(backgroundScope, section, defaultColor) { request -> sent += request; save(request) }

    @Test
    fun newSectionPreselectsTheDefaultColour() = runTest {
        val state = model().state.value
        assertEquals("", state.name)
        assertEquals("orange", state.color)
        assertFalse(state.canSave)
    }

    @Test
    fun editStartsWithTheSection() = runTest {
        val state = model(SPORT).state.value
        assertEquals("Sport", state.name)
        assertEquals("green", state.color)
        assertTrue(state.canSave)
    }

    @Test
    fun saveNeedsANonBlankName() = runTest {
        val model = model()
        model.name("  ")
        assertFalse(model.state.value.canSave)
        model.submit {}
        runCurrent()
        assertEquals(emptyList(), sent)
    }

    @Test
    fun submitSendsTrimmedNameAndChosenColour() = runTest {
        val model = model()
        model.name(" Kultur ")
        model.color("purple")
        var saved: SectionDto? = null
        model.submit { saved = it }
        runCurrent()

        assertEquals(listOf(SectionRequest("Kultur", "purple")), sent)
        assertEquals("Kultur", saved?.name)
        assertFalse(model.state.value.saving)
    }

    @Test
    fun duplicateNameIsShownAtTheFieldAndInputIsKept() = runTest {
        val api = api(HttpStatusCode.Conflict, """{ "errors": [{ "field": "name", "message": "is already used by another section" }] }""")
        save = { api.createSection(it) }
        val model = model()
        model.name("Sport")
        var saved: SectionDto? = null
        model.submit { saved = it }
        runCurrent()

        assertNull(saved)
        assertEquals(mapOf(SectionField.NAME to "is already used by another section"), model.state.value.errors)
        assertEquals("Sport", model.state.value.name)
        assertTrue(model.state.value.canSave)

        model.name("Sportnews")
        assertEquals(emptyMap(), model.state.value.errors)
    }

    @Test
    fun otherFailureIsAGeneralMessage() = runTest {
        val api = api(HttpStatusCode.Forbidden, "")
        save = { api.createSection(it) }
        val model = model()
        model.name("Sport")
        model.submit {}
        runCurrent()

        assertEquals(emptyMap(), model.state.value.errors)
        assertTrue(model.state.value.general!!.isNotEmpty())
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

    private companion object {
        val SPORT = SectionDto(1, "Sport", "sport", "green", 0, listOf("SECTION_EDITOR", "REPORTER"))
    }
}
