package info.unterrainer.presserl.section;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import info.unterrainer.presserl.auth.CurrentUser;
import info.unterrainer.presserl.auth.NewspaperAction;
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

    /**
     * Administrators create, change and reorder sections.
     */
    public boolean mayManageSections() {
        return isAdministrator();
    }

    /**
     * Administrators assign section roles everywhere, section editors in their own sections.
     */
    public boolean mayAssignSectionRolesIn(long sectionId) {
        return isAdministrator() || isSectionEditorOf(sectionId);
    }

    /**
     * Whether there is at least one section in which the user may assign section roles.
     */
    public boolean mayAssignSectionRolesAnywhere() {
        return isAdministrator() || isSectionEditorAnywhere();
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

    /**
     * The newspaper-wide actions the user may perform, in declaration order of
     * {@link NewspaperAction}; each follows the predicate its endpoints check.
     */
    public List<NewspaperAction> allowedActions() {
        List<NewspaperAction> actions = new ArrayList<>();
        if (isWriter()) {
            actions.add(NewspaperAction.WRITE_ARTICLES);
        }
        if (mayManageSections()) {
            actions.add(NewspaperAction.MANAGE_SECTIONS);
        }
        if (mayAssignSectionRolesAnywhere()) {
            actions.add(NewspaperAction.ASSIGN_SECTION_ROLES);
        }
        if (mayAdministerAccounts()) {
            actions.add(NewspaperAction.ADMINISTER_ACCOUNTS);
        }
        return List.copyOf(actions);
    }
}
