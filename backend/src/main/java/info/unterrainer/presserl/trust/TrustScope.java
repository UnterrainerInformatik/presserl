package info.unterrainer.presserl.trust;

import java.util.Comparator;
import java.util.List;

import info.unterrainer.presserl.article.ApprovalLevel;

/**
 * What a trust entry is about: an approval level and, for {@code SECTION_EDITOR} only, the section;
 * {@code sectionId} is {@code null} for the newspaper-wide levels.
 */
public record TrustScope(ApprovalLevel level, Long sectionId) {

    public TrustScope {
        if ((level == ApprovalLevel.SECTION_EDITOR) != (sectionId != null)) {
            throw new IllegalArgumentException("a section belongs to SECTION_EDITOR trust only: " + level + ", "
                    + sectionId);
        }
    }

    /**
     * The scope of {@code level} for an article in {@code sectionId}: the section for
     * {@code SECTION_EDITOR}, newspaper-wide otherwise.
     */
    public static TrustScope of(ApprovalLevel level, long sectionId) {
        return new TrustScope(level, level == ApprovalLevel.SECTION_EDITOR ? sectionId : null);
    }

    /**
     * The order of trust lists: {@code PUBLISHER}, {@code EDITOR_IN_CHIEF}, then
     * {@code SECTION_EDITOR} by section position.
     *
     * @param sectionIds the ids of all sections by position
     */
    public static Comparator<TrustScope> order(List<Long> sectionIds) {
        return Comparator.comparing(TrustScope::level, Comparator.<ApprovalLevel>reverseOrder())
                .thenComparingInt(scope -> sectionIds.indexOf(scope.sectionId()));
    }
}
