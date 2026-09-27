package info.unterrainer.presserl.article;

import java.time.Instant;
import java.util.List;

import info.unterrainer.presserl.section.Newsroom;

/**
 * List entry of {@code GET /api/articles}.
 */
public record ArticleSummaryDto(
        long id,
        ArticleStatus status,
        AuthorDto author,
        SectionRefDto section,
        String headline,
        String kicker,
        int revision,
        Integer liveRevision,
        boolean hasUnpublishedChanges,
        ApprovalLevel pendingLevel,
        Instant updatedAt,
        Instant publishedAt,
        List<ArticleAction> allowedActions) {

    /**
     * @param staffing from {@link StaffingService#forArticles} for the listed articles
     */
    public static ArticleSummaryDto of(ArticleView view, Newsroom newsroom, Staffing staffing) {
        ArticleEntity a = view.article();
        ArticleRevisionEntity r = view.revision();
        return new ArticleSummaryDto(a.id, a.status, AuthorDto.of(a), SectionRefDto.of(view.section()), r.headline, r.kicker, r.number, a.liveRevision,
                ArticlePolicy.hasUnpublishedChanges(a, r.number), a.pendingLevel, a.updatedAt, a.publishedAt,
                ArticlePolicy.allowedActions(newsroom, a, r.number, staffing));
    }
}
