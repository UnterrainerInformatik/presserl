package info.unterrainer.presserl.article;

import java.time.Instant;
import java.util.List;

import info.unterrainer.presserl.issue.IssueRefDto;
import info.unterrainer.presserl.section.Newsroom;

/**
 * List entry of {@code GET /api/articles}.
 */
public record ArticleSummaryDto(
        long id,
        ArticleStatus status,
        AuthorDto author,
        SectionRefDto section,
        IssueRefDto issue,
        String headline,
        String kicker,
        int revision,
        Integer liveRevision,
        boolean hasUnpublishedChanges,
        ApprovalLevel pendingLevel,
        boolean locked,
        Instant createdAt,
        Instant updatedAt,
        Instant publishedAt,
        List<ArticleAction> allowedActions) {

    /**
     * @param staffing from {@link StaffingService#forArticles} for the listed articles
     * @param rules    from {@link ArticleService#rules}
     */
    public static ArticleSummaryDto of(ArticleView view, Newsroom newsroom, Staffing staffing, ArticleRules rules) {
        ArticleEntity a = view.article();
        ArticleRevisionEntity r = view.revision();
        return new ArticleSummaryDto(a.id, a.status, AuthorDto.of(a), SectionRefDto.of(view.section()), IssueRefDto.of(view.issue()),
                r.headline, r.kicker, r.number, a.liveRevision,
                ArticlePolicy.hasUnpublishedChanges(a, r.number), a.pendingLevel, a.locked, a.createdAt, a.updatedAt, a.publishedAt,
                ArticlePolicy.allowedActions(newsroom, a, r.number, staffing, rules));
    }
}
