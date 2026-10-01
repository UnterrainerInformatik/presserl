package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.api.DeletionRequestDto
import info.unterrainer.presserl.admin.ui.account.DeletionStep
import info.unterrainer.presserl.admin.ui.account.MyAccountModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class MyAccountModelTest {

    private val requested = "2026-10-02T08:15:00Z"

    /** Requests the model sent, and the times it reported. */
    private val sent = mutableListOf<String>()
    private val changes = mutableListOf<String?>()
    private var refuse = false

    private fun TestScope.model(requestedAt: String? = null) = MyAccountModel(
        backgroundScope,
        requestedAt,
        request = {
            sent += "POST"
            if (refuse) throw IllegalStateException("unavailable")
            DeletionRequestDto(requested)
        },
        withdraw = {
            sent += "DELETE"
            DeletionRequestDto(null)
        },
        onChanged = { changes += it },
    )

    @Test
    fun requestAsksFirstThenShowsTheDate() = runTest {
        val model = model()
        model.ask()
        assertEquals(DeletionStep.REQUEST, model.state.value.pending)
        assertEquals(emptyList(), sent)

        model.confirm()
        runCurrent()

        assertEquals(listOf("POST"), sent)
        assertEquals(requested, model.state.value.requestedAt)
        assertEquals(listOf<String?>(requested), changes)
        assertNull(model.state.value.pending)
    }

    @Test
    fun withdrawOffersTheRequestAgain() = runTest {
        val model = model(requested)
        model.ask()
        assertEquals(DeletionStep.WITHDRAW, model.state.value.pending)

        model.confirm()
        runCurrent()

        assertEquals(listOf("DELETE"), sent)
        assertNull(model.state.value.requestedAt)
        assertEquals(listOf<String?>(null), changes)
    }

    @Test
    fun cancelSendsNothing() = runTest {
        val model = model()
        model.ask()

        model.cancel()
        model.confirm()
        runCurrent()

        assertEquals(emptyList(), sent)
        assertNull(model.state.value.pending)
        assertNull(model.state.value.requestedAt)
    }

    @Test
    fun failureShowsAnErrorAndKeepsTheState() = runTest {
        refuse = true
        val model = model()
        model.ask()

        model.confirm()
        runCurrent()

        assertEquals("unavailable", model.state.value.error)
        assertNull(model.state.value.requestedAt)
        assertFalse(model.state.value.busy)
        assertEquals(emptyList(), changes)
    }
}
