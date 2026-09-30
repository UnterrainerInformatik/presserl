package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.auth.SlipAutofill
import info.unterrainer.presserl.admin.auth.SlipAutofill.Action
import info.unterrainer.presserl.admin.ui.account.SlipCredentials
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SlipAutofillTest {

    private val anna = SlipCredentials("https://zeitung.example.org", "anna", "tiger-wolke-apfel-leiter")

    @Test
    fun submitsOnceThenReportsRejection() {
        val autofill = SlipAutofill(anna)

        val first = autofill.onPasswordPage(submittedFromThisPage = false)
        assertIs<Action.Submit>(first)
        assertTrue("\"tiger-wolke-apfel-leiter\"" in first.script)
        assertTrue("\"anna\"" in first.script)

        // the load event of the submitted document repeats: no second submission, no rejection
        assertEquals(Action.None, autofill.onPasswordPage(submittedFromThisPage = true))
        // the next password page is the issuer's answer
        assertEquals(Action.Rejected, autofill.onPasswordPage(submittedFromThisPage = false))
        // from now on the user types
        assertEquals(Action.None, autofill.onPasswordPage(submittedFromThisPage = false))
    }

    @Test
    fun withoutCredentialsTheUserTypes() {
        assertEquals(Action.None, SlipAutofill(null).onPasswordPage(submittedFromThisPage = false))
    }

    @Test
    fun valuesAreJsonStringLiterals() {
        val script = SlipAutofill.fillScript("anna", "a'b\"c</script>\\")

        assertTrue("\"a'b\\\"c</script>\\\\\"" in script, script)
        assertFalse("a'b\"c" in script)
    }

    @Test
    fun submitActionHidesTheScript() {
        assertFalse(Action.Submit(SlipAutofill.fillScript("anna", "tiger")).toString().contains("tiger"))
    }
}
