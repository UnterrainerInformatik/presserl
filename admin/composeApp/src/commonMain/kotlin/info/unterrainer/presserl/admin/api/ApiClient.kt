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

    /** Saves [content] as a full replacement; [version] is the article version last received (`409` if stale). */
    suspend fun updateArticle(id: Long, content: ArticleContent, version: Long): ArticleDto =
        http.put("$baseUrl/api/articles/$id") {
            bearerAuth(accessToken())
            contentType(ContentType.Application.Json)
            setBody(ArticleSave(content.kicker, content.headline, content.subheadline, content.lead, content.body, version))
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
}

/** Body of `PUT /api/articles/{id}`: [ArticleContent] plus the article version. */
@Serializable
private data class ArticleSave(
    val kicker: String,
    val headline: String,
    val subheadline: String,
    val lead: String,
    val body: JsonObject?,
    val version: Long,
)
