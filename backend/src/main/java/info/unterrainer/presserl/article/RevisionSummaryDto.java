package info.unterrainer.presserl.article;

import java.time.Instant;

/**
 * Entry of {@code GET /api/articles/{id}/revisions}; {@code live} marks the article's live revision.
 */
public record RevisionSummaryDto(int number, String headline, Instant createdAt, Instant updatedAt,
        Instant publishedAt, boolean live) {

    public static RevisionSummaryDto of(ArticleEntity article, ArticleRevisionEntity r) {
        return new RevisionSummaryDto(r.number, r.headline, r.createdAt, r.updatedAt, r.publishedAt,
                r.number.equals(article.liveRevision));
    }
}
