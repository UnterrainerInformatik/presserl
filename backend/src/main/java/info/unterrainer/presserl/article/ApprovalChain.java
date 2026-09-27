package info.unterrainer.presserl.article;

import java.util.Arrays;
import java.util.Optional;

import info.unterrainer.presserl.auth.NewspaperRole;
import info.unterrainer.presserl.section.Newsroom;

/**
 * The approval chain as pure functions. A user's level for an article is the highest of
 * {@code PUBLISHER}, {@code EDITOR_IN_CHIEF} and {@code SECTION_EDITOR} of the article's section they
 * hold; empty means reporter (below every level). The chain above a level consists of the higher
 * levels that are staffed by someone other than the author and do not trust the author
 * ({@link Staffing}); other levels are skipped, except {@code PUBLISHER} while the article is locked
 * by the emergency brake, which qualifies whether it is staffed or trusts the author or not.
 */
public final class ApprovalChain {

    private ApprovalChain() {
    }

    /**
     * The level of the author (the requesting user) for an article in {@code sectionId}; empty for a
     * reporter.
     */
    public static Optional<ApprovalLevel> authorLevel(Newsroom author, long sectionId) {
        return level(author, sectionId);
    }

    /**
     * The level up to which the requesting user may approve an article in {@code sectionId}; empty
     * when they may approve none. Whether they are the author is decided by {@link ArticlePolicy}.
     */
    public static Optional<ApprovalLevel> approverLevel(Newsroom approver, long sectionId) {
        return level(approver, sectionId);
    }

    /**
     * The lowest staffed level strictly above {@code above} (above nothing when empty) that does not
     * trust the author; empty when no level remains, which means the article is published.
     *
     * @param locked whether the article is locked by the emergency brake: {@code PUBLISHER} then
     *               qualifies whether it is staffed or trusts the author or not
     */
    public static Optional<ApprovalLevel> next(Optional<ApprovalLevel> above, long sectionId, String authorSub,
            Staffing staffing, boolean locked) {
        return Arrays.stream(ApprovalLevel.values())
                .filter(level -> above.map(a -> level.compareTo(a) > 0).orElse(true))
                .filter(level -> locked && level == ApprovalLevel.PUBLISHER
                        || staffing.staffed(level, sectionId, authorSub)
                                && !staffing.trusts(level, sectionId, authorSub))
                .findFirst();
    }

    private static Optional<ApprovalLevel> level(Newsroom newsroom, long sectionId) {
        if (newsroom.user().has(NewspaperRole.PUBLISHER)) {
            return Optional.of(ApprovalLevel.PUBLISHER);
        }
        if (newsroom.user().has(NewspaperRole.EDITOR_IN_CHIEF)) {
            return Optional.of(ApprovalLevel.EDITOR_IN_CHIEF);
        }
        if (newsroom.isSectionEditorOf(sectionId)) {
            return Optional.of(ApprovalLevel.SECTION_EDITOR);
        }
        return Optional.empty();
    }
}
