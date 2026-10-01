package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.api.OidcDto
import info.unterrainer.presserl.admin.ui.account.SlipCredentials
import info.unterrainer.presserl.admin.ui.connect.ConnectError
import info.unterrainer.presserl.admin.ui.connect.ConnectionModel
import info.unterrainer.presserl.admin.ui.connect.ConnectionState
import info.unterrainer.presserl.admin.ui.connect.ConnectionStore
import info.unterrainer.presserl.admin.ui.connect.StoredConnection
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionModelTest {

    private val oidc = OidcDto("https://kc.example.org/realms/presserl", "presserl-admin", listOf("openid"))
    private val slip = "https://zeitung.example.org/qr?u=anna#pw=tiger-wolke-apfel-leiter"
    private val anna = SlipCredentials("https://zeitung.example.org", "anna", "tiger-wolke-apfel-leiter")

    private val requested = mutableListOf<String>()

    /** Answers `GET /api/client-config` per host: a presserl server, a 404, an HTML page, or no answer at all. */
    private fun TestScope.http() = HttpClient(MockEngine.create {
        dispatcher = UnconfinedTestDispatcher(testScheduler)
        addHandler { request ->
            requested += request.url.toString()
            when (request.url.host) {
                "zeitung.example.org", "10.0.2.2" -> respond(
                    """{"oidc": {"issuer": "${oidc.issuer}", "clientId": "${oidc.clientId}", "scopes": ["openid"]}, "spellCheck": true}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
                "down.example.org" -> throw IOException("Unable to resolve host \"down.example.org\"")
                "html.example.org" -> respond("<html></html>", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html"))
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
    })

    private class MemoryStore(var stored: StoredConnection? = null) : ConnectionStore {
        override fun load() = stored
        override fun save(connection: StoredConnection) {
            stored = connection
        }
        override fun clear() {
            stored = null
        }
    }

    private fun TestScope.model(store: ConnectionStore, allowHttp: Boolean = false): ConnectionModel {
        val http = http()
        return ConnectionModel(TestScope(UnconfinedTestDispatcher(testScheduler)), store, allowHttp) { base ->
            ApiClient(http, base) { error("no token needed") }.clientConfig()
        }
    }

    @Test
    fun addressOfANewspaperLeadsToItsLogin() = runTest {
        val store = MemoryStore()
        val model = model(store)

        model.enterAddress(" zeitung.example.org/ ")

        assertEquals(listOf("https://zeitung.example.org/api/client-config"), requested)
        assertEquals(ConnectionState.Login("https://zeitung.example.org", oidc), model.state.value)
        assertEquals(StoredConnection("https://zeitung.example.org"), store.stored)
    }

    @Test
    fun addressWithout404StaysOnTheStartScreen() = runTest {
        val store = MemoryStore()
        val model = model(store)

        model.enterAddress("https://other.example.org")

        assertEquals(ConnectionState.Start(ConnectError.NO_PRESSERL_SERVER), model.state.value)
        assertNull(store.stored)
    }

    @Test
    fun addressAnsweringWithoutJsonIsNoNewspaper() = runTest {
        val model = model(MemoryStore())

        model.enterAddress("https://html.example.org")

        assertEquals(ConnectionState.Start(ConnectError.NO_PRESSERL_SERVER), model.state.value)
    }

    @Test
    fun unreachableAddressNamesTheServer() = runTest {
        val store = MemoryStore()
        val model = model(store)

        model.enterAddress("down.example.org/")

        assertEquals(ConnectionState.Start(ConnectError.UNREACHABLE, address = "https://down.example.org"), model.state.value)
        assertNull(store.stored)
    }

    @Test
    fun scannedSlipOfAnUnreachableServerNamesOnlyTheBase() = runTest {
        val store = MemoryStore()
        val model = model(store)

        model.scanned("https://down.example.org/qr?u=anna#pw=tiger-wolke-apfel-leiter")

        assertEquals(listOf("https://down.example.org/api/client-config"), requested)
        assertEquals(ConnectionState.Start(ConnectError.UNREACHABLE, address = "https://down.example.org"), model.state.value)
        assertNull(store.stored)
    }

    @Test
    fun plainHttpIsRefusedWithoutARequest() = runTest {
        val model = model(MemoryStore())

        model.enterAddress("http://zeitung.example.org")

        assertEquals(ConnectionState.Start(ConnectError.HTTP_REFUSED), model.state.value)
        assertTrue(requested.isEmpty())
    }

    @Test
    fun plainHttpIsAcceptedInDebugBuilds() = runTest {
        val model = model(MemoryStore(), allowHttp = true)

        model.enterAddress("http://10.0.2.2:8080")

        assertEquals(ConnectionState.Login("http://10.0.2.2:8080", oidc), model.state.value)
    }

    @Test
    fun malformedAddressesAreNoNewspaper() = runTest {
        val model = model(MemoryStore())

        for (text in listOf("", "   ", "ftp://zeitung.example.org", "https://zeitung.example.org/?x=1")) {
            model.enterAddress(text)
            assertEquals(ConnectionState.Start(ConnectError.NO_PRESSERL_SERVER), model.state.value, text)
        }
        assertTrue(requested.isEmpty())
    }

    @Test
    fun scannedSlipLeadsToTheLoginWithItsCredentials() = runTest {
        val store = MemoryStore()
        val model = model(store)

        model.scanned(slip)

        assertEquals(ConnectionState.Login("https://zeitung.example.org", oidc, anna), model.state.value)
        // credentials are stored only after they led to a login
        assertEquals(StoredConnection("https://zeitung.example.org"), store.stored)

        model.loggedIn()

        assertEquals(ConnectionState.Connected("https://zeitung.example.org", oidc), model.state.value)
        assertEquals(StoredConnection("https://zeitung.example.org", anna), store.stored)
    }

    @Test
    fun foreignCodeIsRefusedWithoutAnyRequest() = runTest {
        val model = model(MemoryStore())

        model.scanned("https://example.com/some/page")

        assertEquals(ConnectionState.Start(ConnectError.NOT_A_SLIP), model.state.value)
        assertTrue(requested.isEmpty())
    }

    @Test
    fun rejectedCredentialsAreDeletedAndTheLoginPageStays() = runTest {
        val store = MemoryStore(StoredConnection("https://zeitung.example.org", anna))
        val model = model(store)
        model.resume()

        model.credentialsRejected()

        assertEquals(
            ConnectionState.Login("https://zeitung.example.org", oidc, credentials = null, credentialsRejected = true),
            model.state.value,
        )
        assertEquals(StoredConnection("https://zeitung.example.org"), store.stored)
    }

    @Test
    fun resumeLogsInWithStoredCredentials() = runTest {
        val model = model(MemoryStore(StoredConnection("https://zeitung.example.org", anna)))

        model.resume()

        assertEquals(ConnectionState.Login("https://zeitung.example.org", oidc, anna), model.state.value)
    }

    @Test
    fun resumeWithoutStoredServerShowsTheStartScreen() = runTest {
        val model = model(MemoryStore())

        model.resume()

        assertEquals(ConnectionState.Start(), model.state.value)
        assertTrue(requested.isEmpty())
    }

    @Test
    fun loginByAddressStoresNoCredentials() = runTest {
        val store = MemoryStore()
        val model = model(store)
        model.enterAddress("https://zeitung.example.org")

        model.loggedIn()

        assertEquals(StoredConnection("https://zeitung.example.org"), store.stored)
    }

    @Test
    fun logoutForgetsServerAndCredentials() = runTest {
        val store = MemoryStore()
        val model = model(store)
        model.scanned(slip)
        model.loggedIn()

        model.logout()

        assertEquals(ConnectionState.Start(), model.state.value)
        assertNull(store.stored)
    }

    @Test
    fun cancellingTheLoginForgetsTheServer() = runTest {
        val store = MemoryStore()
        val model = model(store)
        model.enterAddress("https://zeitung.example.org")

        model.cancel()

        assertEquals(ConnectionState.Start(), model.state.value)
        assertNull(store.stored)
    }
}
