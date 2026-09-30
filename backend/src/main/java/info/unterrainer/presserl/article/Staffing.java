package info.unterrainer.presserl.article;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import info.unterrainer.presserl.trust.TrustScope;

/**
 * The facts about the newsroom the approval chain needs at the time of a request: who holds the
 * roles of the approval levels (the section editors per section id and the newspaper-wide
 * editors-in-chief and publishers; account ids, locked accounts included), the contributors of the
 * articles the request asks about and their trust entries. {@link #NOT_NEEDED} stands in for
 * requests whose answers do not depend on it.
 *
 * @param trusts       the trust entries by account id; accounts missing are trusted nowhere
 * @param contributors the contributors by article id (see {@link #contributorsOf}); articles missing
 *                     have their author as only contributor
 */
public record Staffing(Map<Long, Set<String>> sectionEditors, Set<String> editorsInChief, Set<String> publishers,
        Map<String, Set<TrustScope>> trusts, Map<Long, Set<String>> contributors) {

    /**
     * For requests that ask no chain question (see {@link StaffingService}); asking it anyway is a
     * programming error.
     */
    public static final Staffing NOT_NEEDED = new Staffing(null, null, null, null, null);

    public Staffing {
        if (sectionEditors != null) {
            sectionEditors = Map.copyOf(sectionEditors);
            editorsInChief = Set.copyOf(editorsInChief);
            publishers = Set.copyOf(publishers);
            trusts = Map.copyOf(trusts);
            contributors = Map.copyOf(contributors);
        }
    }

    /**
     * Staffing whose articles have their authors as only contributors.
     */
    public Staffing(Map<Long, Set<String>> sectionEditors, Set<String> editorsInChief, Set<String> publishers,
            Map<String, Set<TrustScope>> trusts) {
        this(sectionEditors, editorsInChief, publishers, trusts, Map.of());
    }

    /**
     * Staffing without any trust entry.
     */
    public Staffing(Map<Long, Set<String>> sectionEditors, Set<String> editorsInChief, Set<String> publishers) {
        this(sectionEditors, editorsInChief, publishers, Map.of());
    }

    /**
     * The distinct authors of the article's revisions above its live revision (of all revisions
     * while it was never published); the author when there are none.
     *
     * @throws IllegalStateException on {@link #NOT_NEEDED}
     */
    public Set<String> contributorsOf(ArticleEntity article) {
        requireLoaded();
        Set<String> found = article.id == null ? null : contributors.get(article.id);
        return found == null || found.isEmpty() ? Set.of(article.authorSub) : found;
    }

    /**
     * The level of the account for an article in {@code sectionId} from the roles it holds:
     * {@code PUBLISHER}, else {@code EDITOR_IN_CHIEF}, else {@code SECTION_EDITOR} of that section;
     * empty for a reporter (below every level).
     *
     * @throws IllegalStateException on {@link #NOT_NEEDED}
     */
    public Optional<ApprovalLevel> levelOf(String sub, long sectionId) {
        requireLoaded();
        if (publishers.contains(sub)) {
            return Optional.of(ApprovalLevel.PUBLISHER);
        }
        if (editorsInChief.contains(sub)) {
            return Optional.of(ApprovalLevel.EDITOR_IN_CHIEF);
        }
        if (sectionEditors.getOrDefault(sectionId, Set.of()).contains(sub)) {
            return Optional.of(ApprovalLevel.SECTION_EDITOR);
        }
        return Optional.empty();
    }

    /**
     * Whether at least one account other than {@code sub} holds the level's role for an article in
     * {@code sectionId}.
     *
     * @throws IllegalStateException on {@link #NOT_NEEDED}
     */
    public boolean staffed(ApprovalLevel level, long sectionId, String sub) {
        requireLoaded();
        Set<String> holders = switch (level) {
            case SECTION_EDITOR -> sectionEditors.getOrDefault(sectionId, Set.of());
            case EDITOR_IN_CHIEF -> editorsInChief;
            case PUBLISHER -> publishers;
        };
        return holders.stream().anyMatch(holder -> !holder.equals(sub));
    }

    /**
     * Whether the level trusts the account for an article in {@code sectionId}: a
     * {@code SECTION_EDITOR} entry for that section, the newspaper-wide entry for the other levels.
     *
     * @throws IllegalStateException on {@link #NOT_NEEDED}
     */
    public boolean trusts(ApprovalLevel level, long sectionId, String sub) {
        requireLoaded();
        return trusts.getOrDefault(sub, Set.of()).contains(TrustScope.of(level, sectionId));
    }

    private void requireLoaded() {
        if (this == NOT_NEEDED) {
            throw new IllegalStateException("staffing was not loaded for this request");
        }
    }
}
