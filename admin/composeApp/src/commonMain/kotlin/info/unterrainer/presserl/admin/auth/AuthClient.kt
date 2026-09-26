package info.unterrainer.presserl.admin.auth

/**
 * The OIDC boundary of the admin app. The web implementation runs the authorization code flow
 * with PKCE via full-page redirects; Android/iOS will bring their own implementations later.
 */
interface AuthClient {

    /**
     * Completes a pending login callback or starts a login. Returns [AuthState.Redirecting] when the
     * page is about to leave for the issuer.
     */
    suspend fun start(): AuthState

    /** A valid access token, refreshed when it is about to expire. */
    suspend fun accessToken(): String

    /** Leaves for the issuer's login page. */
    fun login()

    /** Discards the tokens and ends the Keycloak session via the end-session endpoint. */
    fun logout()
}

sealed interface AuthState {
    data object Redirecting : AuthState
    data object LoggedIn : AuthState
    data class LoginFailed(val reason: String) : AuthState
}
