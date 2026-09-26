package info.unterrainer.presserl.article;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import info.unterrainer.presserl.auth.CurrentUser;

/**
 * An article with the content of its latest revision ({@code revision}).
 */
public record ArticleDto(
        long id,
        ArticleStatus status,
        AuthorDto author,
        int revision,
        Integer liveRevision,
        boolean hasUnpublishedChanges,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant publishedAt,
        String kicker,
        String headline,
        String subheadline,
        String lead,
        JsonNode body,
        List<ArticleAction> allowedActions) {

    public static ArticleDto of(ArticleView view, CurrentUser user) {
        ArticleEntity a = view.article();
        ArticleRevisionEntity r = view.revision();
        return new ArticleDto(a.id, a.status, AuthorDto.of(a), r.number, a.liveRevision,
                ArticlePolicy.hasUnpublishedChanges(a, r.number), a.version, a.createdAt, a.updatedAt, a.publishedAt,
                r.kicker, r.headline, r.subheadline, r.lead, r.body, ArticlePolicy.allowedActions(user, a, r.number));
    }
}
