package info.unterrainer.presserl.admin.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

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
}
