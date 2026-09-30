package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.api.AccountDto
import info.unterrainer.presserl.admin.api.ApiErrorDto
import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.api.ArticleContent
import info.unterrainer.presserl.admin.api.AuthorDto
import info.unterrainer.presserl.admin.api.CreateAccountRequest
import info.unterrainer.presserl.admin.api.CropRequest
import info.unterrainer.presserl.admin.api.EditMediaRequest
import info.unterrainer.presserl.admin.api.EllipseRequest
import info.unterrainer.presserl.admin.api.EditRolesRequest
import info.unterrainer.presserl.admin.api.LeadImageDto
import info.unterrainer.presserl.admin.api.LeadImageRequest
import info.unterrainer.presserl.admin.api.MediaDetailsRequest
import info.unterrainer.presserl.admin.api.MediaDto
import info.unterrainer.presserl.admin.api.MediaFilter
import info.unterrainer.presserl.admin.api.MediaTagDto
import info.unterrainer.presserl.admin.api.RenditionDto
import info.unterrainer.presserl.admin.api.SectionRequest
import info.unterrainer.presserl.admin.api.SectionRoleDto
import info.unterrainer.presserl.admin.ui.media.MediaEditError
import info.unterrainer.presserl.admin.ui.media.mediaEditErrorOf
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
                path.startsWith("/api/newspaper") -> """{ "name": "N", "subtitle": "", "visibility": "public",
                    "settings": { "reader.text-size": "m" }, "overrides": {} }"""
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
                path.endsWith("/lock") || path.endsWith("/unlock") || path.endsWith("/roles") || path.endsWith("/trust") -> ACCOUNT
                path == "/api/sections" && request.method == HttpMethod.Get || path == "/api/sections/order" ->
                    """{ "canManage": true, "sections": [$SECTION] }"""
                path.endsWith("/members") -> """{ "assignableRoles": ["SECTION_EDITOR", "REPORTER"], "members": [$MEMBER] }"""
                path.contains("/members/") -> MEMBER
                path.startsWith("/api/sections") -> SECTION
                path == "/api/issues" && request.method == HttpMethod.Get -> """{ "issues": [$ISSUE] }"""
                path.startsWith("/api/issues") -> ISSUE_DETAIL
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
    fun updateNewspaperSettingsPutsValuesAndExplicitNulls() = runTest {
        assertEquals("m", api.updateNewspaperSettings(mapOf("reader.text-size" to JsonNull)).settings.getValue("reader.text-size")
            .let { (it as JsonPrimitive).content })
        api.updateNewspaperSettings(mapOf("reader.text-size" to JsonPrimitive("l")))
        api.updateNewspaperSettings(mapOf("article.corrections" to JsonPrimitive(false)))

        assertEquals(
            listOf(
                Recorded(HttpMethod.Put, "https://news.example.org/api/newspaper/settings", "Bearer token-123",
                    buildJsonObject { put("reader.text-size", JsonNull) }),
                Recorded(HttpMethod.Put, "https://news.example.org/api/newspaper/settings", "Bearer token-123",
                    buildJsonObject { put("reader.text-size", "l") }),
                Recorded(HttpMethod.Put, "https://news.example.org/api/newspaper/settings", "Bearer token-123",
                    buildJsonObject { put("article.corrections", false) }),
            ),
            requests,
        )
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
    fun listArticlesAwaitingMe() = runTest {
        api.articles(awaitingMe = true)
        api.articles(pending = true)
        api.articles(mine = false, pending = false, awaitingMe = false)
        assertEquals(
            listOf(
                "https://news.example.org/api/articles?awaitingMe=true",
                "https://news.example.org/api/articles?pending=true",
                "https://news.example.org/api/articles",
            ),
            requests.map { it.url },
        )
    }

    @Test
    fun getArticle() = runTest {
        assertEquals("The pumpkin is huge", api.article(42).headline)
        assertEquals(HttpMethod.Get to "https://news.example.org/api/articles/42", requests.single().let { it.method to it.url })
    }

    @Test
    fun createArticleSendsOnlyTheGivenContentAndTheLeadImage() = runTest {
        api.createArticle(ArticleContent(headline = "Hello"))
        val request = requests.single()
        assertEquals(HttpMethod.Post to "https://news.example.org/api/articles", request.method to request.url)
        assertEquals(buildJsonObject { put("headline", "Hello"); put("leadImage", JsonNull) }, request.body)
        assertEquals("Bearer token-123", request.authorization)
    }

    @Test
    fun createArticleWithLeadImage() = runTest {
        api.createArticle(ArticleContent(headline = "Minka", leadImage = LeadImageRequest(17, "Our cat Minka")))
        assertEquals(
            buildJsonObject {
                put("headline", "Minka")
                put("leadImage", buildJsonObject { put("mediaId", 17); put("caption", "Our cat Minka") })
            },
            requests.single().body,
        )
    }

    @Test
    fun updateArticleSendsTheLeadImage() = runTest {
        api.updateArticle(42, ArticleContent(headline = "H", leadImage = LeadImageRequest(17, "")), version = 5)
        assertEquals(
            buildJsonObject { put("mediaId", 17); put("caption", "") },
            (requests.single().body as JsonObject)["leadImage"],
        )
    }

    @Test
    fun articleAndRevisionReadTheLeadImage() = runTest {
        val leadImage = LeadImageDto(17, "Our cat Minka", 4096, 2731)
        val withImage = ApiClient(
            HttpClient(MockEngine { request ->
                val body = if (request.url.encodedPath.contains("/revisions/")) {
                    """{ "number": 1, "headline": "H", "createdAt": "t", "updatedAt": "t", "live": true, "kicker": "",
                        "subheadline": "", "lead": "", "body": { "version": 1, "blocks": [] },
                        "leadImage": { "mediaId": 17, "caption": "Our cat Minka", "width": 4096, "height": 2731 } }"""
                } else {
                    ARTICLE.trimEnd().removeSuffix("}") +
                        """, "leadImage": { "mediaId": 17, "caption": "Our cat Minka", "width": 4096, "height": 2731 } }"""
                }
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }),
            baseUrl = "https://news.example.org",
        ) { "token-123" }

        assertEquals(leadImage, withImage.article(42).leadImage)
        assertEquals(leadImage, withImage.revision(42, 1).leadImage)
        assertNull(api.article(42).leadImage)
        assertNull(api.revision(42, 1).leadImage)
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
                put("leadImage", JsonNull)
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
                put("leadImage", JsonNull)
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
    fun setTrustPutsLevelSectionAndValue() = runTest {
        assertEquals("lena", api.setTrust("9a1e", "PUBLISHER", null, true).username)
        api.setTrust("9a1e", "SECTION_EDITOR", 3, false)
        assertEquals(
            listOf(
                Recorded(
                    HttpMethod.Put,
                    "https://news.example.org/api/accounts/9a1e/trust",
                    "Bearer token-123",
                    buildJsonObject {
                        put("level", "PUBLISHER")
                        put("sectionId", JsonNull)
                        put("trusted", true)
                    },
                ),
                Recorded(
                    HttpMethod.Put,
                    "https://news.example.org/api/accounts/9a1e/trust",
                    "Bearer token-123",
                    buildJsonObject {
                        put("level", "SECTION_EDITOR")
                        put("sectionId", 3)
                        put("trusted", false)
                    },
                ),
            ),
            requests,
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

    private class Sent(val method: HttpMethod, val url: String, val authorization: String?, val contentType: String?, val body: ByteArray)

    /** A client whose server answers every request with [status], [body] and [contentType], recording what was sent. */
    private fun mediaApi(
        sent: MutableList<Sent>,
        status: HttpStatusCode = HttpStatusCode.OK,
        body: ByteArray = MEDIA.encodeToByteArray(),
        contentType: String = "application/json",
    ) = ApiClient(
        HttpClient(MockEngine { request ->
            sent += Sent(
                request.method,
                request.url.toString(),
                request.headers[HttpHeaders.Authorization],
                request.body.contentType?.toString(),
                request.body.toByteArray(),
            )
            respond(body, status, headersOf(HttpHeaders.ContentType, contentType))
        }),
        baseUrl = "https://news.example.org",
    ) { "token-123" }

    @Test
    fun uploadMediaSendsOneFilePartNamedFile() = runTest {
        val sent = mutableListOf<Sent>()
        val image = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)

        val media = mediaApi(sent, HttpStatusCode.Created).uploadMedia(image, "photo.jpg")

        assertEquals(MediaDto(17, "image/jpeg", 4096, 2731, 1834211, AuthorDto("papa", "Papa"), "2026-09-27T14:03:11.402Z"), media)
        val request = sent.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://news.example.org/api/media", request.url)
        assertEquals("Bearer token-123", request.authorization)
        assertTrue(request.contentType!!.startsWith("multipart/form-data"), request.contentType)
        val text = request.body.decodeToString()
        assertEquals(1, Regex("Content-Disposition").findAll(text).count(), text)
        assertTrue(text.contains("form-data; name=\"file\""), text)
        assertTrue(text.contains("filename=\"photo.jpg\""), text)
        assertTrue(request.body.asList().windowed(image.size).any { it == image.asList() }, "image bytes are sent unchanged")
    }

    @Test
    fun uploadMediaKeepsTheFileNameQuotable() = runTest {
        val sent = mutableListOf<Sent>()

        mediaApi(sent, HttpStatusCode.Created).uploadMedia(byteArrayOf(1), "my \"best\"\\photo\n.png")

        assertTrue(sent.single().body.decodeToString().contains("filename=\"my bestphoto.png\""))
    }

    @Test
    fun mediaReadsTheMetadata() = runTest {
        val sent = mutableListOf<Sent>()

        assertEquals(2731, mediaApi(sent).media(17).height)
        assertEquals("https://news.example.org/api/media/17", sent.single().url)
        assertEquals("Bearer token-123", sent.single().authorization)
    }

    @Test
    fun mediaContentReturnsTheRawBytes() = runTest {
        val sent = mutableListOf<Sent>()
        val image = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())

        val content = mediaApi(sent, body = image, contentType = "image/jpeg").mediaContent(17)

        assertContentEquals(image, content)
        assertEquals("https://news.example.org/api/media/17/content", sent.single().url)
        assertEquals("Bearer token-123", sent.single().authorization)
    }

    @Test
    fun mediaReadsTheRenditions() = runTest {
        val withRenditions = MEDIA.trimEnd().removeSuffix("}") + """, "renditions": {
            "thumbnail": { "width": 480, "height": 320, "size": 31877 },
            "web": { "width": 1600, "height": 1067, "size": 298114 },
            "print": { "width": 3000, "height": 2000, "size": 861022 } } }"""

        val media = mediaApi(mutableListOf(), body = withRenditions.encodeToByteArray()).media(17)

        assertEquals(RenditionDto(480, 320, 31877), media.renditions["thumbnail"])
        assertEquals(RenditionDto(1600, 1067, 298114), media.renditions["web"])
        assertEquals(RenditionDto(3000, 2000, 861022), media.renditions["print"])
        assertEquals(emptyMap(), mediaApi(mutableListOf()).media(17).renditions)
    }

    @Test
    fun mediaRenditionDownloadsWithTheBearerToken() = runTest {
        val sent = mutableListOf<Sent>()
        val image = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())

        val content = mediaApi(sent, body = image, contentType = "image/jpeg").mediaRendition(17, "thumbnail")

        assertContentEquals(image, content)
        assertEquals(HttpMethod.Get, sent.single().method)
        assertEquals("https://news.example.org/api/media/17/renditions/thumbnail", sent.single().url)
        assertEquals("Bearer token-123", sent.single().authorization)
    }

    @Test
    fun uploadMediaPropagatesRefusals() = runTest {
        val error = """{ "errors": [{ "field": "file", "message": "only JPEG, PNG and WebP images are accepted" }] }"""
        val api = mediaApi(mutableListOf(), HttpStatusCode.UnsupportedMediaType, error.encodeToByteArray())

        val refused = assertFailsWith<ClientRequestException> { api.uploadMedia(byteArrayOf(1), "cat.jpg") }

        assertEquals(HttpStatusCode.UnsupportedMediaType, refused.response.status)
        assertEquals("file", refused.response.body<ApiErrorDto>().errors.single().field)
    }

    @Test
    fun listMediaPagesWithLimitAndBefore() = runTest {
        val sent = mutableListOf<Sent>()
        val page = """{ "items": [{ "id": 122, "version": 2, "contentType": "image/jpeg", "width": 4096, "height": 2731,
            "size": 1834211, "uploadedBy": { "username": "anna", "displayName": "Anna" }, "uploadedAt": "2026-09-27T14:03:11.402Z",
            "renditions": { "thumbnail": { "width": 480, "height": 320, "size": 31877 } }, "usageCount": 2 }], "next": 63 }"""

        val result = mediaApi(sent, body = page.encodeToByteArray()).listMedia(60, 123)

        assertEquals("https://news.example.org/api/media?limit=60&before=123", sent.single().url)
        assertEquals("Bearer token-123", sent.single().authorization)
        assertEquals(63, result.next)
        assertEquals(2, result.items.single().version)
        assertEquals(2, result.items.single().usageCount)
        mediaApi(sent, body = """{ "items": [], "next": null }""".encodeToByteArray()).listMedia()
        assertEquals("https://news.example.org/api/media", sent.last().url)
    }

    @Test
    fun uploadMediaSendsDescriptionAndOneTagPartPerTag() = runTest {
        val sent = mutableListOf<Sent>()

        mediaApi(sent, HttpStatusCode.Created).uploadMedia(byteArrayOf(1), "photo.jpg", "Foto: Anna", listOf("Sportfest", "Schule"))

        val text = sent.single().body.decodeToString()
        assertEquals(4, Regex("Content-Disposition").findAll(text).count(), text)
        assertTrue(Regex("name=\"?description\"?\r\n(.*\r\n)*\r\nFoto: Anna\r\n").containsMatchIn(text), text)
        val tags = Regex("name=\"?tag\"?\r\n(?:.*\r\n)*?\r\n(.*)\r\n").findAll(text).map { it.groupValues[1] }.toList()
        assertEquals(listOf("Sportfest", "Schule"), tags)
    }

    @Test
    fun uploadMediaLeavesOutABlankDescription() = runTest {
        val sent = mutableListOf<Sent>()

        mediaApi(sent, HttpStatusCode.Created).uploadMedia(byteArrayOf(1), "photo.jpg", "  ")

        assertEquals(1, Regex("Content-Disposition").findAll(sent.single().body.decodeToString()).count())
    }

    @Test
    fun mediaReadsDescriptionAndTags() = runTest {
        val withDetails = MEDIA.trimEnd().removeSuffix("}") + """, "description": "Foto: Anna", "tags": ["Einsatz", "Feuerwehr"] }"""

        val media = mediaApi(mutableListOf(), body = withDetails.encodeToByteArray()).media(17)

        assertEquals("Foto: Anna", media.description)
        assertEquals(listOf("Einsatz", "Feuerwehr"), media.tags)
        assertNull(mediaApi(mutableListOf()).media(17).description)
        assertEquals(emptyList(), mediaApi(mutableListOf()).media(17).tags)
    }

    @Test
    fun listMediaSendsTheFilters() = runTest {
        val sent = mutableListOf<Sent>()
        val empty = """{ "items": [], "next": null }""".encodeToByteArray()

        mediaApi(sent, body = empty).listMedia(
            60, 123, MediaFilter(listOf("Feuerwehr", "Freiwillige Feuerwehr"), " dorfplatz anna ", unused = true, mine = true),
        )
        mediaApi(sent, body = empty).listMedia(filter = MediaFilter(q = "  "))

        assertEquals(
            "https://news.example.org/api/media?limit=60&before=123&tag=Feuerwehr&tag=Freiwillige+Feuerwehr&q=dorfplatz+anna" +
                "&unused=true&mine=true",
            sent.first().url,
        )
        assertEquals("https://news.example.org/api/media", sent.last().url)
    }

    @Test
    fun setMediaDetailsSendsBothFields() = runTest {
        val sent = mutableListOf<Sent>()

        mediaApi(sent).setMediaDetails(17, MediaDetailsRequest(null, listOf("feuerwehr")))

        val request = sent.single()
        assertEquals(HttpMethod.Put, request.method)
        assertEquals("https://news.example.org/api/media/17/details", request.url)
        assertEquals("Bearer token-123", request.authorization)
        assertEquals(
            Json.parseToJsonElement("""{ "description": null, "tags": ["feuerwehr"] }"""),
            Json.parseToJsonElement(request.body.decodeToString()),
        )
    }

    @Test
    fun mediaTagsSendsPrefixAndLimit() = runTest {
        val sent = mutableListOf<Sent>()
        val tags = """{ "items": [{ "name": "Feuerwehr", "count": 12 }, { "name": "Freiwillige Feuerwehr", "count": 3 }] }"""

        val result = mediaApi(sent, body = tags.encodeToByteArray()).mediaTags(" feu ", 20)
        mediaApi(sent, body = tags.encodeToByteArray()).mediaTags()

        assertEquals(listOf(MediaTagDto("Feuerwehr", 12), MediaTagDto("Freiwillige Feuerwehr", 3)), result)
        assertEquals("https://news.example.org/api/media/tags?prefix=feu&limit=20", sent.first().url)
        assertEquals("Bearer token-123", sent.first().authorization)
        assertEquals("https://news.example.org/api/media/tags", sent.last().url)
    }

    @Test
    fun mediaUsageReadsFlags() = runTest {
        val sent = mutableListOf<Sent>()
        val usage = """{ "mayEdit": false, "articles": [{ "id": 5, "headline": "Our cat Minka",
            "section": { "id": 2, "name": "Tiere", "slug": "tiere", "color": "orange" },
            "author": { "username": "anna", "displayName": "Anna" }, "status": "PUBLISHED", "pendingLevel": null,
            "publishedAt": "2026-09-27T15:00:00Z", "updatedAt": "2026-09-28T08:12:00Z",
            "live": true, "latest": false, "older": false }] }"""

        val result = mediaApi(sent, body = usage.encodeToByteArray()).mediaUsage(17)

        assertEquals("https://news.example.org/api/media/17/usage", sent.single().url)
        assertEquals(false, result.mayEdit)
        val use = result.articles.single()
        assertEquals("orange", use.section?.color)
        assertTrue(use.live)
        assertEquals("2026-09-27T15:00:00Z", use.publishedAt)
    }

    @Test
    fun editMediaSendsVersionCropAndEllipses() = runTest {
        val sent = mutableListOf<Sent>()

        val media = mediaApi(sent, body = MEDIA.replace("\"id\": 17", "\"id\": 17, \"version\": 1").encodeToByteArray())
            .editMedia(17, EditMediaRequest(0, CropRequest(100, 50, 1200, 800), listOf(EllipseRequest(600, 400, 80, 110))))

        assertEquals(1, media.version)
        val request = sent.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://news.example.org/api/media/17/edit", request.url)
        assertEquals("Bearer token-123", request.authorization)
        assertEquals(
            Json.parseToJsonElement("""{ "version": 0, "crop": { "x": 100, "y": 50, "width": 1200, "height": 800 },
                "pixelate": [{ "cx": 600, "cy": 400, "rx": 80, "ry": 110 }] }"""),
            Json.parseToJsonElement(request.body.decodeToString()),
        )
        mediaApi(sent).editMedia(17, EditMediaRequest(3, pixelate = listOf(EllipseRequest(1, 2, 5, 6))))
        assertEquals(
            Json.parseToJsonElement("""{ "version": 3, "pixelate": [{ "cx": 1, "cy": 2, "rx": 5, "ry": 6 }] }"""),
            Json.parseToJsonElement(sent.last().body.decodeToString()),
        )
    }

    @Test
    fun editMediaErrorsAreMapped() = runTest {
        val error = """{ "errors": [{ "field": "version", "message": "media 17 was changed meanwhile" }] }""".encodeToByteArray()
        val cases = mapOf(
            HttpStatusCode.Conflict to MediaEditError.Conflict,
            HttpStatusCode.Forbidden to MediaEditError.Forbidden,
            HttpStatusCode.BadRequest to MediaEditError.Invalid,
            HttpStatusCode.ServiceUnavailable to MediaEditError.Unreachable,
            HttpStatusCode.NotFound to MediaEditError.Other("404 Not Found"),
        )
        for ((status, expected) in cases) {
            val failure = runCatching { mediaApi(mutableListOf(), status, error).editMedia(17, EditMediaRequest(0)) }.exceptionOrNull()!!
            assertEquals(expected, mediaEditErrorOf(failure), status.toString())
        }
        assertEquals(MediaEditError.Unreachable, mediaEditErrorOf(IllegalStateException("fetch failed")))
    }

    @Test
    fun listIssues() = runTest {
        val issue = api.issues().issues.single()
        assertEquals(4, issue.number)
        assertEquals("2026-10-12", issue.publicationDate)
        assertTrue(issue.newest)
        assertEquals(Recorded(HttpMethod.Get, "https://news.example.org/api/issues", "Bearer token-123", null), requests.single())
    }

    @Test
    fun getIssueWithArticlesInOrder() = runTest {
        val issue = api.issue(4)
        assertEquals(listOf(9L, 4L), issue.articles.map { it.id })
        assertEquals(4, issue.articles.first().issue?.number)
        assertEquals("https://news.example.org/api/issues/4", requests.single().url)
    }

    @Test
    fun createIssueSendsTheDateOrAnExplicitNull() = runTest {
        api.createIssue(null)
        api.createIssue("2026-10-12")
        assertEquals(
            listOf(
                Recorded(HttpMethod.Post, "https://news.example.org/api/issues", "Bearer token-123",
                    buildJsonObject { put("publicationDate", JsonNull) }),
                Recorded(HttpMethod.Post, "https://news.example.org/api/issues", "Bearer token-123",
                    buildJsonObject { put("publicationDate", "2026-10-12") }),
            ),
            requests,
        )
    }

    @Test
    fun updateIssueDateClearsWithAnExplicitNull() = runTest {
        api.updateIssueDate(4, null)
        assertEquals(
            Recorded(HttpMethod.Put, "https://news.example.org/api/issues/4", "Bearer token-123",
                buildJsonObject { put("publicationDate", JsonNull) }),
            requests.single(),
        )
    }

    @Test
    fun issueActions() = runTest {
        api.publishIssue(4)
        api.unpublishIssue(4)
        api.setIssueArticles(4, listOf(9, 4, 6))
        api.deleteIssue(4)
        assertEquals(
            listOf(
                Recorded(HttpMethod.Post, "https://news.example.org/api/issues/4/publish", "Bearer token-123", null),
                Recorded(HttpMethod.Post, "https://news.example.org/api/issues/4/unpublish", "Bearer token-123", null),
                Recorded(HttpMethod.Put, "https://news.example.org/api/issues/4/articles", "Bearer token-123",
                    buildJsonObject { putJsonArray("articleIds") { add(JsonPrimitive(9)); add(JsonPrimitive(4)); add(JsonPrimitive(6)) } }),
                Recorded(HttpMethod.Delete, "https://news.example.org/api/issues/4", "Bearer token-123", null),
            ),
            requests,
        )
    }

    @Test
    fun listArticlesSorted() = runTest {
        api.articles(mine = true, sort = "newest")
        api.articles(sort = "section")
        assertEquals(
            listOf(
                "https://news.example.org/api/articles?mine=true&sort=newest",
                "https://news.example.org/api/articles?sort=section",
            ),
            requests.map { it.url },
        )
    }

    @Test
    fun approveAndRejectCarryTheVersion() = runTest {
        api.approveArticle(42, version = 7)
        api.rejectArticle(42, "Too short", version = 8)
        assertEquals(Json.parseToJsonElement("""{"version": 7}"""), requests[0].body)
        assertEquals(Json.parseToJsonElement("""{"note": "Too short", "version": 8}"""), requests[1].body)
    }

    @Test
    fun accountRequestsCarryTheMarkerOnlyWhenGiven() = runTest {
        api.createAccount(CreateAccountRequest("Pia", "", "pia", emptyList(), sectionlessReporter = true))
        api.editRoles("9a1e", EditRolesRequest(listOf("READER"), emptyList()))
        api.editRoles("9a1e", EditRolesRequest(emptyList(), emptyList(), sectionlessReporter = false))
        assertEquals(
            Json.parseToJsonElement("""{"firstName": "Pia", "lastName": "", "username": "pia", "roles": [], "sectionlessReporter": true}"""),
            requests[0].body,
        )
        assertEquals(Json.parseToJsonElement("""{"roles": ["READER"], "sectionRoles": []}"""), requests[1].body)
        assertEquals(
            Json.parseToJsonElement("""{"roles": [], "sectionRoles": [], "sectionlessReporter": false}"""),
            requests[2].body,
        )
    }

    private companion object {
        const val ISSUE = """{ "id": 4, "number": 4, "publicationDate": "2026-10-12", "published": false,
            "publishedAt": null, "articleCount": 2, "newest": true }"""
        const val ISSUE_ARTICLE = """{ "id": %d, "status": "PUBLISHED", "author": { "username": "papa", "displayName": "Papa" },
            "section": { "id": 1, "name": "Sport", "slug": "sport", "color": "green" }, "issue": { "id": 4, "number": 4 },
            "headline": "H", "kicker": "", "revision": 1, "liveRevision": 1, "hasUnpublishedChanges": false,
            "updatedAt": "2026-09-26T10:05:00Z", "publishedAt": "2026-09-26T10:05:00Z", "allowedActions": [] }"""
        val ISSUE_DETAIL = """{ "id": 4, "number": 4, "publicationDate": null, "published": false, "publishedAt": null,
            "articleCount": 2, "newest": true, "articles": [${ISSUE_ARTICLE.replace("%d", "9")}, ${ISSUE_ARTICLE.replace("%d", "4")}] }"""
        const val MEDIA = """{ "id": 17, "contentType": "image/jpeg", "width": 4096, "height": 2731, "size": 1834211,
            "uploadedBy": { "username": "papa", "displayName": "Papa" }, "uploadedAt": "2026-09-27T14:03:11.402Z" }"""
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
