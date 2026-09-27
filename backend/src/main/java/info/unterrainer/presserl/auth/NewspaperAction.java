package info.unterrainer.presserl.auth;

/**
 * Newspaper-wide actions a user may perform, in the order they are listed in {@code allowedActions}
 * of {@code GET /api/me}.
 */
public enum NewspaperAction {
    WRITE_ARTICLES,
    MANAGE_SECTIONS,
    ASSIGN_SECTION_ROLES,
    ADMINISTER_ACCOUNTS
}
