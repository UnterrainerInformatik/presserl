package info.unterrainer.presserl.auth;

/**
 * Newspaper-wide actions a user may perform, in the order they are listed in {@code allowedActions}
 * of {@code GET /api/me}.
 */
public enum NewspaperAction {
    WRITE_ARTICLES,
    USE_MEDIA,
    MANAGE_SECTIONS,
    ASSIGN_SECTION_ROLES,
    MANAGE_ISSUES,
    ADMINISTER_ACCOUNTS,
    CONFIGURE_NEWSPAPER,
    CONFIGURE_SPELL_CHECK,
    CONFIGURE_CORRECTIONS
}
