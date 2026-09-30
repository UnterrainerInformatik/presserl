package info.unterrainer.presserl.admin.auth

import info.unterrainer.presserl.admin.ui.account.SlipCredentials
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/**
 * Fills the issuer's login form with a slip's credentials exactly once (design D4). Keycloak's default theme names the
 * fields `#username` and `#password` and the button `#kc-login`; a theme without them is simply left for typing.
 * A password page loaded after the submission means the credentials were rejected: they are never submitted again.
 */
class SlipAutofill(private var credentials: SlipCredentials?) {

    sealed interface Action {
        /** Run [script] in the page: it fills in the form and submits it. */
        data class Submit(val script: String) : Action {
            override fun toString(): String = "Submit"
        }

        /** The issuer refused the submitted credentials. */
        data object Rejected : Action

        data object None : Action
    }

    private var submitted = false

    /**
     * What to do on a freshly loaded issuer page that has a password field; [submittedFromThisPage] is `true` when the
     * page is the document the form was submitted from (its load finished once more).
     */
    fun onPasswordPage(submittedFromThisPage: Boolean): Action {
        if (submittedFromThisPage) return Action.None
        if (submitted) {
            submitted = false
            credentials = null
            return Action.Rejected
        }
        val current = credentials ?: return Action.None
        submitted = true
        return Action.Submit(fillScript(current.username, current.passPhrase))
    }

    companion object {
        /** Set in the page by the fill script, so a repeated load event of the same document is recognized. */
        const val MARKER = "__presserlSubmitted"

        /** Whether the page has a password field and was the one submitted from, as `[hasPassword, submitted]`. */
        const val PROBE_SCRIPT = "JSON.stringify([!!document.getElementById('password'), !!window.$MARKER])"

        /** The values go in as JSON string literals, never as raw text in the script. */
        fun fillScript(username: String, passPhrase: String): String {
            val user = Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(username))
            val pass = Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(passPhrase))
            return """
                (function () {
                  var username = document.getElementById('username');
                  var password = document.getElementById('password');
                  var submit = document.getElementById('kc-login');
                  if (!username || !password) return false;
                  window.$MARKER = true;
                  username.value = $user;
                  password.value = $pass;
                  username.dispatchEvent(new Event('input', { bubbles: true }));
                  password.dispatchEvent(new Event('input', { bubbles: true }));
                  if (submit) submit.click(); else password.form.submit();
                  return true;
                })()
            """.trimIndent()
        }
    }
}
