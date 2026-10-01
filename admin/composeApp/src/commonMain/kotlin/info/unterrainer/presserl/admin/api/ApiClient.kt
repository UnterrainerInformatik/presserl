package info.unterrainer.presserl.admin.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

val json = Json { ignoreUnknownKeys = true }

/** The backend gives LanguageTool 5 s; a little more covers the way there and back. */
private const val SPELL_CHECK_TIMEOUT_MILLIS = 8_000L

/** The connection check gives up in time on a slow mobile network instead of spinning forever (design D3). */
private const val CLIENT_CONFIG_TIMEOUT_MILLIS = 15_000L
private const val CLIENT_CONFIG_CONNECT_TIMEOUT_MILLIS = 10_000L

fun HttpClient.withJson(): HttpClient = config {
    expectSuccess = true
    install(ContentNegotiation) { json(json) }
    install(HttpTimeout)
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

    suspend fun clientConfig(): ClientConfigDto = http.get("$baseUrl/api/client-config") {
        timeout {
            requestTimeoutMillis = CLIENT_CONFIG_TIMEOUT_MILLIS
            connectTimeoutMillis = CLIENT_CONFIG_CONNECT_TIMEOUT_MILLIS
        }
    }.body()

    /**
     * Checks [text] for spelling, grammar and punctuation mistakes; `null` when the check is unavailable (`503`, any
     * other failed answer, network error or timeout), so callers never have to handle an exception.
     */
    suspend fun spellCheck(text: String): List<SpellMatchDto>? = try {
        http.post("$baseUrl/api/spell-check") {
            bearerAuth(accessToken())
            contentType(ContentType.Application.Json)
            setBody(SpellCheckRequestDto(text))
            timeout { requestTimeoutMillis = SPELL_CHECK_TIMEOUT_MILLIS }
        }.body<SpellCheckResponseDto>().matches
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        // Browser fetch failures surface as kotlin.Error, not Exception
        null
    }

    suspend fun newspaper(): NewspaperDto = http.get("$baseUrl/api/newspaper").body()

    /**
     * Sets the newspaper overrides in [changes] (setting name to JSON value: a string for enumerated settings, a
     * boolean for switches); JSON `null` removes the override. Answers the settings after the change.
     */
    suspend fun updateNewspaperSettings(changes: Map<String, JsonElement>): NewspaperDto =
        http.put("$baseUrl/api/newspaper/settings") {
            bearerAuth(accessToken())
            contentType(ContentType.Application.Json)
            setBody(JsonObject(changes))
        }.body()

    suspend fun me(): MeDto = http.get("$baseUrl/api/me") { bearerAuth(accessToken()) }.body()

    /** Requests the deletion of the user's own account; a repeated request keeps the first time. */
    suspend fun requestDeletion(): DeletionRequestDto =
        http.post("$baseUrl/api/me/deletion-request") { bearerAuth(accessToken()) }.body()

    /** Withdraws the user's pending deletion request. */
    suspend fun withdrawDeletionRequest(): DeletionRequestDto =
        http.delete("$baseUrl/api/me/deletion-request") { bearerAuth(accessToken()) }.body()

    /**
     * Articles in the order [sort] (`changed`, `newest` or `section`; the server's default, newest change first, when
     * `null`); [status] filters by status, [mine] to the user's own articles, [pending] to articles waiting for
     * approval, [awaitingMe] to those the user may approve now.
     */
    suspend fun articles(
        status: String? = null,
        mine: Boolean = false,
        pending: Boolean = false,
        awaitingMe: Boolean = false,
        sort: String? = null,
    ): List<ArticleSummaryDto> =
        http.get("$baseUrl/api/articles") {
            bearerAuth(accessToken())
            status?.let { parameter("status", it) }
            if (mine) parameter("mine", true)
            if (pending) parameter("pending", true)
            if (awaitingMe) parameter("awaitingMe", true)
            sort?.let { parameter("sort", it) }
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
            setBody(
                ArticleSave(content.kicker, content.headline, content.subheadline, content.lead, content.body, content.leadImage,
                    version, content.sectionId),
            )
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

    /** Approves up to the user's level; [version] is the article version the user saw (`409` if stale). */
    suspend fun approveArticle(id: Long, version: Long? = null): ArticleDto =
        http.post("$baseUrl/api/articles/$id/approve") {
            bearerAuth(accessToken())
            if (version != null) {
                contentType(ContentType.Application.Json)
                setBody(ApproveVersion(version))
            }
        }.body()

    /**
     * Ends the submission with [note] (`400` naming `note` when it is blank or too long); [version] is the article
     * version the user saw (`409` if stale).
     */
    suspend fun rejectArticle(id: Long, note: String, version: Long? = null): ArticleDto =
        http.post("$baseUrl/api/articles/$id/reject") {
            bearerAuth(accessToken())
            contentType(ContentType.Application.Json)
            setBody(RejectNote(note, version))
        }.body()

    suspend fun withdrawArticle(id: Long): ArticleDto =
        http.post("$baseUrl/api/articles/$id/withdraw") { bearerAuth(accessToken()) }.body()

    /**
     * Sets the front-page weight (1–999, `400` naming `weight` otherwise) or clears it with `null`; editors-in-chief
     * and publishers only (`403`). No new revision and no new version.
     */
    suspend fun setFrontPageWeight(id: Long, weight: Int?): ArticleDto =
        http.put("$baseUrl/api/articles/$id/front-page-weight") {
            bearerAuth(accessToken())
            contentType(ContentType.Application.Json)
            setBody(FrontPageWeightRequest(weight))
        }.body()

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

    /** Deletes the account (publishers only); its content stays with anonymised names. */
    suspend fun deleteAccount(accountId: String) {
        http.delete("$baseUrl/api/accounts/$accountId") { bearerAuth(accessToken()) }
    }

    /** Sets ([trusted]) or clears a trust entry of the account; answers the account as listed. */
    suspend fun setTrust(accountId: String, level: String, sectionId: Long?, trusted: Boolean): AccountDto =
        http.put("$baseUrl/api/accounts/$accountId/trust") {
            bearerAuth(accessToken())
            contentType(ContentType.Application.Json)
            setBody(SetTrustRequest(level, sectionId, trusted))
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

    /** All issues, highest number first. */
    suspend fun issues(): IssueListDto = http.get("$baseUrl/api/issues") { bearerAuth(accessToken()) }.body()

    suspend fun issue(id: Long): IssueDetailDto = http.get("$baseUrl/api/issues/$id") { bearerAuth(accessToken()) }.body()

    /** Creates the next issue (not published, no articles) with an optional [publicationDate] (`yyyy-mm-dd`). */
    suspend fun createIssue(publicationDate: String?): IssueDetailDto =
        http.post("$baseUrl/api/issues") {
            bearerAuth(accessToken())
            contentType(ContentType.Application.Json)
            setBody(IssueDateRequest(publicationDate))
        }.body()

    /** Sets the publication date (`yyyy-mm-dd`), or clears it with `null`. */
    suspend fun updateIssueDate(id: Long, publicationDate: String?): IssueDetailDto =
        http.put("$baseUrl/api/issues/$id") {
            bearerAuth(accessToken())
            contentType(ContentType.Application.Json)
            setBody(IssueDateRequest(publicationDate))
        }.body()

    suspend fun publishIssue(id: Long): IssueDetailDto =
        http.post("$baseUrl/api/issues/$id/publish") { bearerAuth(accessToken()) }.body()

    suspend fun unpublishIssue(id: Long): IssueDetailDto =
        http.post("$baseUrl/api/issues/$id/unpublish") { bearerAuth(accessToken()) }.body()

    /**
     * Makes [articleIds] the issue's articles in this order; listed articles of other issues move here, unlisted ones
     * of this issue belong to no issue afterwards (`400` naming `articleIds` for unknown or repeated ids).
     */
    suspend fun setIssueArticles(id: Long, articleIds: List<Long>): IssueDetailDto =
        http.put("$baseUrl/api/issues/$id/articles") {
            bearerAuth(accessToken())
            contentType(ContentType.Application.Json)
            setBody(IssueArticles(articleIds))
        }.body()

    /** Deletes an issue that is not published (`409` otherwise); its articles belong to no issue afterwards. */
    suspend fun deleteIssue(id: Long) {
        http.delete("$baseUrl/api/issues/$id") { bearerAuth(accessToken()) }
    }

    /**
     * Uploads an image as the multipart part `file`, with an optional [description] and [tags] (one `tag` part each).
     * The server detects the type from the bytes (JPEG, PNG, WebP), re-encodes it without metadata and answers the
     * stored image; `413` above `media.max-size`, `415` for other types, `400` for damaged or oversized images and for
     * an invalid description or tag (checked before the image).
     */
    suspend fun uploadMedia(
        bytes: ByteArray,
        fileName: String,
        description: String? = null,
        tags: List<String> = emptyList(),
    ): MediaDto =
        http.submitFormWithBinaryData(
            "$baseUrl/api/media",
            formData {
                append(
                    "file",
                    bytes,
                    Headers.build {
                        append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                        append(HttpHeaders.ContentDisposition, "filename=\"${fileName.quotable()}\"")
                    },
                )
                description?.takeIf { it.isNotBlank() }?.let { append("description", it) }
                tags.forEach { append("tag", it) }
            },
        ) { bearerAuth(accessToken()) }.body()

    /**
     * The newspaper's media matching [filter], newest first, [limit] per page; [before] is [MediaPage.next] of the
     * previous page (with the same filter).
     */
    suspend fun listMedia(limit: Int? = null, before: Long? = null, filter: MediaFilter = MediaFilter()): MediaPage =
        http.get("$baseUrl/api/media") {
            bearerAuth(accessToken())
            limit?.let { parameter("limit", it) }
            before?.let { parameter("before", it) }
            filter.tags.forEach { parameter("tag", it) }
            filter.q.trim().takeIf { it.isNotEmpty() }?.let { parameter("q", it) }
            if (filter.unused) parameter("unused", true)
            if (filter.mine) parameter("mine", true)
        }.body()

    /**
     * Replaces description and tags of the image; answers it with the stored (normalised) values. `400` naming
     * `description`, `tags` or `tags[i]` for invalid values.
     */
    suspend fun setMediaDetails(id: Long, request: MediaDetailsRequest): MediaDto =
        http.put("$baseUrl/api/media/$id/details") {
            bearerAuth(accessToken())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    /**
     * The newspaper's tags with their usage count, most used first; [prefix] matches the start of a tag or of one of
     * its words (all tags when blank), at most [limit].
     */
    suspend fun mediaTags(prefix: String = "", limit: Int? = null): List<MediaTagDto> =
        http.get("$baseUrl/api/media/tags") {
            bearerAuth(accessToken())
            prefix.trim().takeIf { it.isNotEmpty() }?.let { parameter("prefix", it) }
            limit?.let { parameter("limit", it) }
        }.body<MediaTagList>().items

    /** The articles using the image and whether the user may edit it. */
    suspend fun mediaUsage(id: Long): MediaUsageDto =
        http.get("$baseUrl/api/media/$id/usage") { bearerAuth(accessToken()) }.body()

    /**
     * Crops and/or pixelates the image permanently, replacing it under the same id; answers the edited media. `400`
     * for an invalid area, `403` when the user may not edit it, `409` when it changed since [EditMediaRequest.version],
     * `503` when the object store is unreachable (see [info.unterrainer.presserl.admin.ui.media.mediaEditErrorOf]).
     */
    suspend fun editMedia(id: Long, request: EditMediaRequest): MediaDto =
        http.post("$baseUrl/api/media/$id/edit") {
            bearerAuth(accessToken())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun media(id: Long): MediaDto = http.get("$baseUrl/api/media/$id") { bearerAuth(accessToken()) }.body()

    /** The stored image bytes (`image/jpeg` or `image/png`, see [MediaDto.contentType]). */
    suspend fun mediaContent(id: Long): ByteArray =
        http.get("$baseUrl/api/media/$id/content") { bearerAuth(accessToken()) }.body()

    /** The bytes of a rendition ([kind] `thumbnail`, `web` or `print`); `404` while it is not produced yet. */
    suspend fun mediaRendition(id: Long, kind: String): ByteArray =
        http.get("$baseUrl/api/media/$id/renditions/$kind") { bearerAuth(accessToken()) }.body()
}

/** A file name safe inside a quoted `Content-Disposition` parameter. */
private fun String.quotable(): String = filter { it >= ' ' && it != '"' && it != '\\' }.ifEmpty { "upload" }

/** Body of `POST /api/articles/{id}/reject`; a `null` [version] is left out. */
@Serializable
private data class RejectNote(val note: String, val version: Long? = null)

/** Body of `POST /api/articles/{id}/approve`. */
@Serializable
private data class ApproveVersion(val version: Long)

/** Body of `PUT /api/sections/order`. */
@Serializable
private data class SectionOrder(val ids: List<Long>)

/** Body of `PUT /api/issues/{id}/articles`. */
@Serializable
private data class IssueArticles(val articleIds: List<Long>)

/** Body of `PUT /api/sections/{id}/members/{accountId}`. */
@Serializable
private data class MemberRole(val role: String)

/**
 * Body of `PUT /api/articles/{id}`: [ArticleContent] plus the article version; a `null` [sectionId] is left out, a
 * `null` [leadImage] is sent (it removes the image).
 */
@Serializable
private data class ArticleSave(
    val kicker: String,
    val headline: String,
    val subheadline: String,
    val lead: String,
    val body: JsonObject?,
    val leadImage: LeadImageRequest?,
    val version: Long,
    val sectionId: Long? = null,
)
