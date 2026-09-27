package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.api.AccountDto
import info.unterrainer.presserl.admin.api.MemberDto
import info.unterrainer.presserl.admin.api.MemberListDto
import info.unterrainer.presserl.admin.ui.section.SectionMembersModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SectionMembersModelTest {

    /** The server's members of the section, changed by assign and remove. */
    private val members = mutableMapOf("n1" to "SECTION_EDITOR")
    private var assignable = listOf("SECTION_EDITOR", "REPORTER")
    private var failAssign = false

    private val accounts = listOf(account("n1", "nogroups"), account("r1", "reader"), account("c1", "chief"))

    private fun TestScope.model() = SectionMembersModel(
        backgroundScope,
        loadMembers = {
            MemberListDto(
                assignable,
                members.map { (id, role) -> MemberDto(id, accounts.first { it.id == id }.username, "", "", role) },
            )
        },
        loadAccounts = { accounts },
        assign = { id, role ->
            if (failAssign) throw IllegalStateException("refused")
            members[id] = role
            MemberDto(id, "", "", "", role)
        },
        remove = { id -> members.remove(id) },
    )

    @Test
    fun candidatesAreAccountsWithoutARoleInTheSection() = runTest {
        val model = model()
        model.load()
        runCurrent()

        val state = model.state.value
        assertTrue(state.loaded)
        assertEquals(listOf("nogroups"), state.members.map { it.username })
        assertEquals(listOf("reader", "chief"), state.candidates.map { it.username })
    }

    @Test
    fun addChangeAndRemoveReload() = runTest {
        val model = model()
        model.load()
        runCurrent()

        model.assign("r1", "REPORTER")
        runCurrent()
        assertEquals(listOf("SECTION_EDITOR", "REPORTER"), model.state.value.members.map { it.role })
        assertEquals(listOf("chief"), model.state.value.candidates.map { it.username })

        model.assign("r1", "SECTION_EDITOR")
        runCurrent()
        assertEquals("SECTION_EDITOR", model.state.value.members.first { it.accountId == "r1" }.role)

        model.remove("n1")
        runCurrent()
        assertEquals(listOf("reader"), model.state.value.members.map { it.username })
        assertFalse(model.state.value.busy)
        assertNull(model.state.value.error)
    }

    @Test
    fun failureIsShownAndTheListReloaded() = runTest {
        val model = model()
        model.load()
        runCurrent()
        failAssign = true
        members["c1"] = "REPORTER"

        model.assign("r1", "REPORTER")
        runCurrent()

        assertEquals("refused", model.state.value.error)
        assertEquals(listOf("nogroups", "chief"), model.state.value.members.map { it.username })
        assertFalse(model.state.value.busy)
    }

    @Test
    fun onlyMembersWithAnAssignableRoleCanBeChanged() = runTest {
        assignable = listOf("REPORTER")
        members["r1"] = "REPORTER"
        val model = model()
        model.load()
        runCurrent()

        val state = model.state.value
        assertFalse(state.canChange(state.members.first { it.accountId == "n1" }))
        assertTrue(state.canChange(state.members.first { it.accountId == "r1" }))
    }

    private fun account(id: String, username: String) = AccountDto(id, username, "", "", emptyList(), true)
}
