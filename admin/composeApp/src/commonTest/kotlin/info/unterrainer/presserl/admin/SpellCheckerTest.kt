package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.api.SpellMatchDto
import info.unterrainer.presserl.admin.ui.spell.SpellChecker
import info.unterrainer.presserl.admin.ui.spell.SpellFinding
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SpellCheckerTest {

    /** Texts sent to the fake backend, in order. */
    private val sent = mutableListOf<String>()

    /** `null` answers "unavailable"; a finding on every "gros" otherwise. */
    private var available = true

    /** Set to hold answers until completed. */
    private var gate: CompletableDeferred<Unit>? = null

    private suspend fun fakeCheck(text: String): List<SpellMatchDto>? {
        sent += text
        gate?.await()
        if (!available) return null
        return Regex("\\bgros\\b").findAll(text).map { SpellMatchDto(it.range.first, 4, "Tippfehler", listOf("groß")) }.toList()
    }

    private fun TestScope.checker(enabled: Boolean = true) =
        SpellChecker(backgroundScope, enabled, ::fakeCheck, clock = { testScheduler.currentTime })

    @Test
    fun aFieldIsCheckedOneSecondAfterTheLastChange() = runTest {
        val checker = checker()
        checker.changed("headline", "Der")
        advanceTimeBy(500)
        checker.changed("headline", "Der Hund ist gros")
        advanceTimeBy(999)
        runCurrent()
        assertEquals(emptyList(), sent)
        advanceTimeBy(2)
        runCurrent()

        assertEquals(listOf("Der Hund ist gros"), sent)
        assertEquals(listOf(SpellFinding(13, 17, "gros", "Tippfehler", listOf("groß"))), checker.findings("headline", "Der Hund ist gros"))
    }

    @Test
    fun checkNowDoesNotWait() = runTest {
        val checker = checker()
        checker.checkNow("lead", "gros")
        runCurrent()

        assertEquals(listOf("gros"), sent)
        assertEquals(1, checker.findings("lead", "gros").size)
    }

    @Test
    fun anAnswerForChangedTextIsDiscarded() = runTest {
        val checker = checker()
        gate = CompletableDeferred()
        checker.checkNow("headline", "gros")
        runCurrent()
        checker.changed("headline", "gross")
        gate!!.complete(Unit)
        runCurrent()

        assertEquals(emptyList(), checker.findings("headline", "gros"))
        assertEquals(emptyList(), checker.findings("headline", "gross"))
    }

    @Test
    fun findingsBelongToTheCheckedTextOnly() = runTest {
        val checker = checker()
        checker.checkNow("headline", "Der Hund ist gros")
        runCurrent()

        assertEquals(emptyList(), checker.findings("headline", "Der Hund ist gros!"))
        assertEquals(emptyList(), checker.findings("kicker", "Der Hund ist gros"))
    }

    @Test
    fun undoToACheckedTextIsAnsweredFromTheCache() = runTest {
        val checker = checker()
        checker.checkNow("headline", "gros")
        runCurrent()
        checker.changed("headline", "gross")
        advanceTimeBy(1_001)
        runCurrent()
        checker.changed("headline", "gros")
        advanceTimeBy(1_001)
        runCurrent()

        assertEquals(listOf("gros", "gross"), sent)
        assertEquals(1, checker.findings("headline", "gros").size)
    }

    @Test
    fun blankTextIsNotSent() = runTest {
        val checker = checker()
        checker.checkNow("kicker", "  ")
        runCurrent()

        assertEquals(emptyList(), sent)
    }

    @Test
    fun ignoringAWordHidesItInEveryField() = runTest {
        val checker = checker()
        checker.checkNow("headline", "gros und gros")
        checker.checkNow("lead", "sehr gros")
        runCurrent()
        checker.ignore("gros")

        assertEquals(emptyList(), checker.findings("headline", "gros und gros"))
        assertEquals(emptyList(), checker.findings("lead", "sehr gros"))
        checker.changed("lead", "gros gros")
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(emptyList(), checker.findings("lead", "gros gros"))
    }

    @Test
    fun atMostTwoRequestsRunAtATime() = runTest {
        val checker = checker()
        gate = CompletableDeferred()
        listOf("a", "b", "c", "d").forEach { checker.checkNow(it, "gros $it") }
        runCurrent()
        assertEquals(2, sent.size)
        gate!!.complete(Unit)
        runCurrent()

        assertEquals(4, sent.size)
    }

    @Test
    fun afterAFailureAFieldIsRetriedAtItsNextChangeOnceAMinuteHasPassed() = runTest {
        val checker = checker()
        available = false
        checker.checkNow("lead", "gros")
        runCurrent()
        assertTrue(checker.notice)

        available = true
        checker.changed("lead", "gros 1")
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(listOf("gros"), sent)

        advanceTimeBy(30_000)
        checker.changed("lead", "gros 2")
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(listOf("gros", "gros 2"), sent)
        assertEquals(1, checker.findings("lead", "gros 2").size)
        assertFalse(checker.notice)
    }

    @Test
    fun theNoticeIsShownOnlyOnce() = runTest {
        val checker = checker()
        available = false
        checker.checkNow("lead", "gros")
        runCurrent()
        available = true
        advanceTimeBy(61_000)
        checker.changed("lead", "gros!")
        advanceTimeBy(1_001)
        runCurrent()
        assertFalse(checker.notice)

        available = false
        checker.checkNow("headline", "gros")
        runCurrent()
        assertFalse(checker.notice)
    }

    @Test
    fun marksOfUnchangedTextStayWhenTheCheckFails() = runTest {
        val checker = checker()
        checker.checkNow("lead", "gros")
        runCurrent()
        available = false
        checker.changed("lead", "gros!")
        advanceTimeBy(1_001)
        runCurrent()

        assertEquals(1, checker.findings("lead", "gros").size)
    }

    @Test
    fun nothingIsSentWhenTheSpellCheckIsOff() = runTest {
        val checker = checker(enabled = false)
        checker.checkNow("headline", "gros")
        checker.changed("lead", "gros")
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(emptyList(), sent)
        assertEquals(emptyList(), checker.findings("headline", "gros"))
    }
}
