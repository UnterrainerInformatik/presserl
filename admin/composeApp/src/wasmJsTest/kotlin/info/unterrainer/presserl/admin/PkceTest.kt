package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.auth.Pkce
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PkceTest {

    @Test
    fun challengeMatchesRfc7636TestVector() = runTest {
        // RFC 7636, Appendix B
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun verifierIsUnreservedAndLongEnough() {
        val verifier = Pkce.newVerifier()

        assertEquals(43, verifier.length)
        assertTrue(verifier.all { it.isLetterOrDigit() || it in "-._~" })
        assertNotEquals(verifier, Pkce.newVerifier())
    }

    @Test
    fun stateMustMatchTheSentOne() {
        val state = Pkce.newState()

        assertTrue(Pkce.stateMatches(state, state))
        assertFalse(Pkce.stateMatches(state, "forged"))
        assertFalse(Pkce.stateMatches(state, null))
        assertFalse(Pkce.stateMatches(null, null))
        assertFalse(Pkce.stateMatches("", ""))
    }
}
