package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.auth.TokenClient
import info.unterrainer.presserl.admin.auth.Tokens
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.parseQueryString
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TokenClientTest {

    private val forms = mutableListOf<Map<String, String>>()
    private var now = 1_000_000.0

    private fun client(idToken: String? = "id-1") = TokenClient(
        HttpClient(MockEngine { request ->
            assertEquals("https://kc.example.org/token", request.url.toString())
            val form = parseQueryString(request.body.toByteArray().decodeToString())
            forms += form.names().associateWith { form[it]!! }
            val id = idToken?.let { """, "id_token": "$it"""" } ?: ""
            respond(
                """{"access_token": "access-${forms.size}", "expires_in": 300, "refresh_token": "refresh-${forms.size}"$id}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }),
        clientId = "presserl-admin",
        tokenEndpoint = "https://kc.example.org/token",
        now = { now },
    )

    @Test
    fun exchangesTheCodeWithVerifierAndRedirectUri() = runTest {
        val tokens = client().exchangeCode("the-code", "https://example.org/admin/", "the-verifier")

        assertEquals(
            mapOf(
                "client_id" to "presserl-admin",
                "grant_type" to "authorization_code",
                "code" to "the-code",
                "redirect_uri" to "https://example.org/admin/",
                "code_verifier" to "the-verifier",
            ),
            forms.single(),
        )
        assertEquals("access-1", tokens.access)
        assertEquals("refresh-1", tokens.refresh)
        assertEquals("id-1", tokens.idToken)
        assertEquals(now + 300_000.0, tokens.expiresAt)
    }

    @Test
    fun keepsTokensThatAreValidBeyondTheMargin() = runTest {
        val current = Tokens("a", "r", "i", expiresAt = now + 31_000.0)

        assertSame(current, client().fresh(current))
        assertTrue(forms.isEmpty())
    }

    @Test
    fun refreshesWithinTheMarginAndKeepsTheIdToken() = runTest {
        val current = Tokens("a", "r", "i", expiresAt = now + 29_000.0)

        val fresh = client(idToken = null).fresh(current)

        assertEquals(mapOf("client_id" to "presserl-admin", "grant_type" to "refresh_token", "refresh_token" to "r"), forms.single())
        assertEquals("access-1", fresh.access)
        assertEquals("i", fresh.idToken)
    }

    @Test
    fun keepsExpiringTokensWithoutRefreshToken() = runTest {
        val current = Tokens("a", null, "i", expiresAt = now)

        assertSame(current, client().fresh(current))
        assertTrue(forms.isEmpty())
    }
}
