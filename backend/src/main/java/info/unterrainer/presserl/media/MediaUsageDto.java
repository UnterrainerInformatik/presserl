package info.unterrainer.presserl.media;

import java.time.Instant;
import java.util.List;

import info.unterrainer.presserl.article.ApprovalLevel;
import info.unterrainer.presserl.article.ArticleStatus;
import info.unterrainer.presserl.article.AuthorDto;
import info.unterrainer.presserl.article.SectionRefDto;

/**
 * Where a media is used ({@code GET /api/media/{id}/usage}) and whether the caller may edit it.
 *
 * @param articles most recently changed first
 */
public record MediaUsageDto(boolean mayEdit, List<ArticleUseDto> articles) {

    /**
     * An article any of whose revisions uses the media as lead image; the headline is that of its
     * latest revision.
     *
     * @param live   the live revision uses the media
     * @param latest the latest revision uses the media
     * @param older  only revisions other than the live and the latest one use the media
     */
    public record ArticleUseDto(long id, String headline, SectionRefDto section, AuthorDto author,
            ArticleStatus status, ApprovalLevel pendingLevel, Instant publishedAt, Instant updatedAt, boolean live,
            boolean latest, boolean older) {
    }
}
