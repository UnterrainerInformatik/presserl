package info.unterrainer.presserl.admin.ui.connect

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.auth.AndroidAuthClient
import info.unterrainer.presserl.admin.auth.LoginWebView
import info.unterrainer.presserl.admin.auth.SlipAutofill
import info.unterrainer.presserl.admin.auth.clearWebSession
import info.unterrainer.presserl.admin.resources.Res
import info.unterrainer.presserl.admin.resources.connect_credentials_rejected
import info.unterrainer.presserl.admin.ui.App
import info.unterrainer.presserl.admin.ui.Banner
import info.unterrainer.presserl.admin.ui.PresserlTheme
import info.unterrainer.presserl.admin.ui.SystemBackHandler
import info.unterrainer.presserl.admin.ui.account.SlipPrinter
import io.ktor.client.HttpClient
import org.jetbrains.compose.resources.stringResource

/**
 * The Android app: the start screen until a server is chosen, then the admin [App] for it, with the issuer's pages
 * (login, logout) in a web view above it (design D7).
 */
@Composable
fun AndroidApp(
    model: ConnectionModel,
    http: HttpClient,
    slipPrinter: SlipPrinter,
    onScan: () -> Unit,
    onPrivacyPolicy: () -> Unit,
) {
    val state by model.state.collectAsState()
    PresserlTheme {
        Surface(Modifier.fillMaxSize()) {
            when (val current = state) {
                is ConnectionState.Start, is ConnectionState.Checking ->
                    StartScreen(current, onScan = onScan, onAddress = model::enterAddress, onPrivacyPolicy = onPrivacyPolicy)
                is ConnectionState.Login -> key(current.base, current.oidc) { Session(model, current.base, current, http, slipPrinter) }
                is ConnectionState.Connected -> key(current.base, current.oidc) { Session(model, current.base, current, http, slipPrinter) }
            }
        }
    }
}

@Composable
private fun Session(model: ConnectionModel, base: String, state: ConnectionState, http: HttpClient, slipPrinter: SlipPrinter) {
    // "try again" after a failed login starts the flow afresh, as the web app's reload does
    var attempt by remember { mutableIntStateOf(0) }
    val auth = remember(attempt) {
        val oidc = when (state) {
            is ConnectionState.Login -> state.oidc
            is ConnectionState.Connected -> state.oidc
            else -> error("no session in $state")
        }
        AndroidAuthClient(
            http,
            base,
            oidc,
            onLoggedIn = model::loggedIn,
            onRestart = { attempt++ },
            onLoggedOut = {
                clearWebSession()
                model.logout()
            },
        )
    }
    val currentAuth by rememberUpdatedState(auth)
    val api = remember { ApiClient(http, base) { currentAuth.accessToken() } }
    val autofill = remember(auth) { SlipAutofill((state as? ConnectionState.Login)?.credentials) }
    val page by auth.page.collectAsState()

    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        key(attempt) { App(auth, api, siteUrl = base, slipPrinter = slipPrinter) }
        page?.let { current ->
            SystemBackHandler { if (current.logout) auth.finishLogout() else model.cancel() }
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    if ((state as? ConnectionState.Login)?.credentialsRejected == true) {
                        Box(Modifier.fillMaxWidth().padding(8.dp)) { Banner(stringResource(Res.string.connect_credentials_rejected)) }
                    }
                    LoginWebView(current, auth, autofill, onRejected = model::credentialsRejected, modifier = Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}
