package info.unterrainer.presserl.admin

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.auth.BrowserAuthClient
import info.unterrainer.presserl.admin.ui.App
import info.unterrainer.presserl.admin.ui.BrowserSlipPrinter
import info.unterrainer.presserl.admin.ui.installFocusGuard
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import kotlinx.browser.document
import kotlinx.browser.window

/**
 * The backend serves the app and the reader from the same origin; only the Gradle dev server on
 * :8081 talks to `quarkus dev` on :8080.
 */
private fun apiBaseUrl(): String =
    if (window.location.port == "8081") "http://localhost:8080" else window.location.origin

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val http = HttpClient(Js)
    val baseUrl = apiBaseUrl()
    lateinit var auth: BrowserAuthClient
    val api = ApiClient(http, baseUrl) { auth.accessToken() }
    auth = BrowserAuthClient(http) { api.clientConfig().oidc }
    ComposeViewport(document.body!!) {
        // startup.js treats errors before this signal as start-up failures
        LaunchedEffect(Unit) {
            withFrameNanos {}
            document.documentElement?.setAttribute("data-presserl-started", "true")
        }
        App(auth, api, siteUrl = baseUrl, slipPrinter = BrowserSlipPrinter())
    }
    installFocusGuard(document.body!!)
}
