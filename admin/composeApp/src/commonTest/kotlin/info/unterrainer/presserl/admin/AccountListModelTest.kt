package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.api.AccountDto
import info.unterrainer.presserl.admin.api.CreatedAccountDto
import info.unterrainer.presserl.admin.api.TrustScopeDto
import info.unterrainer.presserl.admin.ui.account.AccountAction
import info.unterrainer.presserl.admin.ui.account.AccountListModel
import info.unterrainer.presserl.admin.ui.account.PendingAction
import info.unterrainer.presserl.admin.ui.account.PendingTrust
import info.unterrainer.presserl.admin.ui.account.actions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class AccountListModelTest {

    private val reader = account("r1", "reader", enabled = true, "RESET_PASSWORD", "LOCK")
    private val chief = account("c1", "chief", enabled = true, "RESET_PASSWORD", "LOCK")

    /** Requests the model sent, as "action id". */
    private val sent = mutableListOf<String>()
    private var refuse = false

    private fun TestScope.model() = AccountListModel(
        backgroundScope,
        listOf(chief, reader),
        resetPassword = { id ->
            sent += "reset $id"
            if (refuse) throw IllegalStateException("refused")
            CreatedAccountDto(reader, "tiger-wolke-apfel-leiter")
        },
        lock = { id ->
            sent += "lock $id"
            if (refuse) throw IllegalStateException("you may not lock account 'reader'")
            account(id, "reader", enabled = false, "RESET_PASSWORD", "UNLOCK")
        },
        unlock = { id ->
            sent += "unlock $id"
            account(id, "reader", enabled = true, "RESET_PASSWORD", "LOCK")
        },
        setTrust = { id, level, sectionId, trusted ->
            sent += "trust $id $level $sectionId $trusted"
            if (refuse) throw IllegalStateException("you may not change trust of account 'chief' at PUBLISHER")
            chief.copy(trusts = if (trusted) listOf(TrustScopeDto(level, sectionId)) else emptyList())
        },
        delete = { id ->
            sent += "delete $id"
            if (refuse) throw IllegalStateException("you may not delete account 'reader'")
        },
    )

    @Test
    fun actionsFollowAllowedActions() {
        assertEquals(listOf(AccountAction.RESET_PASSWORD, AccountAction.LOCK), reader.actions())
        assertEquals(emptyList(), account("x", "x", enabled = true).actions())
        assertEquals(listOf(AccountAction.UNLOCK, AccountAction.DELETE), account("x", "x", enabled = false, "UNLOCK", "DELETE").actions())
        assertEquals(listOf(AccountAction.DELETE), account("x", "x", enabled = true, "DELETE", "ARCHIVE").actions())
    }

    @Test
    fun pendingDeletionRequestsComeFirstOldestFirst() = runTest {
        val late = account("l1", "late", enabled = true).copy(deletionRequestedAt = "2026-10-02T09:00:00Z")
        val early = account("e1", "early", enabled = true).copy(deletionRequestedAt = "2026-10-02T08:15:00.500Z")
        val model = AccountListModel(
            backgroundScope,
            listOf(chief, late, reader, early),
            resetPassword = { error("unused") },
            lock = { error("unused") },
            unlock = { error("unused") },
            setTrust = { _, _, _, _ -> error("unused") },
            delete = { error("unused") },
        )

        assertEquals(listOf(early, late, chief, reader), model.state.value.accounts)
    }

    @Test
    fun confirmedDeletionRemovesTheRow() = runTest {
        val model = model()
        model.request(reader, AccountAction.DELETE)
        assertEquals(AccountAction.DELETE, assertIs<PendingAction>(model.state.value.pending).action)

        model.confirm()
        runCurrent()

        assertEquals(listOf("delete r1"), sent)
        assertEquals(listOf(chief), model.state.value.accounts)
        assertFalse(model.state.value.busy)
    }

    @Test
    fun cancelledDeletionSendsNothing() = runTest {
        val model = model()
        model.request(reader, AccountAction.DELETE)

        model.cancel()
        runCurrent()

        assertEquals(emptyList(), sent)
        assertEquals(listOf(chief, reader), model.state.value.accounts)
    }

    @Test
    fun refusedDeletionShowsAnErrorAndKeepsTheList() = runTest {
        refuse = true
        val model = model()
        model.request(reader, AccountAction.DELETE)

        model.confirm()
        runCurrent()

        assertEquals("you may not delete account 'reader'", model.state.value.error)
        assertEquals(listOf(chief, reader), model.state.value.accounts)
        assertFalse(model.state.value.busy)
    }

    @Test
    fun cancelSendsNothing() = runTest {
        val model = model()
        model.request(reader, AccountAction.LOCK)
        assertEquals(AccountAction.LOCK, assertIs<PendingAction>(model.state.value.pending).action)

        model.cancel()
        runCurrent()

        assertNull(model.state.value.pending)
        assertEquals(emptyList(), sent)
        assertEquals(listOf(chief, reader), model.state.value.accounts)
    }

    @Test
    fun confirmedLockReplacesTheRow() = runTest {
        val model = model()
        model.request(reader, AccountAction.LOCK)
        model.confirm()
        runCurrent()

        assertEquals(listOf("lock r1"), sent)
        val locked = model.state.value.accounts.first { it.id == "r1" }
        assertFalse(locked.enabled)
        assertEquals(listOf(AccountAction.RESET_PASSWORD, AccountAction.UNLOCK), locked.actions())
        assertEquals(chief, model.state.value.accounts.first())
        assertFalse(model.state.value.busy)
        assertNull(model.state.value.pending)

        model.request(locked, AccountAction.UNLOCK)
        model.confirm()
        runCurrent()
        assertEquals(listOf("lock r1", "unlock r1"), sent)
        assertEquals(reader, model.state.value.accounts.first { it.id == "r1" })
    }

    @Test
    fun refusedActionShowsAnErrorAndKeepsTheList() = runTest {
        refuse = true
        val model = model()
        model.request(reader, AccountAction.LOCK)
        model.confirm()
        runCurrent()

        assertEquals("you may not lock account 'reader'", model.state.value.error)
        assertEquals(listOf(chief, reader), model.state.value.accounts)
        assertFalse(model.state.value.busy)
        assertNull(model.state.value.reset)
    }

    @Test
    fun resetYieldsTheSlipData() = runTest {
        val model = model()
        model.request(reader, AccountAction.RESET_PASSWORD)
        model.confirm()
        runCurrent()

        val reset = assertNotNull(model.state.value.reset)
        assertEquals("reader", reset.account.username)
        assertEquals("tiger-wolke-apfel-leiter", reset.password)
        assertEquals(listOf("reset r1"), sent)

        model.resetShown()
        assertNull(model.state.value.reset)
    }

    @Test
    fun confirmWithoutPendingSendsNothing() = runTest {
        val model = model()
        model.confirm()
        runCurrent()
        assertEquals(emptyList(), sent)
    }

    // --- trust

    private val publisherTrust = TrustScopeDto("PUBLISHER", null)

    @Test
    fun turningTrustOnAsksFirstAndReplacesTheRow() = runTest {
        val model = model()
        model.requestTrust(chief, publisherTrust, on = true)
        runCurrent()
        assertEquals(PendingTrust(chief, publisherTrust), model.state.value.pending)
        assertEquals(emptyList(), sent)

        model.confirm()
        runCurrent()
        assertEquals(listOf("trust c1 PUBLISHER null true"), sent)
        assertEquals(listOf(publisherTrust), model.state.value.accounts.first { it.id == "c1" }.trusts)
        assertNull(model.state.value.pending)
        assertFalse(model.state.value.busy)
    }

    @Test
    fun cancellingTrustSendsNothing() = runTest {
        val model = model()
        model.requestTrust(chief, publisherTrust, on = true)
        model.cancel()
        runCurrent()

        assertNull(model.state.value.pending)
        assertEquals(emptyList(), sent)
        assertEquals(listOf(chief, reader), model.state.value.accounts)
    }

    @Test
    fun turningTrustOffSendsAtOnce() = runTest {
        val model = model()
        val trusted = chief.copy(trusts = listOf(TrustScopeDto("SECTION_EDITOR", 3)))
        model.requestTrust(trusted, TrustScopeDto("SECTION_EDITOR", 3), on = false)
        runCurrent()

        assertNull(model.state.value.pending)
        assertEquals(listOf("trust c1 SECTION_EDITOR 3 false"), sent)
        assertEquals(emptyList(), model.state.value.accounts.first { it.id == "c1" }.trusts)
    }

    @Test
    fun refusedTrustShowsAnErrorAndKeepsTheSwitch() = runTest {
        refuse = true
        val model = model()
        model.requestTrust(chief, publisherTrust, on = true)
        model.confirm()
        runCurrent()

        assertEquals("you may not change trust of account 'chief' at PUBLISHER", model.state.value.error)
        assertEquals(listOf(chief, reader), model.state.value.accounts)
        assertFalse(model.state.value.busy)
    }

    @Test
    fun unknownTrustLevelsAreIgnored() = runTest {
        val model = model()
        model.requestTrust(chief, TrustScopeDto("OMBUDSMAN", null), on = true)
        model.requestTrust(chief, TrustScopeDto("OMBUDSMAN", null), on = false)
        runCurrent()

        assertNull(model.state.value.pending)
        assertEquals(emptyList(), sent)
    }

    private fun account(id: String, username: String, enabled: Boolean, vararg actions: String) =
        AccountDto(id, username, "", "", listOf("READER"), enabled, allowedActions = actions.toList())
}
