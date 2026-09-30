package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.ui.ListTab
import info.unterrainer.presserl.admin.ui.NavEntry
import info.unterrainer.presserl.admin.ui.Route
import info.unterrainer.presserl.admin.ui.stackAfterBack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SystemBackTest {

    private val articles = Route.ArticleList(ListTab.MINE)

    @Test
    fun backFromTheEditorReturnsToTheList() {
        assertEquals(listOf(articles), stackAfterBack(listOf(articles, Route.Editor(7)), NavEntry.ARTICLES))
    }

    @Test
    fun backFromARevisionReturnsToTheRevisions() {
        val stack = listOf(articles, Route.Editor(7), Route.Revisions(7), Route.Revision(7, 2))

        assertEquals(stack.dropLast(1), stackAfterBack(stack, NavEntry.ARTICLES))
    }

    @Test
    fun theStartViewLeavesTheApp() {
        assertNull(stackAfterBack(listOf(articles), NavEntry.ARTICLES))
        assertNull(stackAfterBack(listOf(Route.ArticleList(ListTab.entries.last())), NavEntry.ARTICLES))
        assertNull(stackAfterBack(listOf(Route.Media), NavEntry.IMAGES))
    }

    @Test
    fun anotherHeaderEntryReturnsToTheStartView() {
        assertEquals(listOf(articles), stackAfterBack(listOf(Route.Sections), NavEntry.ARTICLES))
        assertEquals(listOf(Route.Media), stackAfterBack(listOf(Route.Accounts), NavEntry.IMAGES))
    }

    @Test
    fun theNoWritingRoleNoticeLeavesTheApp() {
        assertNull(stackAfterBack(emptyList(), null))
        assertNull(stackAfterBack(listOf(Route.Accounts), null))
    }

    @Test
    fun backFromImageDetailsReturnsToTheImages() {
        assertEquals(listOf(Route.Media), stackAfterBack(listOf(Route.Media, Route.MediaDetail(3)), NavEntry.IMAGES))
    }
}
