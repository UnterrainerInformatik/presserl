package info.unterrainer.presserl.article;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

import info.unterrainer.presserl.auth.NewspaperRole;
import info.unterrainer.presserl.section.Newsroom;

/**
 * The approval chain as pure functions. A user's level for an article is the highest of
 * {@code PUBLISHER}, {@code EDITOR_IN_CHIEF} and {@code SECTION_EDITOR} of the article's section they
 * hold; empty means reporter (below every level). The chain of an article is the union of the chains
 * of its contributors ({@link Staffing#contributorsOf}). The chain of one contributor consists of the
 * levels above the contributor's level that are staffed by someone other than the contributor and do
 * not trust the contributor ({@link Staffing}); other levels are skipped, except {@code PUBLISHER}
 * while the article is locked by the emergency brake, which qualifies for every contributor below it
 * whether it is staffed or trusts them or not.
 */
public final class ApprovalChain {

    private ApprovalChain() {
    }

    /**
     * The level up to which the requesting user may approve an article in {@code sectionId}; empty
     * when they may approve none. Whether they are the author is decided by {@link ArticlePolicy}.
     */
    public static Optional<ApprovalLevel> approverLevel(Newsroom approver, long sectionId) {
        if (approver.user().has(NewspaperRole.PUBLISHER)) {
            return Optional.of(ApprovalLevel.PUBLISHER);
        }
        if (approver.user().has(NewspaperRole.EDITOR_IN_CHIEF)) {
            return Optional.of(ApprovalLevel.EDITOR_IN_CHIEF);
        }
        if (approver.isSectionEditorOf(sectionId)) {
            return Optional.of(ApprovalLevel.SECTION_EDITOR);
        }
        return Optional.empty();
    }

    /**
     * The lowest level strictly above {@code above} (above nothing when empty) that belongs to the
     * chain of at least one contributor; empty when no level remains, which means the article is
     * published. Staffing is only asked when a level above {@code above} exists.
     *
     * @param locked whether the article is locked by the emergency brake: {@code PUBLISHER} then
     *               belongs to the chain of every contributor below it
     */
    public static Optional<ApprovalLevel> next(Optional<ApprovalLevel> above, long sectionId, Set<String> contributors,
            Staffing staffing, boolean locked) {
        return Arrays.stream(ApprovalLevel.values())
                .filter(level -> above.map(a -> level.compareTo(a) > 0).orElse(true))
                .filter(level -> contributors.stream()
                        .anyMatch(contributor -> inChainOf(contributor, level, sectionId, staffing, locked)))
                .findFirst();
    }

    private static boolean inChainOf(String contributor, ApprovalLevel level, long sectionId, Staffing staffing,
            boolean locked) {
        if (staffing.levelOf(contributor, sectionId).filter(own -> level.compareTo(own) <= 0).isPresent()) {
            return false;
        }
        return locked && level == ApprovalLevel.PUBLISHER
                || staffing.staffed(level, sectionId, contributor) && !staffing.trusts(level, sectionId, contributor);
    }
}
