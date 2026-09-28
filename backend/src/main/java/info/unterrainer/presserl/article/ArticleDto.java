package info.unterrainer.presserl.article;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import info.unterrainer.presserl.issue.IssueRefDto;
import info.unterrainer.presserl.section.Newsroom;

/**
 * An article with the content of its latest revision ({@code revision}).
 */
public record ArticleDto(
        long id,
        ArticleStatus status,
        AuthorDto author,
        SectionRefDto section,
        IssueRefDto issue,
        int revision,
        Integer liveRevision,
        boolean hasUnpublishedChanges,
        ApprovalLevel pendingLevel,
        boolean locked,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant publishedAt,
        String kicker,
        String headline,
        String subheadline,
        String lead,
        JsonNode body,
        LeadImageDto leadImage,
        List<ArticleAction> allowedActions) {

    /**
     * @param staffing  from {@link StaffingService#forArticles} for this article
     * @param leadImage from {@link ArticleService#leadImage} for the revision
     */
    public static ArticleDto of(ArticleView view, Newsroom newsroom, Staffing staffing, LeadImageDto leadImage) {
        ArticleEntity a = view.article();
        ArticleRevisionEntity r = view.revision();
        return new ArticleDto(a.id, a.status, AuthorDto.of(a), SectionRefDto.of(view.section()), IssueRefDto.of(view.issue()), r.number, a.liveRevision,
                ArticlePolicy.hasUnpublishedChanges(a, r.number), a.pendingLevel, a.locked, a.version, a.createdAt, a.updatedAt, a.publishedAt,
                r.kicker, r.headline, r.subheadline, r.lead, r.body, leadImage, ArticlePolicy.allowedActions(newsroom, a, r.number, staffing));
    }
}
