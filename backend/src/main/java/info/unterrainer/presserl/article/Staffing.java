package info.unterrainer.presserl.article;

import java.util.Map;
import java.util.Set;

import info.unterrainer.presserl.trust.TrustScope;

/**
 * The facts about the newsroom the approval chain needs at the time of a request: who holds the
 * roles of the approval levels (the section editors per section id and the newspaper-wide
 * editors-in-chief and publishers; account ids, locked accounts included) and the trust entries of
 * the authors the request asks about. {@link #NOT_NEEDED} stands in for requests whose answers do
 * not depend on it.
 *
 * @param trusts the trust entries by account id; accounts missing are trusted nowhere
 */
public record Staffing(Map<Long, Set<String>> sectionEditors, Set<String> editorsInChief, Set<String> publishers,
        Map<String, Set<TrustScope>> trusts) {

    /**
     * For requests that ask no chain question (see {@link StaffingService}); asking it anyway is a
     * programming error.
     */
    public static final Staffing NOT_NEEDED = new Staffing(null, null, null, null);

    public Staffing {
        if (sectionEditors != null) {
            sectionEditors = Map.copyOf(sectionEditors);
            editorsInChief = Set.copyOf(editorsInChief);
            publishers = Set.copyOf(publishers);
            trusts = Map.copyOf(trusts);
        }
    }

    /**
     * Staffing without any trust entry.
     */
    public Staffing(Map<Long, Set<String>> sectionEditors, Set<String> editorsInChief, Set<String> publishers) {
        this(sectionEditors, editorsInChief, publishers, Map.of());
    }

    /**
     * Whether at least one account other than the author holds the level's role for an article in
     * {@code sectionId}.
     *
     * @throws IllegalStateException on {@link #NOT_NEEDED}
     */
    public boolean staffed(ApprovalLevel level, long sectionId, String authorSub) {
        requireLoaded();
        Set<String> holders = switch (level) {
            case SECTION_EDITOR -> sectionEditors.getOrDefault(sectionId, Set.of());
            case EDITOR_IN_CHIEF -> editorsInChief;
            case PUBLISHER -> publishers;
        };
        return holders.stream().anyMatch(holder -> !holder.equals(authorSub));
    }

    /**
     * Whether the level trusts the author for an article in {@code sectionId}: a
     * {@code SECTION_EDITOR} entry for that section, the newspaper-wide entry for the other levels.
     *
     * @throws IllegalStateException on {@link #NOT_NEEDED}
     */
    public boolean trusts(ApprovalLevel level, long sectionId, String authorSub) {
        requireLoaded();
        return trusts.getOrDefault(authorSub, Set.of()).contains(TrustScope.of(level, sectionId));
    }

    private void requireLoaded() {
        if (this == NOT_NEEDED) {
            throw new IllegalStateException("staffing was not loaded for this request");
        }
    }
}
