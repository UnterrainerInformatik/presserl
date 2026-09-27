package info.unterrainer.presserl.admin.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

val json = Json { ignoreUnknownKeys = true }

fun HttpClient.withJson(): HttpClient = config {
    expectSuccess = true
    install(ContentNegotiation) { json(json) }
}

/**
 * Client for the Presserl REST API. [accessToken] supplies a valid bearer token for
 * authenticated endpoints.
 */
class ApiClient(
    http: HttpClient,
    private val baseUrl: String,
    private val accessToken: suspend () -> String,
) {
    private val http = http.withJson()

    suspend fun clientConfig(): ClientConfigDto = http.get("$baseUrl/api/client-config").body()

    suspend fun newspaper(): NewspaperDto = http.get("$baseUrl/api/newspaper").body()

    suspend fun me(): MeDto = http.get("$baseUrl/api/me") { bearerAuth(accessToken()) }.body()

    /** Articles, newest change first; [status] filters by status, [mine] to the user's own articles. */
    suspend fun articles(status: String? = null, mine: Boolean = false): List<ArticleSummaryDto> =
        http.get("$baseUrl/api/articles") {
            bearerAuth(accessToken())
            status?.let { parameter("status", it) }
            if (mine) parameter("mine", true)
        }.body()

    suspend fun article(id: Long): ArticleDto = http.get("$baseUrl/api/articles/$id") { bearerAuth(accessToken()) }.body()

    suspend fun createArticle(content: ArticleContent = ArticleContent()): ArticleDto =
        http.post("$baseUrl/api/articles") {
            bearerAuth(accessToken())
            contentType(ContentType.Application.Json)
            setBody(content)
        }.body()

    /**
     * Saves [content] as a full replacement, moving the article to [ArticleContent.sectionId] if given; [version] is
     * the article version last received (`409` if stale).
     */
    suspend fun updateArticle(id: Long, content: ArticleContent, version: Long): ArticleDto =
        http.put("$baseUrl/api/articles/$id") {
            bearerAuth(accessToken())
            contentType(ContentType.Application.Json)
            setBody(ArticleSave(content.kicker, content.headline, content.subheadline, content.lead, content.body, version, content.sectionId))
        }.body()

    suspend fun deleteArticle(id: Long) {
        http.delete("$baseUrl/api/articles/$id") { bearerAuth(accessToken()) }
    }

    suspend fun publishArticle(id: Long): ArticleDto =
        http.post("$baseUrl/api/articles/$id/publish") { bearerAuth(accessToken()) }.body()

    suspend fun takeArticleOffline(id: Long): ArticleDto =
        http.post("$baseUrl/api/articles/$id/offline") { bearerAuth(accessToken()) }.body()

    suspend fun revisions(articleId: Long): List<RevisionSummaryDto> =
        http.get("$baseUrl/api/articles/$articleId/revisions") { bearerAuth(accessToken()) }.body()

    suspend fun revision(articleId: Long, number: Int): RevisionDto =
        http.get("$baseUrl/api/articles/$articleId/revisions/$number") { bearerAuth(accessToken()) }.body()

    /** All accounts, sorted by username, with the roles the user may assign. */
    suspend fun accounts(): AccountListDto = http.get("$baseUrl/api/accounts") { bearerAuth(accessToken()) }.body()

    /** A free username derived from [firstName]. */
    suspend fun usernameSuggestion(firstName: String): String =
        http.get("$baseUrl/api/accounts/username-suggestion") {
            bearerAuth(accessToken())
            parameter("firstName", firstName)
        }.body<UsernameSuggestionDto>().username

    /** Creates an account; the response carries its generated password, which is not available later. */
    suspend fun createAccount(request: CreateAccountRequest): CreatedAccountDto =
        http.post("$baseUrl/api/accounts") {
            bearerAuth(accessToken())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    /** All sections by position, with what the user may do. */
    suspend fun sections(): SectionListDto = http.get("$baseUrl/api/sections") { bearerAuth(accessToken()) }.body()

    suspend fun createSection(request: SectionRequest): SectionDto =
        http.post("$baseUrl/api/sections") {
            bearerAuth(accessToken())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    /** Replaces name and colour; both are required. */
    suspend fun updateSection(id: Long, request: SectionRequest): SectionDto =
        http.put("$baseUrl/api/sections/$id") {
            bearerAuth(accessToken())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    /** Sets the order; [ids] must name every section once. */
    suspend fun reorderSections(ids: List<Long>): SectionListDto =
        http.put("$baseUrl/api/sections/order") {
            bearerAuth(accessToken())
            contentType(ContentType.Application.Json)
            setBody(SectionOrder(ids))
        }.body()

    suspend fun members(sectionId: Long): MemberListDto =
        http.get("$baseUrl/api/sections/$sectionId/members") { bearerAuth(accessToken()) }.body()

    /** Gives the account [role] in the section, replacing its current one. */
    suspend fun assignMember(sectionId: Long, accountId: String, role: String): MemberDto =
        http.put("$baseUrl/api/sections/$sectionId/members/$accountId") {
            bearerAuth(accessToken())
            contentType(ContentType.Application.Json)
            setBody(MemberRole(role))
        }.body()

    suspend fun removeMember(sectionId: Long, accountId: String) {
        http.delete("$baseUrl/api/sections/$sectionId/members/$accountId") { bearerAuth(accessToken()) }
    }
}

/** Body of `PUT /api/sections/order`. */
@Serializable
private data class SectionOrder(val ids: List<Long>)

/** Body of `PUT /api/sections/{id}/members/{accountId}`. */
@Serializable
private data class MemberRole(val role: String)

/** Body of `PUT /api/articles/{id}`: [ArticleContent] plus the article version; a `null` [sectionId] is left out. */
@Serializable
private data class ArticleSave(
    val kicker: String,
    val headline: String,
    val subheadline: String,
    val lead: String,
    val body: JsonObject?,
    val version: Long,
    val sectionId: Long? = null,
)
