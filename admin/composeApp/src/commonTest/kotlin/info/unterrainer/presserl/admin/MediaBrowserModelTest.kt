package info.unterrainer.presserl.admin

import androidx.compose.ui.graphics.ImageBitmap
import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.api.AuthorDto
import info.unterrainer.presserl.admin.api.MediaDto
import info.unterrainer.presserl.admin.api.MediaFilter
import info.unterrainer.presserl.admin.api.MediaListItemDto
import info.unterrainer.presserl.admin.api.MediaPage
import info.unterrainer.presserl.admin.api.MediaUsageDto
import info.unterrainer.presserl.admin.api.MediaUseDto
import info.unterrainer.presserl.admin.api.json
import info.unterrainer.presserl.admin.ui.media.EditImpact
import info.unterrainer.presserl.admin.ui.media.MediaGridModel
import info.unterrainer.presserl.admin.ui.media.MediaSearch
import info.unterrainer.presserl.admin.ui.media.Thumbnails
import info.unterrainer.presserl.admin.ui.media.UsagePlace
import info.unterrainer.presserl.admin.ui.media.editImpact
import info.unterrainer.presserl.admin.ui.media.formatBytes
import info.unterrainer.presserl.admin.ui.media.mediaGridOf
import info.unterrainer.presserl.admin.ui.media.mayEdit
import info.unterrainer.presserl.admin.ui.media.usagePlaces
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaBrowserModelTest {

    private fun item(id: Long, usageCount: Long = 0) =
        MediaListItemDto(id, 0, "image/jpeg", 1600, 1067, 1000, AuthorDto("anna", "Anna"), "2026-09-27T14:03:11Z",
            usageCount = usageCount)

    private fun use(live: Boolean, latest: Boolean, older: Boolean, status: String = "PUBLISHED") =
        MediaUseDto(5, "Minka", null, AuthorDto("anna", "Anna"), status, null, null, "2026-09-28T08:12:00Z", live, latest, older)

    @Test
    fun gridLoadsTheNextPageAtTheEnd() = runTest {
        val requested = mutableListOf<Long?>()
        val grid = MediaGridModel { _, before ->
            requested += before
            if (before == null) MediaPage((75L downTo 16L).map { item(it) }, next = 16) else MediaPage((15L downTo 1L).map { item(it) }, null)
        }
        assertTrue(grid.hasMore)

        grid.loadMore()
        assertEquals(60, grid.items.size)
        assertTrue(grid.hasMore)

        grid.loadMore()
        assertEquals(75, grid.items.size)
        assertEquals(listOf(75L, 1L), listOf(grid.items.first().id, grid.items.last().id))
        assertFalse(grid.hasMore)

        grid.loadMore()
        assertEquals(listOf(null, 16L), requested)
    }

    @Test
    fun pickerGridStartsUnfilteredAndPagesWithNext() = runTest {
        val requested = mutableListOf<String>()
        val api = ApiClient(
            HttpClient(MockEngine { request ->
                requested += request.url.encodedPathAndQuery
                val page = if (request.url.parameters["before"] == null) MediaPage(listOf(item(3), item(2)), next = 2)
                else MediaPage(listOf(item(1)), null)
                respond(json.encodeToString(page), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }),
            "https://news.example.org",
        ) { "token" }
        // an earlier opening searched; a new one starts without filters
        mediaGridOf(api).setFilter(MediaFilter(tags = listOf("Sportfest"), unused = true))
        requested.clear()
        val search = MediaSearch(mediaGridOf(api))
        assertEquals(MediaFilter(), search.grid.filter)
        assertEquals("", search.query)
        assertEquals(emptyList(), search.tags.tags)

        search.grid.loadMore()
        search.grid.loadMore()

        assertEquals(listOf("/api/media?limit=60", "/api/media?limit=60&before=2"), requested)
        assertEquals(listOf(3L, 2L, 1L), search.grid.items.map { it.id })
        assertFalse(search.grid.hasMore)
    }

    @Test
    fun gridKeepsItsPagesWhenAPageFails() = runTest {
        var fail = false
        val grid = MediaGridModel { _, _ -> if (fail) error("offline") else MediaPage(listOf(item(3), item(2)), next = 2) }
        grid.loadMore()
        fail = true

        grid.loadMore()

        assertEquals(2, grid.items.size)
        assertEquals("offline", grid.error)
        assertTrue(grid.hasMore)
    }

    @Test
    fun gridShowsAnEditedMediaInPlace() = runTest {
        val grid = MediaGridModel { _, _ -> MediaPage(listOf(item(3, usageCount = 2), item(2)), null) }
        grid.loadMore()

        grid.replace(MediaDto(3, "image/jpeg", 1200, 800, 900, AuthorDto("anna", "Anna"), "t", version = 1))

        assertEquals(1, grid.items[0].version)
        assertEquals(1200, grid.items[0].width)
        assertEquals(2, grid.items[0].usageCount)
        assertEquals(0, grid.items[1].version)
    }

    @Test
    fun gridShowsSavedDetailsInPlace() = runTest {
        val grid = MediaGridModel { _, _ -> MediaPage(listOf(item(3, usageCount = 2)), null) }
        grid.loadMore()

        grid.replace(MediaDto(3, "image/jpeg", 1600, 1067, 1000, AuthorDto("anna", "Anna"), "t", description = "Foto: Anna",
            tags = listOf("Feuerwehr")))

        assertEquals("Foto: Anna", grid.items[0].description)
        assertEquals(listOf("Feuerwehr"), grid.items[0].tags)
        assertEquals(2, grid.items[0].usageCount)
    }

    @Test
    fun filterReloadsFromTheFirstPage() = runTest {
        val requested = mutableListOf<Pair<MediaFilter, Long?>>()
        val grid = MediaGridModel { filter, before ->
            requested += filter to before
            if (filter.tags.isEmpty()) MediaPage(listOf(item(9), item(8)), next = 8) else MediaPage(listOf(item(5)), null)
        }
        grid.loadMore()
        val feuerwehr = MediaFilter(tags = listOf("Feuerwehr"), unused = true)

        grid.setFilter(feuerwehr)

        assertEquals(listOf(5L), grid.items.map { it.id })
        assertEquals(feuerwehr, grid.filter)
        assertFalse(grid.hasMore)
        assertEquals(listOf<Pair<MediaFilter, Long?>>(MediaFilter() to null, feuerwehr to null), requested)
    }

    @Test
    fun pagingKeepsTheFilter() = runTest {
        val requested = mutableListOf<Pair<MediaFilter, Long?>>()
        val filter = MediaFilter(q = "dorfplatz", mine = true)
        val grid = MediaGridModel { f, before ->
            requested += f to before
            if (before == null) MediaPage(listOf(item(9), item(8)), next = 8) else MediaPage(listOf(item(4)), null)
        }

        grid.setFilter(filter)
        grid.loadMore()

        assertEquals(listOf(9L, 8L, 4L), grid.items.map { it.id })
        assertEquals(listOf<Pair<MediaFilter, Long?>>(filter to null, filter to 8L), requested)
    }

    @Test
    fun pageOfAPreviousFilterIsDropped() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val grid = MediaGridModel { filter, _ ->
            if (filter.tags.isEmpty()) {
                gate.await()
                MediaPage(listOf(item(9)), null)
            } else {
                MediaPage(listOf(item(5)), null)
            }
        }
        val first = launch { grid.loadMore() }
        testScheduler.runCurrent()

        grid.setFilter(MediaFilter(tags = listOf("Sport")))
        gate.complete(Unit)
        first.join()

        assertEquals(listOf(5L), grid.items.map { it.id })
        assertFalse(grid.loading)
    }

    @Test
    fun uploadedMediaIsPrependedEvenWhenFiltered() = runTest {
        val grid = MediaGridModel { _, _ -> MediaPage(listOf(item(5), item(4)), null) }
        grid.setFilter(MediaFilter(tags = listOf("Sport")))

        grid.prepend(item(12))
        grid.prepend(item(13))
        grid.prepend(item(12))

        assertEquals(listOf(12L, 13L, 5L, 4L), grid.items.map { it.id })
    }

    @Test
    fun usagePlacesNameLiveWorkingAndOlder() {
        assertEquals(listOf(UsagePlace.LIVE), usagePlaces(use(live = true, latest = true, older = false)))
        assertEquals(listOf(UsagePlace.LIVE), usagePlaces(use(live = true, latest = false, older = false)))
        assertEquals(listOf(UsagePlace.WORKING), usagePlaces(use(live = false, latest = true, older = false, status = "DRAFT")))
        assertEquals(listOf(UsagePlace.OLDER), usagePlaces(use(live = false, latest = false, older = true)))
    }

    @Test
    fun editButtonOnlyWithMayEdit() {
        assertTrue(mayEdit(MediaUsageDto(true)))
        assertFalse(mayEdit(MediaUsageDto(false, listOf(use(live = true, latest = true, older = false)))))
        assertFalse(mayEdit(null))
    }

    @Test
    fun impactCountsArticlesAndPublishedOnes() {
        val usage = MediaUsageDto(true, listOf(use(true, true, false), use(false, true, false, "DRAFT"), use(false, false, true, "OFFLINE")))

        assertEquals(EditImpact(3, 1), editImpact(usage))
        assertEquals(EditImpact(0, 0), editImpact(MediaUsageDto(true)))
    }

    @Test
    fun usageDtoReadsTheServerShape() {
        val usage = json.decodeFromString<MediaUsageDto>("""{ "mayEdit": true, "articles": [{ "id": 8, "headline": "",
            "section": { "id": 2, "name": "Tiere", "slug": "tiere", "color": "orange" },
            "author": { "username": "anna", "displayName": "Anna" }, "status": "DRAFT", "pendingLevel": "SECTION_EDITOR",
            "publishedAt": null, "updatedAt": "2026-09-28T08:12:00Z", "live": false, "latest": true, "older": false }] }""")

        assertTrue(usage.mayEdit)
        assertEquals("SECTION_EDITOR", usage.articles.single().pendingLevel)
        assertNull(usage.articles.single().publishedAt)
    }

    @Test
    fun savedEditFetchesTheThumbnailAgain() = runTest {
        val fetched = mutableListOf<Long>()
        val thumbnails = Thumbnails(decode = { ImageBitmap(2, 2) }) { id -> fetched += id; byteArrayOf(1) }

        thumbnails.fetch(17, version = 0)
        thumbnails.fetch(17, version = 0)
        thumbnails.fetch(17, version = 1)
        thumbnails.invalidate(17)
        assertNull(thumbnails[17])
        thumbnails.fetch(17)

        assertEquals(listOf(17L, 17L, 17L), fetched)
    }

    @Test
    fun bytesForPeople() {
        assertEquals("1.8 MB", formatBytes(1_834_211))
        assertEquals("320 KB", formatBytes(319_600))
        assertEquals("900 B", formatBytes(900))
    }
}
