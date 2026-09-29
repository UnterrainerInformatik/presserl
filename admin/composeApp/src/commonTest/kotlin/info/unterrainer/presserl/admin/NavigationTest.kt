package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.ui.NavEntry
import info.unterrainer.presserl.admin.ui.navEntries
import kotlin.test.Test
import kotlin.test.assertEquals

class NavigationTest {

    @Test
    fun publisherSeesEveryEntry() {
        assertEquals(
            listOf(NavEntry.ARTICLES, NavEntry.IMAGES, NavEntry.SECTIONS, NavEntry.ISSUES, NavEntry.ACCOUNTS, NavEntry.NEWSPAPER),
            navEntries(
                listOf("WRITE_ARTICLES", "MANAGE_SECTIONS", "ASSIGN_SECTION_ROLES", "MANAGE_ISSUES", "ADMINISTER_ACCOUNTS",
                    "CONFIGURE_NEWSPAPER"),
            ),
        )
    }

    @Test
    fun serverWithoutConfigureNewspaperShowsNoNewspaperEntry() {
        assertEquals(
            listOf(NavEntry.ARTICLES, NavEntry.IMAGES, NavEntry.SECTIONS, NavEntry.ACCOUNTS),
            navEntries(listOf("WRITE_ARTICLES", "MANAGE_SECTIONS", "ASSIGN_SECTION_ROLES", "ADMINISTER_ACCOUNTS")),
        )
    }

    @Test
    fun serverWithoutManageIssuesShowsNoIssuesEntry() {
        assertEquals(
            listOf(NavEntry.ARTICLES, NavEntry.IMAGES, NavEntry.SECTIONS, NavEntry.ACCOUNTS, NavEntry.NEWSPAPER),
            navEntries(
                listOf("WRITE_ARTICLES", "MANAGE_SECTIONS", "ASSIGN_SECTION_ROLES", "ADMINISTER_ACCOUNTS", "CONFIGURE_NEWSPAPER"),
            ),
        )
    }

    @Test
    fun sectionEditorHasNoIssuesEntry() {
        assertEquals(
            listOf(NavEntry.ARTICLES, NavEntry.IMAGES, NavEntry.SECTIONS, NavEntry.ACCOUNTS),
            navEntries(listOf("WRITE_ARTICLES", "ASSIGN_SECTION_ROLES", "ADMINISTER_ACCOUNTS")),
        )
    }

    @Test
    fun reporterSeesArticlesAndImages() {
        assertEquals(listOf(NavEntry.ARTICLES, NavEntry.IMAGES), navEntries(listOf("WRITE_ARTICLES")))
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
        assertEquals(listOf(NavEntry.ARTICLES, NavEntry.IMAGES), navEntries(listOf("WRITE_ARTICLES", "REVIEW")))
        assertEquals(
            listOf(NavEntry.ARTICLES, NavEntry.IMAGES, NavEntry.SECTIONS),
            navEntries(listOf("REVIEW", "WRITE_ARTICLES", "MANAGE_SECTIONS")),
        )
    }
}
