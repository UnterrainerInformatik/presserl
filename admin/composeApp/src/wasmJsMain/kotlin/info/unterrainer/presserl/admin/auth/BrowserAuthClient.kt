@file:OptIn(ExperimentalWasmJsInterop::class)

package info.unterrainer.presserl.admin.auth

import info.unterrainer.presserl.admin.api.OidcDto
import info.unterrainer.presserl.admin.api.withJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.URLBuilder
import io.ktor.http.parseQueryString
import kotlinx.browser.sessionStorage
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlin.js.ExperimentalWasmJsInterop

/**
 * Authorization code flow with PKCE via full-page redirects. Tokens live in memory only; the
 * PKCE verifier and `state` survive the redirect in `sessionStorage`. On reload the flow runs
 * again, which the Keycloak SSO cookie turns into a silent redirect.
 */
class BrowserAuthClient(
    http: HttpClient,
    private val oidcConfig: suspend () -> OidcDto,
) : AuthClient {

    private val http = http.withJson()
    private val scope = MainScope()
    private lateinit var oidc: OidcDto
    private lateinit var discovery: OidcDiscovery
    private lateinit var tokenClient: TokenClient
    private var tokens: Tokens? = null

    /** This page without query or fragment; registered as redirect URI in the realm. */
    private val redirectUri: String
        get() = window.location.origin + window.location.pathname

    override suspend fun start(): AuthState {
        oidc = oidcConfig()
        discovery = http.get(oidc.issuer.trimEnd('/') + "/.well-known/openid-configuration").body()
        tokenClient = TokenClient(http, oidc.clientId, discovery.tokenEndpoint)

        val query = parseQueryString(window.location.search.removePrefix("?"))
        val expectedState = sessionStorage.getItem(STATE_KEY)
        val verifier = sessionStorage.getItem(VERIFIER_KEY)
        sessionStorage.removeItem(STATE_KEY)
        sessionStorage.removeItem(VERIFIER_KEY)

        val error = query["error"]
        if (error != null) {
            clearCallbackFromUrl()
            return AuthState.LoginFailed(query["error_description"] ?: error)
        }
        val code = query["code"] ?: run {
            login()
            return AuthState.Redirecting
        }
        clearCallbackFromUrl()
        if (!Pkce.stateMatches(expectedState, query["state"]) || verifier == null) {
            return AuthState.LoginFailed("The login response did not match the login request")
        }
        tokens = tokenClient.exchangeCode(code, redirectUri, verifier)
        return AuthState.LoggedIn
    }

    override suspend fun accessToken(): String {
        val fresh = tokenClient.fresh(tokens ?: error("Not logged in"))
        tokens = fresh
        return fresh.access
    }

    override fun login() {
        val verifier = Pkce.newVerifier()
        val state = Pkce.newState()
        sessionStorage.setItem(VERIFIER_KEY, verifier)
        sessionStorage.setItem(STATE_KEY, state)
        // The challenge needs Web Crypto, which is asynchronous
        scope.launch {
            val url = URLBuilder(discovery.authorizationEndpoint).apply {
                parameters.append("response_type", "code")
                parameters.append("client_id", oidc.clientId)
                parameters.append("redirect_uri", redirectUri)
                parameters.append("scope", oidc.scopes.joinToString(" "))
                parameters.append("state", state)
                parameters.append("code_challenge", Pkce.challenge(verifier))
                parameters.append("code_challenge_method", Pkce.METHOD)
            }.buildString()
            window.location.assign(url)
        }
    }

    override fun logout() {
        val idToken = tokens?.idToken
        tokens = null
        val endSession = discovery.endSessionEndpoint
        if (endSession == null) {
            window.location.assign(redirectUri)
            return
        }
        window.location.assign(URLBuilder(endSession).apply {
            parameters.append("client_id", oidc.clientId)
            parameters.append("post_logout_redirect_uri", redirectUri)
            idToken?.let { parameters.append("id_token_hint", it) }
        }.buildString())
    }

    private fun clearCallbackFromUrl() {
        window.history.replaceState(null, "", redirectUri)
    }

    private companion object {
        const val VERIFIER_KEY = "presserl.pkce.verifier"
        const val STATE_KEY = "presserl.pkce.state"
    }
}
