package info.unterrainer.presserl.admin.ui

/** Header entries of the logged-in user, in display order. */
enum class NavEntry { ARTICLES, IMAGES, SECTIONS, ISSUES, ACCOUNTS, NEWSPAPER }

/** Newspaper-wide actions of `GET /api/me` the app knows; any other value is ignored. */
object NewspaperAction {
    const val WRITE_ARTICLES = "WRITE_ARTICLES"
    const val USE_MEDIA = "USE_MEDIA"
    const val MANAGE_SECTIONS = "MANAGE_SECTIONS"
    const val ASSIGN_SECTION_ROLES = "ASSIGN_SECTION_ROLES"
    const val MANAGE_ISSUES = "MANAGE_ISSUES"
    const val ADMINISTER_ACCOUNTS = "ADMINISTER_ACCOUNTS"
    const val CONFIGURE_NEWSPAPER = "CONFIGURE_NEWSPAPER"
    const val CONFIGURE_SPELL_CHECK = "CONFIGURE_SPELL_CHECK"
    const val CONFIGURE_CORRECTIONS = "CONFIGURE_CORRECTIONS"
}

/**
 * The header entries for [allowedActions] of `GET /api/me`; none when fewer than two remain. Only
 * visibility, the server enforces access.
 */
fun navEntries(allowedActions: List<String>): List<NavEntry> {
    val entries = buildList {
        if (NewspaperAction.WRITE_ARTICLES in allowedActions) add(NavEntry.ARTICLES)
        if (NewspaperAction.USE_MEDIA in allowedActions) add(NavEntry.IMAGES)
        if (NewspaperAction.MANAGE_SECTIONS in allowedActions || NewspaperAction.ASSIGN_SECTION_ROLES in allowedActions) {
            add(NavEntry.SECTIONS)
        }
        if (NewspaperAction.MANAGE_ISSUES in allowedActions) add(NavEntry.ISSUES)
        if (NewspaperAction.ADMINISTER_ACCOUNTS in allowedActions) add(NavEntry.ACCOUNTS)
        if (NewspaperAction.CONFIGURE_NEWSPAPER in allowedActions) add(NavEntry.NEWSPAPER)
    }
    return if (entries.size < 2) emptyList() else entries
}

/**
 * The view opened after login for [allowedActions] of `GET /api/me`: the article list for writers, otherwise the
 * images for sectionless reporters; `null` (the "no writing role" notice) for everyone else.
 */
fun startEntry(allowedActions: List<String>): NavEntry? = when {
    NewspaperAction.WRITE_ARTICLES in allowedActions -> NavEntry.ARTICLES
    NewspaperAction.USE_MEDIA in allowedActions -> NavEntry.IMAGES
    else -> null
}
