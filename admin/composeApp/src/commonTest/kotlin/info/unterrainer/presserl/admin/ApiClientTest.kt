package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.api.AccountDto
import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.api.ArticleContent
import info.unterrainer.presserl.admin.api.CreateAccountRequest
import info.unterrainer.presserl.admin.api.EditRolesRequest
import info.unterrainer.presserl.admin.api.SectionRequest
import info.unterrainer.presserl.admin.api.SectionRoleDto
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
                path.endsWith("/reviews") -> "[$REVIEW]"
                path.endsWith("/revisions") -> """[{ "number": 1, "headline": "H", "createdAt": "t", "updatedAt": "t", "live": false }]"""
                path.contains("/revisions/") -> """{ "number": 1, "headline": "H", "createdAt": "t", "updatedAt": "t",
                    "live": true, "kicker": "", "subheadline": "", "lead": "", "body": { "version": 1, "blocks": [] } }"""
                path.startsWith("/api/articles") -> ARTICLE
                path == "/api/accounts/username-suggestion" -> """{ "username": "juergen-maria" }"""
                path == "/api/accounts" && request.method == HttpMethod.Get -> """{ "assignableRoles": ["EDITOR_IN_CHIEF", "READER"],
                    "accounts": [$ACCOUNT] }"""
                path == "/api/accounts" || path.endsWith("/password-reset") ->
                    """{ "account": $ACCOUNT, "password": "tiger-wolke-apfel-leiter" }"""
                path.endsWith("/lock") || path.endsWith("/unlock") || path.endsWith("/roles") -> ACCOUNT
                path == "/api/sections" && request.method == HttpMethod.Get || path == "/api/sections/order" ->
                    """{ "canManage": true, "sections": [$SECTION] }"""
                path.endsWith("/members") -> """{ "assignableRoles": ["SECTION_EDITOR", "REPORTER"], "members": [$MEMBER] }"""
                path.contains("/members/") -> MEMBER
                path.startsWith("/api/sections") -> SECTION
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
        val summary = api.articles().single()
        assertEquals(42, summary.id)
        assertEquals("Sport", summary.section?.name)
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
    fun updateArticleSendsTheSection() = runTest {
        api.updateArticle(42, ArticleContent(headline = "H", sectionId = 4), version = 5)
        assertEquals(
            buildJsonObject {
                put("kicker", "")
                put("headline", "H")
                put("subheadline", "")
                put("lead", "")
                put("body", null as String?)
                put("version", 5)
                put("sectionId", 4)
            },
            requests.single().body,
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
    fun chainActions() = runTest {
        api.submitArticle(42)
        api.approveArticle(42)
        api.rejectArticle(42, "Please add who scored.\nThanks")
        api.withdrawArticle(42)
        assertEquals(
            listOf(
                HttpMethod.Post to "https://news.example.org/api/articles/42/submit",
                HttpMethod.Post to "https://news.example.org/api/articles/42/approve",
                HttpMethod.Post to "https://news.example.org/api/articles/42/reject",
                HttpMethod.Post to "https://news.example.org/api/articles/42/withdraw",
            ),
            requests.map { it.method to it.url },
        )
        assertEquals(Json.parseToJsonElement("""{"note": "Please add who scored.\nThanks"}"""), requests[2].body)
        listOf(0, 1, 3).forEach { assertNull(requests[it].body) }
        requests.forEach { assertEquals("Bearer token-123", it.authorization) }
    }

    @Test
    fun reviews() = runTest {
        val review = api.reviews(42).single()
        assertEquals("REJECTED", review.decision)
        assertEquals("Too short", review.note)
        assertEquals("chief", review.reviewer.username)
        assertEquals(Recorded(HttpMethod.Get, "https://news.example.org/api/articles/42/reviews", "Bearer token-123", null), requests.single())
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
        assertEquals(
            AccountDto("9a1e", "lena", "Lena", "", listOf("EDITOR_IN_CHIEF"), true, allowedActions = listOf("RESET_PASSWORD", "LOCK")),
            list.accounts.single(),
        )
        assertEquals(Recorded(HttpMethod.Get, "https://news.example.org/api/accounts", "Bearer token-123", null), requests.single())
    }

    @Test
    fun resetPasswordPostsWithoutBodyAndReturnsThePassword() = runTest {
        val created = api.resetPassword("9a1e")
        assertEquals("tiger-wolke-apfel-leiter", created.password)
        assertEquals(listOf("RESET_PASSWORD", "LOCK"), created.account.allowedActions)
        assertEquals(
            Recorded(HttpMethod.Post, "https://news.example.org/api/accounts/9a1e/password-reset", "Bearer token-123", null),
            requests.single(),
        )
    }

    @Test
    fun lockAndUnlockPostWithoutBody() = runTest {
        assertEquals("lena", api.lock("9a1e").username)
        api.unlock("9a1e")
        assertEquals(
            listOf(
                Recorded(HttpMethod.Post, "https://news.example.org/api/accounts/9a1e/lock", "Bearer token-123", null),
                Recorded(HttpMethod.Post, "https://news.example.org/api/accounts/9a1e/unlock", "Bearer token-123", null),
            ),
            requests,
        )
    }

    @Test
    fun editRolesPutsBothListsAndReturnsTheAccount() = runTest {
        assertEquals("lena", api.editRoles("9a1e", EditRolesRequest(listOf("EDITOR_IN_CHIEF"), emptyList())).username)
        assertEquals(
            Recorded(
                HttpMethod.Put,
                "https://news.example.org/api/accounts/9a1e/roles",
                "Bearer token-123",
                buildJsonObject {
                    putJsonArray("roles") { add(JsonPrimitive("EDITOR_IN_CHIEF")) }
                    putJsonArray("sectionRoles") {}
                },
            ),
            requests.single(),
        )
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

    @Test
    fun createAccountWithSectionRolesOnly() = runTest {
        api.createAccount(CreateAccountRequest("Max", "", "max", emptyList(), listOf(SectionRoleDto(1, "REPORTER"))))
        assertEquals(
            buildJsonObject {
                put("firstName", "Max")
                put("lastName", "")
                put("username", "max")
                putJsonArray("roles") {}
                putJsonArray("sectionRoles") {
                    add(buildJsonObject {
                        put("sectionId", 1)
                        put("role", "REPORTER")
                    })
                }
            },
            requests.single().body,
        )
    }

    @Test
    fun listSections() = runTest {
        val list = api.sections()
        assertEquals(true, list.canManage)
        assertEquals("sport", list.sections.single().slug)
        assertEquals(true, list.sections.single().canWrite)
        assertEquals(Recorded(HttpMethod.Get, "https://news.example.org/api/sections", "Bearer token-123", null), requests.single())
    }

    @Test
    fun createSectionWithoutColourLeavesItOut() = runTest {
        assertEquals("green", api.createSection(SectionRequest("Sport")).color)
        val request = requests.single()
        assertEquals(HttpMethod.Post to "https://news.example.org/api/sections", request.method to request.url)
        assertEquals(buildJsonObject { put("name", "Sport") }, request.body)
        assertEquals("Bearer token-123", request.authorization)
    }

    @Test
    fun deleteSection() = runTest {
        api.deleteSection(7)
        assertEquals(
            listOf(Recorded(HttpMethod.Delete, "https://news.example.org/api/sections/7", "Bearer token-123", null)),
            requests,
        )
    }

    @Test
    fun updateAndReorderSections() = runTest {
        api.updateSection(1, SectionRequest("Sportnews", "blue"))
        api.reorderSections(listOf(2, 1))
        assertEquals(
            listOf(
                Recorded(
                    HttpMethod.Put,
                    "https://news.example.org/api/sections/1",
                    "Bearer token-123",
                    buildJsonObject {
                        put("name", "Sportnews")
                        put("color", "blue")
                    },
                ),
                Recorded(
                    HttpMethod.Put,
                    "https://news.example.org/api/sections/order",
                    "Bearer token-123",
                    buildJsonObject {
                        putJsonArray("ids") {
                            add(JsonPrimitive(2))
                            add(JsonPrimitive(1))
                        }
                    },
                ),
            ),
            requests,
        )
    }

    @Test
    fun members() = runTest {
        val list = api.members(1)
        assertEquals(listOf("SECTION_EDITOR", "REPORTER"), list.assignableRoles)
        assertEquals("nogroups", list.members.single().username)
        assertEquals("REPORTER", api.assignMember(1, "5f0c", "REPORTER").role)
        api.removeMember(1, "5f0c")
        assertEquals(
            listOf(
                Recorded(HttpMethod.Get, "https://news.example.org/api/sections/1/members", "Bearer token-123", null),
                Recorded(
                    HttpMethod.Put,
                    "https://news.example.org/api/sections/1/members/5f0c",
                    "Bearer token-123",
                    buildJsonObject { put("role", "REPORTER") },
                ),
                Recorded(HttpMethod.Delete, "https://news.example.org/api/sections/1/members/5f0c", "Bearer token-123", null),
            ),
            requests,
        )
    }

    private companion object {
        const val SECTION = """{ "id": 1, "name": "Sport", "slug": "sport", "color": "green", "position": 0,
            "assignableRoles": ["SECTION_EDITOR", "REPORTER"], "canWrite": true }"""
        const val MEMBER = """{ "accountId": "5f0c", "username": "nogroups", "firstName": "No", "lastName": "Groups",
            "role": "REPORTER" }"""
        const val ACCOUNT = """{ "id": "9a1e", "username": "lena", "firstName": "Lena", "lastName": "",
            "roles": ["EDITOR_IN_CHIEF"], "enabled": true, "allowedActions": ["RESET_PASSWORD", "LOCK"] }"""
        const val REVIEW = """{ "decision": "REJECTED", "level": "EDITOR_IN_CHIEF", "revision": 2,
            "reviewer": { "username": "chief", "displayName": "Chief" }, "note": "Too short", "createdAt": "2026-09-27T10:05:00Z" }"""
        const val SUMMARY = """{ "id": 42, "status": "DRAFT", "author": { "username": "papa", "displayName": "Papa" },
            "section": { "id": 1, "name": "Sport", "slug": "sport", "color": "green" },
            "headline": "H", "kicker": "", "revision": 1, "liveRevision": null, "hasUnpublishedChanges": false,
            "updatedAt": "2026-09-26T10:05:00Z", "publishedAt": null, "allowedActions": ["EDIT", "PUBLISH", "DELETE"] }"""
    }
}
