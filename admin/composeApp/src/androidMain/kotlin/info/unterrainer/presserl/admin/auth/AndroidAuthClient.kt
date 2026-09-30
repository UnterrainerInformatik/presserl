package info.unterrainer.presserl.admin.auth

import info.unterrainer.presserl.admin.api.OidcDto
import info.unterrainer.presserl.admin.api.withJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** An issuer page the app shows in its web view: the login, or the end of the session on logout. */
data class IssuerPage(val url: String, val logout: Boolean = false)

/**
 * Authorization code flow with PKCE in a web view the app owns (design D3): [start] shows the issuer's login page
 * through [page] and waits until the web view reports the redirect to [redirectUri] via [interceptRedirect]; the
 * redirect itself is never loaded. Tokens live in memory only; [onLoggedIn] follows the code exchange. [onRestart] lets the host start the flow again (the
 * web app reloads for that); [onLoggedOut] follows the end of the issuer session.
 */
class AndroidAuthClient(
    http: HttpClient,
    base: String,
    private val oidc: OidcDto,
    private val onLoggedIn: () -> Unit,
    private val onRestart: () -> Unit,
    private val onLoggedOut: () -> Unit,
) : AuthClient {

    private val http = http.withJson()

    /** Already registered for the web app, so the issuer needs nothing new. */
    val redirectUri = "${base.trimEnd('/')}/admin/"

    private val _page = MutableStateFlow<IssuerPage?>(null)
    val page: StateFlow<IssuerPage?> = _page.asStateFlow()

    private var discovery: OidcDiscovery? = null
    private var tokenClient: TokenClient? = null
    private var tokens: Tokens? = null
    private var callback: CompletableDeferred<Url>? = null

    val issuerHost: String get() = Url(oidc.issuer).host

    override suspend fun start(): AuthState {
        val discovery = http.get(oidc.issuer.trimEnd('/') + "/.well-known/openid-configuration").body<OidcDiscovery>()
        this.discovery = discovery
        val tokenClient = TokenClient(http, oidc.clientId, discovery.tokenEndpoint).also { tokenClient = it }
        val verifier = Pkce.newVerifier()
        val state = Pkce.newState()
        val url = URLBuilder(discovery.authorizationEndpoint).apply {
            parameters.append("response_type", "code")
            parameters.append("client_id", oidc.clientId)
            parameters.append("redirect_uri", redirectUri)
            parameters.append("scope", oidc.scopes.joinToString(" "))
            parameters.append("state", state)
            parameters.append("code_challenge", Pkce.challenge(verifier))
            parameters.append("code_challenge_method", Pkce.METHOD)
        }.buildString()
        val pending = CompletableDeferred<Url>().also { callback = it }
        _page.value = IssuerPage(url)
        val response = try {
            pending.await()
        } finally {
            callback = null
            if (_page.value?.logout == false) _page.value = null
        }
        val error = response.parameters["error"]
        if (error != null) return AuthState.LoginFailed(response.parameters["error_description"] ?: error)
        val code = response.parameters["code"]
        if (code == null || !Pkce.stateMatches(state, response.parameters["state"])) {
            return AuthState.LoginFailed("The login response did not match the login request")
        }
        tokens = tokenClient.exchangeCode(code, redirectUri, verifier)
        onLoggedIn()
        return AuthState.LoggedIn
    }

    /**
     * Called by the web view for every navigation. `true` when [url] is the redirect back to the app: it completes the
     * login or the logout and must not be loaded.
     */
    fun interceptRedirect(url: String): Boolean {
        if (url.substringBefore('#').substringBefore('?') != redirectUri) return false
        if (_page.value?.logout == true) finishLogout() else callback?.complete(Url(url))
        return true
    }

    override suspend fun accessToken(): String {
        val fresh = tokenClient!!.fresh(tokens ?: error("Not logged in"))
        tokens = fresh
        return fresh.access
    }

    override fun login() = onRestart()

    override fun logout() {
        val idToken = tokens?.idToken
        tokens = null
        val endSession = discovery?.endSessionEndpoint ?: return finishLogout()
        _page.value = IssuerPage(
            URLBuilder(endSession).apply {
                parameters.append("client_id", oidc.clientId)
                parameters.append("post_logout_redirect_uri", redirectUri)
                idToken?.let { parameters.append("id_token_hint", it) }
            }.buildString(),
            logout = true,
        )
    }

    /** Also used when the end-session page cannot be loaded: the app forgets the session either way. */
    fun finishLogout() {
        _page.value = null
        onLoggedOut()
    }
}
