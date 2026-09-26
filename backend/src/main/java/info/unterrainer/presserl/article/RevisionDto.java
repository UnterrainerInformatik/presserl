package info.unterrainer.presserl.article;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Response of {@code GET /api/articles/{id}/revisions/{number}}.
 */
public record RevisionDto(int number, String headline, Instant createdAt, Instant updatedAt, Instant publishedAt,
        boolean live, String kicker, String subheadline, String lead, JsonNode body) {

    public static RevisionDto of(ArticleView view) {
        ArticleRevisionEntity r = view.revision();
        return new RevisionDto(r.number, r.headline, r.createdAt, r.updatedAt, r.publishedAt,
                r.number.equals(view.article().liveRevision), r.kicker, r.subheadline, r.lead, r.body);
    }
}
