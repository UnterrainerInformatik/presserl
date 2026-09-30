package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.ui.NavEntry
import info.unterrainer.presserl.admin.ui.navEntries
import info.unterrainer.presserl.admin.ui.startEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NavigationTest {

    @Test
    fun publisherSeesEveryEntry() {
        assertEquals(
            listOf(NavEntry.ARTICLES, NavEntry.IMAGES, NavEntry.SECTIONS, NavEntry.ISSUES, NavEntry.ACCOUNTS, NavEntry.NEWSPAPER),
            navEntries(
                listOf("WRITE_ARTICLES", "USE_MEDIA", "MANAGE_SECTIONS", "ASSIGN_SECTION_ROLES", "MANAGE_ISSUES",
                    "ADMINISTER_ACCOUNTS", "CONFIGURE_NEWSPAPER"),
            ),
        )
    }

    @Test
    fun serverWithoutConfigureNewspaperShowsNoNewspaperEntry() {
        assertEquals(
            listOf(NavEntry.ARTICLES, NavEntry.IMAGES, NavEntry.SECTIONS, NavEntry.ACCOUNTS),
            navEntries(listOf("WRITE_ARTICLES", "USE_MEDIA", "MANAGE_SECTIONS", "ASSIGN_SECTION_ROLES", "ADMINISTER_ACCOUNTS")),
        )
    }

    @Test
    fun serverWithoutManageIssuesShowsNoIssuesEntry() {
        assertEquals(
            listOf(NavEntry.ARTICLES, NavEntry.IMAGES, NavEntry.SECTIONS, NavEntry.ACCOUNTS, NavEntry.NEWSPAPER),
            navEntries(
                listOf("WRITE_ARTICLES", "USE_MEDIA", "MANAGE_SECTIONS", "ASSIGN_SECTION_ROLES", "ADMINISTER_ACCOUNTS",
                    "CONFIGURE_NEWSPAPER"),
            ),
        )
    }

    @Test
    fun sectionEditorHasNoIssuesEntry() {
        assertEquals(
            listOf(NavEntry.ARTICLES, NavEntry.IMAGES, NavEntry.SECTIONS, NavEntry.ACCOUNTS),
            navEntries(listOf("WRITE_ARTICLES", "USE_MEDIA", "ASSIGN_SECTION_ROLES", "ADMINISTER_ACCOUNTS")),
        )
    }

    @Test
    fun reporterSeesArticlesAndImages() {
        assertEquals(listOf(NavEntry.ARTICLES, NavEntry.IMAGES), navEntries(listOf("WRITE_ARTICLES", "USE_MEDIA")))
    }

    @Test
    fun sectionManagerWithoutWritingSeesNoEntries() {
        assertEquals(emptyList(), navEntries(listOf("MANAGE_SECTIONS")))
    }

    @Test
    fun readerSeesNoEntries() {
        assertEquals(emptyList(), navEntries(emptyList()))
    }

    @Test
    fun unknownActionsAreIgnored() {
        assertEquals(listOf(NavEntry.ARTICLES, NavEntry.IMAGES), navEntries(listOf("WRITE_ARTICLES", "USE_MEDIA", "REVIEW")))
        assertEquals(
            listOf(NavEntry.ARTICLES, NavEntry.IMAGES, NavEntry.SECTIONS),
            navEntries(listOf("REVIEW", "WRITE_ARTICLES", "USE_MEDIA", "MANAGE_SECTIONS")),
        )
    }

    @Test
    fun imagesFollowUseMediaOnly() {
        assertEquals(listOf(NavEntry.ARTICLES, NavEntry.SECTIONS), navEntries(listOf("WRITE_ARTICLES", "MANAGE_SECTIONS")))
    }

    @Test
    fun sectionlessReporterGetsNoEntries() {
        assertEquals(emptyList(), navEntries(listOf("USE_MEDIA")))
    }

    @Test
    fun startView() {
        assertEquals(NavEntry.ARTICLES, startEntry(listOf("WRITE_ARTICLES", "USE_MEDIA")))
        assertEquals(NavEntry.IMAGES, startEntry(listOf("USE_MEDIA")))
        assertEquals(NavEntry.IMAGES, startEntry(listOf("USE_MEDIA", "ADMINISTER_ACCOUNTS")))
        assertNull(startEntry(emptyList()))
        assertNull(startEntry(listOf("MANAGE_SECTIONS")))
    }
}
