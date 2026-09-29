package info.unterrainer.presserl.admin

import androidx.compose.ui.graphics.ImageBitmap
import info.unterrainer.presserl.admin.api.AuthorDto
import info.unterrainer.presserl.admin.api.MediaDto
import info.unterrainer.presserl.admin.api.MediaListItemDto
import info.unterrainer.presserl.admin.api.MediaPage
import info.unterrainer.presserl.admin.api.MediaUsageDto
import info.unterrainer.presserl.admin.api.MediaUseDto
import info.unterrainer.presserl.admin.api.json
import info.unterrainer.presserl.admin.ui.media.EditImpact
import info.unterrainer.presserl.admin.ui.media.MediaGridModel
import info.unterrainer.presserl.admin.ui.media.Thumbnails
import info.unterrainer.presserl.admin.ui.media.UsagePlace
import info.unterrainer.presserl.admin.ui.media.editImpact
import info.unterrainer.presserl.admin.ui.media.formatBytes
import info.unterrainer.presserl.admin.ui.media.mayEdit
import info.unterrainer.presserl.admin.ui.media.usagePlaces
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
        val grid = MediaGridModel { before ->
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
    fun gridKeepsItsPagesWhenAPageFails() = runTest {
        var fail = false
        val grid = MediaGridModel { before -> if (fail) error("offline") else MediaPage(listOf(item(3), item(2)), next = 2) }
        grid.loadMore()
        fail = true

        grid.loadMore()

        assertEquals(2, grid.items.size)
        assertEquals("offline", grid.error)
        assertTrue(grid.hasMore)
    }

    @Test
    fun gridShowsAnEditedMediaInPlace() = runTest {
        val grid = MediaGridModel { MediaPage(listOf(item(3, usageCount = 2), item(2)), null) }
        grid.loadMore()

        grid.replace(MediaDto(3, "image/jpeg", 1200, 800, 900, AuthorDto("anna", "Anna"), "t", version = 1))

        assertEquals(1, grid.items[0].version)
        assertEquals(1200, grid.items[0].width)
        assertEquals(2, grid.items[0].usageCount)
        assertEquals(0, grid.items[1].version)
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
