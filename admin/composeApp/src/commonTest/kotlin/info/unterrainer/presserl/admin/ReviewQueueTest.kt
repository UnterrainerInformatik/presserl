package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.api.ArticleSummaryDto
import info.unterrainer.presserl.admin.ui.ListTab
import info.unterrainer.presserl.admin.ui.tabAfterQueue
import info.unterrainer.presserl.admin.ui.visibleTabs
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ReviewQueueTest {

    private val waiting = Json { ignoreUnknownKeys = true }.decodeFromString<ArticleSummaryDto>(
        """{ "id": 42, "status": "SUBMITTED", "author": { "username": "reader", "displayName": "Reader" },
            "section": { "id": 1, "name": "Sport", "slug": "sport", "color": "green" },
            "headline": "Goal", "kicker": "", "revision": 1, "liveRevision": null, "hasUnpublishedChanges": false,
            "pendingLevel": "SECTION_EDITOR", "updatedAt": "2026-09-27T10:05:00Z", "publishedAt": null,
            "allowedActions": ["APPROVE", "REJECT"] }""",
    )

    @Test
    fun noQueueTabBeforeTheFirstResponse() {
        assertEquals(listOf(ListTab.MINE, ListTab.ALL), visibleTabs(null))
    }

    @Test
    fun noQueueTabWhileNothingWaits() {
        assertEquals(listOf(ListTab.MINE, ListTab.ALL), visibleTabs(emptyList()))
    }

    @Test
    fun queueTabWhileSomethingWaits() {
        assertEquals(listOf(ListTab.MINE, ListTab.ALL, ListTab.QUEUE), visibleTabs(listOf(waiting)))
    }

    @Test
    fun emptiedQueueFallsBackToMine() {
        assertEquals(ListTab.MINE, tabAfterQueue(ListTab.QUEUE, emptyList()))
    }

    @Test
    fun otherTabsStay() {
        assertEquals(ListTab.QUEUE, tabAfterQueue(ListTab.QUEUE, listOf(waiting)))
        assertEquals(ListTab.ALL, tabAfterQueue(ListTab.ALL, emptyList()))
        assertEquals(ListTab.MINE, tabAfterQueue(ListTab.MINE, emptyList()))
    }
}
