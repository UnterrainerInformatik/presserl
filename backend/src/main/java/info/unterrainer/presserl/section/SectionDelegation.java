package info.unterrainer.presserl.section;

import java.util.List;

/**
 * Which section roles a user may assign and remove in a section: publishers and editors-in-chief
 * everywhere, section editors in their own sections, nobody else (reporters do not delegate).
 */
public final class SectionDelegation {

    private static final List<SectionRole> ALL = List.of(SectionRole.SECTION_EDITOR, SectionRole.REPORTER);

    private SectionDelegation() {
    }

    /**
     * The assignable roles in declaration order of {@link SectionRole}; empty when the user may not
     * manage the section's members at all.
     */
    public static List<SectionRole> assignable(Newsroom newsroom, long sectionId) {
        if (newsroom.isAdministrator()
                || newsroom.roleIn(sectionId).filter(role -> role == SectionRole.SECTION_EDITOR).isPresent()) {
            return ALL;
        }
        return List.of();
    }

    /**
     * Whether the user may give an account role {@code next} in the section while it holds
     * {@code current} ({@code null} for none): both must be assignable.
     */
    public static boolean mayChange(Newsroom newsroom, long sectionId, SectionRole current, SectionRole next) {
        List<SectionRole> assignable = assignable(newsroom, sectionId);
        return assignable.contains(next) && (current == null || assignable.contains(current));
    }

    /**
     * Whether the user may take role {@code current} away in the section.
     */
    public static boolean mayRemove(Newsroom newsroom, long sectionId, SectionRole current) {
        return assignable(newsroom, sectionId).contains(current);
    }
}
