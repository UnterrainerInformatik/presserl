package info.unterrainer.presserl.section;

/**
 * Roles an account holds within one section, stored in the Presserl database (not in Keycloak).
 * Roles are cumulative: a section editor may do everything a reporter may.
 */
public enum SectionRole {
    SECTION_EDITOR,
    REPORTER
}
