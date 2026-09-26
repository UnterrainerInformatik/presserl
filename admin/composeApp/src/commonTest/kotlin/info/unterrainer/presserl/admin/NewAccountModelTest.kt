package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.api.AccountDto
import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.api.CreateAccountRequest
import info.unterrainer.presserl.admin.api.CreatedAccountDto
import info.unterrainer.presserl.admin.ui.account.AccountField
import info.unterrainer.presserl.admin.ui.account.NewAccountModel
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NewAccountModelTest {

    private val suggested = mutableListOf<String>()
    private val sent = mutableListOf<CreateAccountRequest>()

    /** Answers a suggestion request; the default lower-cases the first name. */
    private var suggest: suspend (String) -> String = { it.lowercase() }

    /** Answers `POST /api/accounts`; the default creates the account. */
    private var create: suspend (CreateAccountRequest) -> CreatedAccountDto = { request ->
        CreatedAccountDto(AccountDto("id", request.username, request.firstName, request.lastName, request.roles, true), "tiger-wolke-apfel-leiter")
    }

    private fun TestScope.model(assignable: List<String> = listOf("PUBLISHER", "EDITOR_IN_CHIEF", "READER")) =
        NewAccountModel(
            backgroundScope,
            assignable,
            suggest = { firstName -> suggested += firstName; suggest(firstName) },
            create = { request -> sent += request; create(request) },
        )

    @Test
    fun usernameFollowsTheFirstNameAfterADebounce() = runTest {
        val model = model()
        model.firstName("A")
        advanceTimeBy(200)
        model.firstName("An")
        advanceTimeBy(200)
        model.firstName("Anna")
        advanceTimeBy(299)
        assertEquals(emptyList(), suggested)

        advanceTimeBy(2)
        runCurrent()

        assertEquals(listOf("Anna"), suggested)
        assertEquals("anna", model.state.value.username)
    }

    @Test
    fun serverSuggestionIsTaken() = runTest {
        suggest = { "anna-2" }
        val model = model()
        model.firstName("Anna")
        advanceTimeBy(301)
        runCurrent()

        assertEquals("anna-2", model.state.value.username)
    }

    @Test
    fun outdatedSuggestionIsIgnored() = runTest {
        val slow = CompletableDeferred<String>()
        suggest = { if (it == "Anna") slow.await() else it.lowercase() }
        val model = model()
        model.firstName("Anna")
        advanceTimeBy(301)
        runCurrent()
        model.firstName("Berta")
        slow.complete("anna")
        runCurrent()
        assertEquals("", model.state.value.username)

        advanceTimeBy(301)
        runCurrent()
        assertEquals("berta", model.state.value.username)
    }

    @Test
    fun editedUsernameIsKept() = runTest {
        val model = model()
        model.firstName("Anna")
        advanceTimeBy(301)
        runCurrent()
        model.username("annika")
        model.firstName("Anneliese")
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals("annika", model.state.value.username)
        assertTrue(model.state.value.usernameEdited)
        assertEquals(listOf("Anna"), suggested)
    }

    @Test
    fun clearedUsernameFollowsTheFirstNameAgain() = runTest {
        val model = model()
        model.firstName("Anna")
        model.username("annika")
        model.username("")
        advanceTimeBy(301)
        runCurrent()

        assertEquals("anna", model.state.value.username)
        assertFalse(model.state.value.usernameEdited)
    }

    @Test
    fun failedSuggestionLeavesTheUsernameEmpty() = runTest {
        suggest = { throw IllegalStateException("offline") }
        val model = model()
        model.firstName("Anna")
        advanceTimeBy(301)
        runCurrent()

        assertEquals("", model.state.value.username)
        assertNull(model.state.value.general)
    }

    @Test
    fun createNeedsFirstNameUsernameAndRole() = runTest {
        val model = model()
        assertFalse(model.state.value.canCreate)
        model.firstName("Lena")
        advanceTimeBy(301)
        runCurrent()
        assertFalse(model.state.value.canCreate)
        model.role("EDITOR_IN_CHIEF", true)
        assertTrue(model.state.value.canCreate)
        model.username("")
        model.firstName(" ")
        advanceTimeBy(301)
        runCurrent()
        assertFalse(model.state.value.canCreate)
    }

    @Test
    fun onlyAssignableRolesCanBeChosen() = runTest {
        val model = model(listOf("EDITOR_IN_CHIEF", "READER"))
        model.role("PUBLISHER", true)
        model.role("READER", true)

        assertEquals(setOf("READER"), model.state.value.roles)
    }

    @Test
    fun submitSendsTrimmedNamesAndRolesInAssignableOrder() = runTest {
        val model = model()
        model.firstName(" Lena ")
        model.lastName(" Berger ")
        model.username("lena")
        model.role("READER", true)
        model.role("PUBLISHER", true)
        var created: CreatedAccountDto? = null
        model.submit { created = it }
        runCurrent()

        assertEquals(listOf(CreateAccountRequest("Lena", "Berger", "lena", listOf("PUBLISHER", "READER"))), sent)
        assertEquals("tiger-wolke-apfel-leiter", created?.password)
        assertFalse(model.state.value.creating)
    }

    @Test
    fun refusalIsShownAtTheFieldAndInputIsKept() = runTest {
        val api = api(HttpStatusCode.Conflict, """{ "errors": [{ "field": "username", "message": "is already taken" }] }""")
        create = { api.createAccount(it) }
        val model = filled(model())
        var created: CreatedAccountDto? = null
        model.submit { created = it }
        runCurrent()

        val state = model.state.value
        assertNull(created)
        assertEquals(mapOf(AccountField.USERNAME to "is already taken"), state.errors)
        assertNull(state.general)
        assertEquals("Lena", state.firstName)
        assertEquals("lena", state.username)
        assertEquals(setOf("EDITOR_IN_CHIEF"), state.roles)
        assertTrue(state.canCreate)

        model.username("lena-b")
        assertEquals(emptyMap(), model.state.value.errors)
    }

    @Test
    fun severalFieldErrorsAndGeneralMessages() = runTest {
        val api = api(
            HttpStatusCode.BadRequest,
            """{ "errors": [{ "field": "username", "message": "bad" }, { "field": "roles", "message": "empty" },
                { "field": null, "message": "request body must be a JSON object" }] }""",
        )
        create = { api.createAccount(it) }
        val model = filled(model())
        model.submit {}
        runCurrent()

        assertEquals(mapOf(AccountField.USERNAME to "bad", AccountField.ROLES to "empty"), model.state.value.errors)
        assertEquals("request body must be a JSON object", model.state.value.general)
    }

    @Test
    fun unavailableServerIsAGeneralMessage() = runTest {
        val api = api(HttpStatusCode.ServiceUnavailable, """{ "errors": [{ "field": null, "message": "unavailable" }] }""")
        create = { api.createAccount(it) }
        val model = filled(model())
        model.submit {}
        runCurrent()

        assertEquals(emptyMap(), model.state.value.errors)
        assertTrue(model.state.value.general!!.isNotEmpty())
        assertEquals("Lena", model.state.value.firstName)
    }

    private fun TestScope.filled(model: NewAccountModel): NewAccountModel {
        model.firstName("Lena")
        model.username("lena")
        model.role("EDITOR_IN_CHIEF", true)
        runCurrent()
        return model
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
