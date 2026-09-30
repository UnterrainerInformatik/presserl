package info.unterrainer.presserl.admin.ui.connect

import info.unterrainer.presserl.admin.api.ClientConfigDto
import info.unterrainer.presserl.admin.api.OidcDto
import info.unterrainer.presserl.admin.ui.account.SlipCredentials
import info.unterrainer.presserl.admin.ui.account.SlipQr
import io.ktor.http.URLProtocol
import io.ktor.http.parseUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Why the start screen refused an address or a scanned code. */
enum class ConnectError { NO_PRESSERL_SERVER, HTTP_REFUSED, NOT_A_SLIP, SCANNER_UNAVAILABLE }

/** Where the app stands on the way to a newspaper (design D7). */
sealed interface ConnectionState {
    /** The start screen: scan a slip or enter an address; [error] explains the last refusal. */
    data class Start(val error: ConnectError? = null) : ConnectionState

    /** `GET <base>/api/client-config` is running. */
    data class Checking(val base: String) : ConnectionState

    /**
     * The issuer's login page for [base]. With [credentials] from a slip the page is filled in and submitted once;
     * [credentialsRejected] means the issuer refused them and the user types instead.
     */
    data class Login(
        val base: String,
        val oidc: OidcDto,
        val credentials: SlipCredentials? = null,
        val credentialsRejected: Boolean = false,
    ) : ConnectionState

    /** Logged in to [base]. */
    data class Connected(val base: String, val oidc: OidcDto) : ConnectionState
}

/** What the device remembers: the server address until logout, slip credentials only after they led to a login. */
data class StoredConnection(val base: String, val credentials: SlipCredentials? = null)

/** Persistence of the connection on the device (Android: preferences plus Keystore-encrypted credentials). */
interface ConnectionStore {
    fun load(): StoredConnection?
    fun save(connection: StoredConnection)
    fun clear()
}

/**
 * The connect flow before the admin app: an address or a scanned slip is accepted only when [clientConfig] of it
 * names an OIDC configuration; `http` only when [allowHttp] (debug builds). The accepted address is stored right away,
 * credentials only by [loggedIn] after a login with them; [credentialsRejected] and [logout] delete them.
 */
class ConnectionModel(
    private val scope: CoroutineScope,
    private val store: ConnectionStore,
    private val allowHttp: Boolean,
    private val clientConfig: suspend (base: String) -> ClientConfigDto,
) {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Start())
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    /** At app start: back to the remembered server, logging in with stored credentials if there are any. */
    fun resume() {
        val stored = store.load() ?: return
        check(stored.base, stored.credentials)
    }

    fun enterAddress(text: String) {
        when (val base = normalizeAddress(text)) {
            is Address.Valid -> check(base.base, credentials = null)
            Address.HttpRefused -> _state.value = ConnectionState.Start(ConnectError.HTTP_REFUSED)
            Address.Invalid -> _state.value = ConnectionState.Start(ConnectError.NO_PRESSERL_SERVER)
        }
    }

    /** A code read by the scanner; anything but a slip is refused without a request. */
    fun scanned(text: String) {
        val credentials = SlipQr.parse(text, allowHttp)
        if (credentials == null) {
            _state.value = ConnectionState.Start(ConnectError.NOT_A_SLIP)
            return
        }
        check(credentials.base, credentials)
    }

    /** The device cannot scan (no Google Play services); the user enters the address instead. */
    fun scannerUnavailable() {
        _state.value = ConnectionState.Start(ConnectError.SCANNER_UNAVAILABLE)
    }

    /** The login succeeded; credentials that led to it are stored now. */
    fun loggedIn() {
        val login = _state.value as? ConnectionState.Login ?: return
        store.save(StoredConnection(login.base, login.credentials))
        _state.value = ConnectionState.Connected(login.base, login.oidc)
    }

    /** The issuer refused the slip's credentials: forget them, the user types on the login page. */
    fun credentialsRejected() {
        val login = _state.value as? ConnectionState.Login ?: return
        store.save(StoredConnection(login.base))
        _state.value = login.copy(credentials = null, credentialsRejected = true)
    }

    /** Leaves the login page for the start screen; the address is no longer remembered. */
    fun cancel() {
        store.clear()
        _state.value = ConnectionState.Start()
    }

    fun logout() {
        store.clear()
        _state.value = ConnectionState.Start()
    }

    private fun check(base: String, credentials: SlipCredentials?) {
        _state.value = ConnectionState.Checking(base)
        scope.launch {
            val oidc = try {
                clientConfig(base).oidc.takeIf { it.issuer.isNotBlank() && it.clientId.isNotBlank() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                null
            }
            if (oidc == null) {
                _state.value = ConnectionState.Start(ConnectError.NO_PRESSERL_SERVER)
                return@launch
            }
            store.save(StoredConnection(base, store.load()?.takeIf { it.base == base }?.credentials))
            _state.value = ConnectionState.Login(base, oidc, credentials)
        }
    }

    private sealed interface Address {
        data class Valid(val base: String) : Address
        data object HttpRefused : Address
        data object Invalid : Address
    }

    /** `https://` is assumed without a scheme; the result has no trailing slash, query or fragment. */
    private fun normalizeAddress(text: String): Address {
        val trimmed = text.trim().trimEnd('/')
        if (trimmed.isEmpty()) return Address.Invalid
        val url = parseUrl(if ("://" in trimmed) trimmed else "https://$trimmed") ?: return Address.Invalid
        if (url.host.isEmpty() || url.user != null || url.encodedQuery.isNotEmpty() || url.encodedFragment.isNotEmpty()) {
            return Address.Invalid
        }
        when (url.protocol) {
            URLProtocol.HTTPS -> Unit
            URLProtocol.HTTP -> if (!allowHttp) return Address.HttpRefused
            else -> return Address.Invalid
        }
        val port = if (url.specifiedPort == 0 || url.specifiedPort == url.protocol.defaultPort) "" else ":${url.port}"
        return Address.Valid("${url.protocol.name}://${url.host}$port${url.encodedPath.trimEnd('/')}")
    }
}
