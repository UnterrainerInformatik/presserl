package info.unterrainer.presserl.article;

import java.util.Map;
import java.util.Set;

/**
 * Who holds the roles of the approval levels at the time of a request: the section editors per
 * section id and the newspaper-wide editors-in-chief and publishers (account ids, locked accounts
 * included). {@link #NOT_NEEDED} stands in for requests whose answers do not depend on it.
 */
public record Staffing(Map<Long, Set<String>> sectionEditors, Set<String> editorsInChief, Set<String> publishers) {

    /**
     * For requests that ask no chain question (see {@link StaffingService}); asking it anyway is a
     * programming error.
     */
    public static final Staffing NOT_NEEDED = new Staffing(null, null, null);

    public Staffing {
        if (sectionEditors != null) {
            sectionEditors = Map.copyOf(sectionEditors);
            editorsInChief = Set.copyOf(editorsInChief);
            publishers = Set.copyOf(publishers);
        }
    }

    /**
     * Whether at least one account other than the author holds the level's role for an article in
     * {@code sectionId}.
     *
     * @throws IllegalStateException on {@link #NOT_NEEDED}
     */
    public boolean staffed(ApprovalLevel level, long sectionId, String authorSub) {
        if (this == NOT_NEEDED) {
            throw new IllegalStateException("staffing was not loaded for this request");
        }
        Set<String> holders = switch (level) {
            case SECTION_EDITOR -> sectionEditors.getOrDefault(sectionId, Set.of());
            case EDITOR_IN_CHIEF -> editorsInChief;
            case PUBLISHER -> publishers;
        };
        return holders.stream().anyMatch(holder -> !holder.equals(authorSub));
    }
}
