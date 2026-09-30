package info.unterrainer.presserl.section;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import info.unterrainer.presserl.auth.CurrentUser;
import info.unterrainer.presserl.auth.NewspaperAction;
import info.unterrainer.presserl.auth.NewspaperRole;

/**
 * Who the requesting user is in this newspaper: the newspaper roles from the token, the section
 * roles and the sectionless-reporter marker from the database. Access checks that depend on section
 * roles or the marker use this instead of {@code @RolesAllowed}, which only sees the token.
 *
 * @param sectionRoles        the user's role per section id
 * @param sectionlessReporter whether the user carries the marker that lets them use the media
 *                            endpoints without writing articles
 */
public record Newsroom(CurrentUser user, Map<Long, SectionRole> sectionRoles, boolean sectionlessReporter) {

    public Newsroom {
        sectionRoles = Map.copyOf(sectionRoles);
    }

    /**
     * A user without the sectionless-reporter marker.
     */
    public Newsroom(CurrentUser user, Map<Long, SectionRole> sectionRoles) {
        this(user, sectionRoles, false);
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

    /**
     * Administrators create, date, publish and assemble issues.
     */
    public boolean mayManageIssues() {
        return isAdministrator();
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
     * Administrators change the newspaper settings.
     */
    public boolean mayConfigureNewspaper() {
        return isAdministrator();
    }

    /**
     * Only publishers choose how much help the spell check gives ({@code spell-check.help}).
     */
    public boolean mayConfigureSpellCheck() {
        return user.has(NewspaperRole.PUBLISHER);
    }

    /**
     * Only publishers switch corrections by higher levels ({@code article.corrections}).
     */
    public boolean mayConfigureCorrections() {
        return user.has(NewspaperRole.PUBLISHER);
    }

    /**
     * Administrators and every holder of a section role may use the article endpoints.
     */
    public boolean isWriter() {
        return isAdministrator() || !sectionRoles.isEmpty();
    }

    /**
     * Writers and sectionless reporters may use the media endpoints.
     */
    public boolean mayUseMedia() {
        return isWriter() || sectionlessReporter;
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
        if (mayUseMedia()) {
            actions.add(NewspaperAction.USE_MEDIA);
        }
        if (mayManageSections()) {
            actions.add(NewspaperAction.MANAGE_SECTIONS);
        }
        if (mayAssignSectionRolesAnywhere()) {
            actions.add(NewspaperAction.ASSIGN_SECTION_ROLES);
        }
        if (mayManageIssues()) {
            actions.add(NewspaperAction.MANAGE_ISSUES);
        }
        if (mayAdministerAccounts()) {
            actions.add(NewspaperAction.ADMINISTER_ACCOUNTS);
        }
        if (mayConfigureNewspaper()) {
            actions.add(NewspaperAction.CONFIGURE_NEWSPAPER);
        }
        if (mayConfigureSpellCheck()) {
            actions.add(NewspaperAction.CONFIGURE_SPELL_CHECK);
        }
        if (mayConfigureCorrections()) {
            actions.add(NewspaperAction.CONFIGURE_CORRECTIONS);
        }
        return List.copyOf(actions);
    }
}
