package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.api.ClientConfigDto
import info.unterrainer.presserl.admin.api.SpellMatchDto
import info.unterrainer.presserl.admin.api.json
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpellCheckApiTest {

    private var status = HttpStatusCode.OK
    private var throwing = false
    private val bodies = mutableListOf<String>()

    private val api = ApiClient(
        HttpClient(MockEngine { request ->
            if (throwing) throw IllegalStateException("network down")
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("https://news.example.org/api/spell-check", request.url.toString())
            assertEquals("Bearer token-123", request.headers[HttpHeaders.Authorization])
            bodies += (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            val body = if (status == HttpStatusCode.OK) {
                """{ "matches": [{ "offset": 13, "length": 4, "message": "Möglicher Tippfehler gefunden.",
                    "replacements": ["groß", "Gros"] }] }"""
            } else {
                """{ "errors": [{ "field": null, "message": "the spell check is currently unavailable" }] }"""
            }
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }),
        baseUrl = "https://news.example.org",
    ) { "token-123" }

    @Test
    fun findingsAreReturned() = runTest {
        assertEquals(
            listOf(SpellMatchDto(13, 4, "Möglicher Tippfehler gefunden.", listOf("groß", "Gros"))),
            api.spellCheck("Der Hund ist gros."),
        )
        assertEquals("""{"text":"Der Hund ist gros."}""", bodies.single())
    }

    @Test
    fun unavailableIsNull() = runTest {
        status = HttpStatusCode.ServiceUnavailable
        assertNull(api.spellCheck("gros"))
    }

    @Test
    fun aNetworkErrorIsNull() = runTest {
        throwing = true
        assertNull(api.spellCheck("gros"))
    }

    @Test
    fun clientConfigAnnouncesTheSpellCheckAndDefaultsToOff() {
        val oidc = """"oidc": { "issuer": "https://kc/realms/presserl", "clientId": "presserl-admin", "scopes": ["openid"] }"""
        assertTrue(json.decodeFromString<ClientConfigDto>("{ $oidc, \"spellCheck\": true }").spellCheck)
        assertFalse(json.decodeFromString<ClientConfigDto>("{ $oidc }").spellCheck)
    }
}
