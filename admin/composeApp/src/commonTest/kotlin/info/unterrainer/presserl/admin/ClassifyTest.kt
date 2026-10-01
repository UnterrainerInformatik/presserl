package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.api.withJson
import info.unterrainer.presserl.admin.ui.connect.ConnectError
import info.unterrainer.presserl.admin.ui.connect.classify
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.ContentConvertException
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClassifyTest {

    @Test
    fun errorStatusIsAnAnswer() = runTest {
        val http = HttpClient(MockEngine { respond("", HttpStatusCode.NotFound) }).withJson()
        val e = assertFailsWith<Throwable> { http.get("https://x.example.org/api/client-config") }

        assertEquals(ConnectError.NO_PRESSERL_SERVER, classify(e))
    }

    @Test
    fun conversionFailureIsAnAnswer() {
        assertEquals(ConnectError.NO_PRESSERL_SERVER, classify(ContentConvertException("not a client config")))
        assertEquals(ConnectError.NO_PRESSERL_SERVER, classify(SerializationException("missing field")))
    }

    @Test
    fun timeoutIsUnreachable() {
        assertEquals(ConnectError.UNREACHABLE, classify(HttpRequestTimeoutException("https://x.example.org", 15_000L)))
    }

    @Test
    fun ioExceptionIsUnreachable() {
        assertEquals(ConnectError.UNREACHABLE, classify(IOException("Connection refused")))
    }
}
