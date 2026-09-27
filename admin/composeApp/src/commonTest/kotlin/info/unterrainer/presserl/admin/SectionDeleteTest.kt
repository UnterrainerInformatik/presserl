package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.ui.section.SectionDeleteResult
import info.unterrainer.presserl.admin.ui.section.deleteSection
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SectionDeleteTest {

    private fun api(status: HttpStatusCode, body: String = "") = ApiClient(
        HttpClient(MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) }),
        baseUrl = "https://news.example.org",
    ) { "token-123" }

    @Test
    fun noContentIsDeleted() = runTest {
        val api = api(HttpStatusCode.NoContent)
        assertEquals(SectionDeleteResult.Deleted, deleteSection { api.deleteSection(1) })
    }

    @Test
    fun conflictMeansTheSectionStillHasArticles() = runTest {
        val api = api(
            HttpStatusCode.Conflict,
            """{ "errors": [{ "field": null, "message": "section still contains 2 article(s); move them to another section first" }] }""",
        )
        assertEquals(SectionDeleteResult.NotEmpty, deleteSection { api.deleteSection(1) })
    }

    @Test
    fun otherRefusalsShowTheErrorText() = runTest {
        val api = api(HttpStatusCode.Forbidden)
        val result = deleteSection { api.deleteSection(1) }
        assertEquals(true, result is SectionDeleteResult.Failed && result.message.contains("403"), "$result")
    }

    @Test
    fun otherFailuresShowTheirMessage() = runTest {
        assertEquals(SectionDeleteResult.Failed("offline"), deleteSection { throw IllegalStateException("offline") })
    }
}
