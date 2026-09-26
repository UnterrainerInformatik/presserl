package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.api.ApiClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ApiClientTest {

    private val requests = mutableListOf<Pair<String, String?>>()

    private val api = ApiClient(
        HttpClient(MockEngine { request ->
            requests += request.url.toString() to request.headers[HttpHeaders.Authorization]
            val body = when (request.url.encodedPath) {
                "/api/me" -> """{ "username": "papa", "displayName": "Papa", "roles": ["PUBLISHER"] }"""
                else -> """{ "oidc": { "issuer": "https://kc/realms/presserl", "clientId": "presserl-admin", "scopes": ["openid"] } }"""
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }),
        baseUrl = "https://news.example.org",
    ) { "token-123" }

    @Test
    fun meSendsTheBearerToken() = runTest {
        assertEquals("Papa", api.me().displayName)
        assertEquals("https://news.example.org/api/me" to "Bearer token-123", requests.single())
    }

    @Test
    fun clientConfigIsAnonymous() = runTest {
        assertEquals("presserl-admin", api.clientConfig().oidc.clientId)
        assertNull(requests.single().second)
    }
}
