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

    /**
     * Articles, newest change first; [status] filters by status, [mine] to the user's own articles,
     * [pending] to articles waiting for approval, [awaitingMe] to those the user may approve now.
     */
    suspend fun articles(
        status: String? = null,
        mine: Boolean = false,
        pending: Boolean = false,
        awaitingMe: Boolean = false,
    ): List<ArticleSummaryDto> =
        http.get("$baseUrl/api/articles") {
            bearerAuth(accessToken())
            status?.let { parameter("status", it) }
            if (mine) parameter("mine", true)
            if (pending) parameter("pending", true)
            if (awaitingMe) parameter("awaitingMe", true)
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

    /** Lifts the emergency-brake lock (publishers only); the article stays offline. */
    suspend fun unlockArticle(id: Long): ArticleDto =
        http.post("$baseUrl/api/articles/$id/unlock") { bearerAuth(accessToken()) }.body()

    /** Starts a submission; the article then waits for the lowest level of the author's chain. */
    suspend fun submitArticle(id: Long): ArticleDto =
        http.post("$baseUrl/api/articles/$id/submit") { bearerAuth(accessToken()) }.body()

    suspend fun approveArticle(id: Long): ArticleDto =
        http.post("$baseUrl/api/articles/$id/approve") { bearerAuth(accessToken()) }.body()

    /** Ends the submission with [note] (`400` naming `note` when it is blank or too long). */
    suspend fun rejectArticle(id: Long, note: String): ArticleDto =
        http.post("$baseUrl/api/articles/$id/reject") {
            bearerAuth(accessToken())
            contentType(ContentType.Application.Json)
            setBody(RejectNote(note))
        }.body()

    suspend fun withdrawArticle(id: Long): ArticleDto =
        http.post("$baseUrl/api/articles/$id/withdraw") { bearerAuth(accessToken()) }.body()

    /** Approvals and rejections of the article, newest first. */
    suspend fun reviews(articleId: Long): List<ReviewDto> =
        http.get("$baseUrl/api/articles/$articleId/reviews") { bearerAuth(accessToken()) }.body()

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

    /** Replaces the account's newspaper and section roles with the complete [request]; answers the account as listed. */
    suspend fun editRoles(accountId: String, request: EditRolesRequest): AccountDto =
        http.put("$baseUrl/api/accounts/$accountId/roles") {
            bearerAuth(accessToken())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    /** Sets a new generated password and ends the account's sessions; the response carries the password once. */
    suspend fun resetPassword(accountId: String): CreatedAccountDto =
        http.post("$baseUrl/api/accounts/$accountId/password-reset") { bearerAuth(accessToken()) }.body()

    /** Disables the account and ends its sessions (publishers only). */
    suspend fun lock(accountId: String): AccountDto =
        http.post("$baseUrl/api/accounts/$accountId/lock") { bearerAuth(accessToken()) }.body()

    suspend fun unlock(accountId: String): AccountDto =
        http.post("$baseUrl/api/accounts/$accountId/unlock") { bearerAuth(accessToken()) }.body()

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

    /** Deletes an empty section with its roles; `409` while articles belong to it. */
    suspend fun deleteSection(id: Long) {
        http.delete("$baseUrl/api/sections/$id") { bearerAuth(accessToken()) }
    }

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

/** Body of `POST /api/articles/{id}/reject`. */
@Serializable
private data class RejectNote(val note: String)

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
