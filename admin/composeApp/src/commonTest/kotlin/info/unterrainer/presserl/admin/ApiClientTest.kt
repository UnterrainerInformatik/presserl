package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.api.AccountDto
import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.api.ArticleContent
import info.unterrainer.presserl.admin.api.CreateAccountRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ApiClientTest {

    private data class Recorded(val method: HttpMethod, val url: String, val authorization: String?, val body: JsonElement?)

    private val requests = mutableListOf<Recorded>()

    private val api = ApiClient(
        HttpClient(MockEngine { request ->
            val sent = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString()
            requests += Recorded(
                request.method,
                request.url.toString(),
                request.headers[HttpHeaders.Authorization],
                sent?.let { Json.parseToJsonElement(it) },
            )
            val path = request.url.encodedPath
            val body = when {
                path == "/api/me" -> """{ "username": "papa", "displayName": "Papa", "roles": ["PUBLISHER"] }"""
                path == "/api/articles" && request.method == HttpMethod.Get -> "[$SUMMARY]"
                path.endsWith("/revisions") -> """[{ "number": 1, "headline": "H", "createdAt": "t", "updatedAt": "t", "live": false }]"""
                path.contains("/revisions/") -> """{ "number": 1, "headline": "H", "createdAt": "t", "updatedAt": "t",
                    "live": true, "kicker": "", "subheadline": "", "lead": "", "body": { "version": 1, "blocks": [] } }"""
                path.startsWith("/api/articles") -> ARTICLE
                path == "/api/accounts/username-suggestion" -> """{ "username": "juergen-maria" }"""
                path == "/api/accounts" && request.method == HttpMethod.Get -> """{ "assignableRoles": ["EDITOR_IN_CHIEF", "READER"],
                    "accounts": [$ACCOUNT] }"""
                path == "/api/accounts" -> """{ "account": $ACCOUNT, "password": "tiger-wolke-apfel-leiter" }"""
                else -> """{ "oidc": { "issuer": "https://kc/realms/presserl", "clientId": "presserl-admin", "scopes": ["openid"] } }"""
            }
            if (request.method == HttpMethod.Delete) {
                respond("", HttpStatusCode.NoContent)
            } else {
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }),
        baseUrl = "https://news.example.org",
    ) { "token-123" }

    @Test
    fun meSendsTheBearerToken() = runTest {
        assertEquals("Papa", api.me().displayName)
        val request = requests.single()
        assertEquals("https://news.example.org/api/me", request.url)
        assertEquals("Bearer token-123", request.authorization)
    }

    @Test
    fun clientConfigIsAnonymous() = runTest {
        assertEquals("presserl-admin", api.clientConfig().oidc.clientId)
        assertNull(requests.single().authorization)
    }

    @Test
    fun listArticlesWithoutFilters() = runTest {
        assertEquals(42, api.articles().single().id)
        assertEquals(Recorded(HttpMethod.Get, "https://news.example.org/api/articles", "Bearer token-123", null), requests.single())
    }

    @Test
    fun listArticlesWithFilters() = runTest {
        api.articles(status = "DRAFT", mine = true)
        assertEquals("https://news.example.org/api/articles?status=DRAFT&mine=true", requests.single().url)
    }

    @Test
    fun getArticle() = runTest {
        assertEquals("The pumpkin is huge", api.article(42).headline)
        assertEquals(HttpMethod.Get to "https://news.example.org/api/articles/42", requests.single().let { it.method to it.url })
    }

    @Test
    fun createArticleSendsOnlyTheGivenContent() = runTest {
        api.createArticle(ArticleContent(headline = "Hello"))
        val request = requests.single()
        assertEquals(HttpMethod.Post to "https://news.example.org/api/articles", request.method to request.url)
        assertEquals(buildJsonObject { put("headline", "Hello") }, request.body)
        assertEquals("Bearer token-123", request.authorization)
    }

    @Test
    fun updateArticleSendsContentAndVersion() = runTest {
        val body = buildJsonObject {
            put("version", 1)
            putJsonArray("blocks") {}
        }
        api.updateArticle(42, ArticleContent(kicker = "K", headline = "H", body = body), version = 5)
        val request = requests.single()
        assertEquals(HttpMethod.Put to "https://news.example.org/api/articles/42", request.method to request.url)
        assertEquals(
            buildJsonObject {
                put("kicker", "K")
                put("headline", "H")
                put("subheadline", "")
                put("lead", "")
                put("body", body)
                put("version", 5)
            },
            request.body,
        )
    }

    @Test
    fun deletePublishAndTakeOffline() = runTest {
        api.deleteArticle(42)
        api.publishArticle(42)
        api.takeArticleOffline(42)
        assertEquals(
            listOf(
                HttpMethod.Delete to "https://news.example.org/api/articles/42",
                HttpMethod.Post to "https://news.example.org/api/articles/42/publish",
                HttpMethod.Post to "https://news.example.org/api/articles/42/offline",
            ),
            requests.map { it.method to it.url },
        )
        requests.forEach { assertEquals("Bearer token-123", it.authorization) }
    }

    @Test
    fun revisions() = runTest {
        assertEquals(1, api.revisions(42).single().number)
        assertEquals(true, api.revision(42, 1).live)
        assertEquals(
            listOf(
                "https://news.example.org/api/articles/42/revisions",
                "https://news.example.org/api/articles/42/revisions/1",
            ),
            requests.map { it.url },
        )
    }

    @Test
    fun listAccounts() = runTest {
        val list = api.accounts()
        assertEquals(listOf("EDITOR_IN_CHIEF", "READER"), list.assignableRoles)
        assertEquals(AccountDto("9a1e", "lena", "Lena", "", listOf("EDITOR_IN_CHIEF"), true), list.accounts.single())
        assertEquals(Recorded(HttpMethod.Get, "https://news.example.org/api/accounts", "Bearer token-123", null), requests.single())
    }

    @Test
    fun usernameSuggestionEncodesTheFirstName() = runTest {
        assertEquals("juergen-maria", api.usernameSuggestion("Jürgen Maria"))
        val request = requests.single()
        assertEquals("https://news.example.org/api/accounts/username-suggestion?firstName=J%C3%BCrgen+Maria", request.url)
        assertEquals("Bearer token-123", request.authorization)
    }

    @Test
    fun createAccountSendsTheFormAndReturnsThePassword() = runTest {
        val created = api.createAccount(CreateAccountRequest("Lena", "", "lena", listOf("EDITOR_IN_CHIEF")))
        assertEquals("tiger-wolke-apfel-leiter", created.password)
        assertEquals("lena", created.account.username)
        val request = requests.single()
        assertEquals(HttpMethod.Post to "https://news.example.org/api/accounts", request.method to request.url)
        assertEquals(
            buildJsonObject {
                put("firstName", "Lena")
                put("lastName", "")
                put("username", "lena")
                putJsonArray("roles") { add(JsonPrimitive("EDITOR_IN_CHIEF")) }
            },
            request.body,
        )
        assertEquals("Bearer token-123", request.authorization)
    }

    private companion object {
        const val ACCOUNT = """{ "id": "9a1e", "username": "lena", "firstName": "Lena", "lastName": "",
            "roles": ["EDITOR_IN_CHIEF"], "enabled": true }"""
        const val SUMMARY = """{ "id": 42, "status": "DRAFT", "author": { "username": "papa", "displayName": "Papa" },
            "headline": "H", "kicker": "", "revision": 1, "liveRevision": null, "hasUnpublishedChanges": false,
            "updatedAt": "2026-09-26T10:05:00Z", "publishedAt": null, "allowedActions": ["EDIT", "PUBLISH", "DELETE"] }"""
    }
}
