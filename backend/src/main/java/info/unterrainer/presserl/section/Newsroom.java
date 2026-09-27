package info.unterrainer.presserl.section;

import java.util.Map;
import java.util.Optional;

import info.unterrainer.presserl.auth.CurrentUser;
import info.unterrainer.presserl.auth.NewspaperRole;

/**
 * Who the requesting user is in this newspaper: the newspaper roles from the token and the section
 * roles from the database. Access checks that depend on section roles use this instead of
 * {@code @RolesAllowed}, which only sees the token.
 *
 * @param sectionRoles the user's role per section id
 */
public record Newsroom(CurrentUser user, Map<Long, SectionRole> sectionRoles) {

    public Newsroom {
        sectionRoles = Map.copyOf(sectionRoles);
    }

    /**
     * Publishers and editors-in-chief administer the whole newspaper.
     */
    public boolean isAdministrator() {
        return user.has(NewspaperRole.PUBLISHER) || user.has(NewspaperRole.EDITOR_IN_CHIEF);
    }

    public boolean isSectionEditorAnywhere() {
        return sectionRoles.containsValue(SectionRole.SECTION_EDITOR);
    }

    /**
     * Administrators and section editors of any section may open the account administration.
     */
    public boolean mayAdministerAccounts() {
        return isAdministrator() || isSectionEditorAnywhere();
    }

    /**
     * Administrators and every holder of a section role may use the article endpoints.
     */
    public boolean isWriter() {
        return isAdministrator() || !sectionRoles.isEmpty();
    }

    /**
     * Administrators write in every section, section members in their own sections.
     */
    public boolean mayWriteIn(long sectionId) {
        return isAdministrator() || sectionRoles.containsKey(sectionId);
    }

    public boolean isSectionEditorOf(long sectionId) {
        return roleIn(sectionId).orElse(null) == SectionRole.SECTION_EDITOR;
    }

    public Optional<SectionRole> roleIn(long sectionId) {
        return Optional.ofNullable(sectionRoles.get(sectionId));
    }
}
