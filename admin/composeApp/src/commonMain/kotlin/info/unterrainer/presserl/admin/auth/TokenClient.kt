package info.unterrainer.presserl.admin.auth

import info.unterrainer.presserl.admin.api.withJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.http.parameters
import kotlin.time.Clock

/** Tokens of a login, held in memory only; [expiresAt] of the access token in epoch milliseconds. */
class Tokens(val access: String, val refresh: String?, val idToken: String?, val expiresAt: Double) {
    override fun toString(): String = "Tokens(expiresAt=$expiresAt)"
}

/**
 * The issuer's token endpoint for the public client [clientId]: exchanges an authorization code (PKCE) and refreshes
 * access tokens shortly before they expire. Shared by the web and the Android login.
 */
class TokenClient(
    http: HttpClient,
    private val clientId: String,
    private val tokenEndpoint: String,
    private val now: () -> Double = { Clock.System.now().toEpochMilliseconds().toDouble() },
) {
    private val http = http.withJson()

    suspend fun exchangeCode(code: String, redirectUri: String, verifier: String): Tokens = request(
        previous = null,
        "grant_type" to "authorization_code",
        "code" to code,
        "redirect_uri" to redirectUri,
        "code_verifier" to verifier,
    )

    /** [current] while its access token is valid for more than the refresh margin or it cannot be refreshed. */
    suspend fun fresh(current: Tokens): Tokens {
        val refresh = current.refresh
        if (now() < current.expiresAt - REFRESH_MARGIN_MS || refresh == null) return current
        return request(current, "grant_type" to "refresh_token", "refresh_token" to refresh)
    }

    private suspend fun request(previous: Tokens?, vararg form: Pair<String, String>): Tokens {
        val response: TokenResponse = http.submitForm(tokenEndpoint, parameters {
            append("client_id", clientId)
            form.forEach { (name, value) -> append(name, value) }
        }).body()
        return Tokens(
            access = response.accessToken,
            refresh = response.refreshToken,
            idToken = response.idToken ?: previous?.idToken,
            expiresAt = now() + response.expiresIn * 1000.0,
        )
    }

    private companion object {
        const val REFRESH_MARGIN_MS = 30_000.0
    }
}
