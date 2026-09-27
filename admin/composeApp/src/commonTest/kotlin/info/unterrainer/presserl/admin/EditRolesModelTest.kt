package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.api.AccountDto
import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.api.EditRolesRequest
import info.unterrainer.presserl.admin.api.SectionDto
import info.unterrainer.presserl.admin.api.SectionRoleDto
import info.unterrainer.presserl.admin.ui.account.AccountField
import info.unterrainer.presserl.admin.ui.account.EditRolesModel
import info.unterrainer.presserl.admin.ui.account.RoleChoice
import info.unterrainer.presserl.admin.ui.account.SectionChoice
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EditRolesModelTest {

    private val sent = mutableListOf<Pair<String, EditRolesRequest>>()

    /** Answers `PUT /api/accounts/{id}/roles`; the default applies the roles. */
    private var save: suspend (String, EditRolesRequest) -> AccountDto = { _, request ->
        READER.copy(roles = request.roles, sectionRoles = request.sectionRoles)
    }

    private fun TestScope.model(
        account: AccountDto = READER,
        assignable: List<String> = PUBLISHER_ASSIGNABLE,
        sections: List<SectionDto> = listOf(SPORT, KULTUR),
    ) = EditRolesModel(backgroundScope, account, assignable, sections) { id, request ->
        sent += id to request
        save(id, request)
    }

    @Test
    fun currentRolesArePresetAndSaveIsNotPossibleYet() = runTest {
        val model = model(REPORTER_IN_SPORT)

        assertEquals(setOf("READER"), model.state.value.roles)
        assertEquals(mapOf(SPORT.id to "REPORTER"), model.state.value.sectionRoles)
        assertFalse(model.state.value.canSave)
    }

    @Test
    fun publisherMayChangeEveryNewspaperRoleAndSection() = runTest {
        val model = model()

        assertEquals(PUBLISHER_ASSIGNABLE.map { RoleChoice(it, editable = true) }, model.roleChoices)
        assertEquals(listOf(SectionChoice(SPORT, true), SectionChoice(KULTUR, true)), model.sectionChoices)
    }

    @Test
    fun editorInChiefSeesPublisherRoleOfTheAccountReadOnly() = runTest {
        val model = model(READER.copy(roles = listOf("PUBLISHER", "READER")), assignable = listOf("EDITOR_IN_CHIEF", "READER"))

        assertEquals(
            listOf(RoleChoice("PUBLISHER", false), RoleChoice("EDITOR_IN_CHIEF", true), RoleChoice("READER", true)),
            model.roleChoices,
        )
        model.role("PUBLISHER", false)
        assertEquals(setOf("PUBLISHER", "READER"), model.state.value.roles)
    }

    @Test
    fun sectionEditorSeesReaderReadOnlyAndOnlyTheirSection() = runTest {
        val model = model(REPORTER_IN_SPORT, assignable = emptyList(), sections = listOf(SPORT, KULTUR_READ_ONLY))

        assertEquals(listOf(RoleChoice("READER", false)), model.roleChoices)
        assertEquals(listOf(SectionChoice(SPORT, true)), model.sectionChoices)

        model.role("READER", false)
        assertEquals(setOf("READER"), model.state.value.roles)

        model.sectionRole(SPORT.id, null)
        assertTrue(model.state.value.canSave)
    }

    @Test
    fun sectionWithRoleOutsideTheScopeIsReadOnly() = runTest {
        val account = READER.copy(sectionRoles = listOf(SectionRoleDto(KULTUR_READ_ONLY.id, "SECTION_EDITOR")))
        val model = model(account, assignable = emptyList(), sections = listOf(SPORT, KULTUR_READ_ONLY))

        assertEquals(listOf(SectionChoice(SPORT, true), SectionChoice(KULTUR_READ_ONLY, false)), model.sectionChoices)
        model.sectionRole(KULTUR_READ_ONLY.id, null)
        assertEquals(mapOf(KULTUR_READ_ONLY.id to "SECTION_EDITOR"), model.state.value.sectionRoles)
    }

    @Test
    fun sectionEditorOfSportCannotChangeASportSectionEditor() = runTest {
        val sportForReporters = SPORT.copy(assignableRoles = listOf("REPORTER"))
        val account = READER.copy(sectionRoles = listOf(SectionRoleDto(SPORT.id, "SECTION_EDITOR")))

        val model = model(account, assignable = emptyList(), sections = listOf(sportForReporters))

        assertEquals(listOf(SectionChoice(sportForReporters, false)), model.sectionChoices)
    }

    @Test
    fun saveNeedsAChangeAndARole() = runTest {
        val model = model()
        model.role("EDITOR_IN_CHIEF", true)
        assertTrue(model.state.value.canSave)

        model.role("EDITOR_IN_CHIEF", false)
        assertFalse(model.state.value.canSave)

        model.role("READER", false)
        assertFalse(model.state.value.canSave)

        model.sectionRole(SPORT.id, "REPORTER")
        assertTrue(model.state.value.canSave)
    }

    @Test
    fun submitSendsTheCompleteRolesInServerOrder() = runTest {
        val model = model(REPORTER_IN_SPORT)
        model.role("EDITOR_IN_CHIEF", true)
        model.sectionRole(KULTUR.id, "SECTION_EDITOR")
        var saved: AccountDto? = null
        model.submit { saved = it }
        runCurrent()

        assertEquals(
            listOf(
                "id-r" to EditRolesRequest(
                    listOf("EDITOR_IN_CHIEF", "READER"),
                    listOf(SectionRoleDto(SPORT.id, "REPORTER"), SectionRoleDto(KULTUR.id, "SECTION_EDITOR")),
                ),
            ),
            sent,
        )
        assertEquals(listOf("EDITOR_IN_CHIEF", "READER"), saved?.roles)
        assertFalse(model.state.value.saving)
    }

    @Test
    fun readOnlyRolesAreSentUnchanged() = runTest {
        val model = model(REPORTER_IN_SPORT, assignable = emptyList(), sections = listOf(SPORT, KULTUR_READ_ONLY))
        model.sectionRole(SPORT.id, "SECTION_EDITOR")
        model.submit {}
        runCurrent()

        assertEquals(EditRolesRequest(listOf("READER"), listOf(SectionRoleDto(SPORT.id, "SECTION_EDITOR"))), sent.single().second)
    }

    @Test
    fun refusalIsShownAtTheFieldKeepingTheInput() = runTest {
        val api = api(HttpStatusCode.Forbidden, """{ "errors": [{ "field": "roles", "message": "you may not add or remove [PUBLISHER]" }] }""")
        save = { id, request -> api.editRoles(id, request) }
        val model = model()
        model.role("PUBLISHER", true)
        var saved = false
        model.submit { saved = true }
        runCurrent()

        assertFalse(saved)
        assertEquals(mapOf(AccountField.ROLES to "you may not add or remove [PUBLISHER]"), model.state.value.errors)
        assertEquals(setOf("PUBLISHER", "READER"), model.state.value.roles)
        assertTrue(model.state.value.canSave)

        model.role("PUBLISHER", false)
        assertEquals(emptyMap(), model.state.value.errors)
    }

    @Test
    fun refusalWithoutFieldIsTheGeneralMessage() = runTest {
        val api = api(HttpStatusCode.Forbidden, """{ "errors": [{ "field": null, "message": "you may not edit the roles of account 'reader'" }] }""")
        save = { id, request -> api.editRoles(id, request) }
        val model = model()
        model.role("EDITOR_IN_CHIEF", true)
        model.submit {}
        runCurrent()

        assertEquals("you may not edit the roles of account 'reader'", model.state.value.general)
        assertEquals(emptyMap(), model.state.value.errors)
    }

    @Test
    fun serverFailureIsAGeneralMessage() = runTest {
        val api = api(HttpStatusCode.ServiceUnavailable, """{ "errors": [{ "message": "the account service is unavailable" }] }""")
        save = { id, request -> api.editRoles(id, request) }
        val model = model()
        model.role("EDITOR_IN_CHIEF", true)
        model.submit {}
        runCurrent()

        assertNotNull(model.state.value.general)
        assertEquals(emptyMap(), model.state.value.errors)
        assertTrue(model.state.value.canSave)
    }

    @Test
    fun nothingIsSentWithoutAChange() = runTest {
        val model = model()
        model.submit {}
        runCurrent()

        assertEquals(emptyList(), sent)
        assertNull(model.state.value.general)
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
        val PUBLISHER_ASSIGNABLE = listOf("PUBLISHER", "EDITOR_IN_CHIEF", "READER")
        val SPORT = SectionDto(1, "Sport", "sport", "green", 0, listOf("SECTION_EDITOR", "REPORTER"))
        val KULTUR = SectionDto(2, "Kultur", "kultur", "red", 1, listOf("SECTION_EDITOR", "REPORTER"))
        val KULTUR_READ_ONLY = KULTUR.copy(assignableRoles = emptyList())
        val READER = AccountDto("id-r", "reader", "Reader", "", listOf("READER"), true)
        val REPORTER_IN_SPORT = READER.copy(sectionRoles = listOf(SectionRoleDto(SPORT.id, "REPORTER")))
    }
}
