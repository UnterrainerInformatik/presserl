package info.unterrainer.presserl.reader;

import java.util.List;
import java.util.Optional;

import info.unterrainer.presserl.article.ArticleEntity;
import info.unterrainer.presserl.article.ArticleRevisionEntity;
import info.unterrainer.presserl.article.ArticleStatus;
import info.unterrainer.presserl.media.MediaRenditionEntity;
import info.unterrainer.presserl.media.RenditionKind;
import info.unterrainer.presserl.section.SectionEntity;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Read-only queries for the reader. Status and live-revision filter sit in the query, so only live
 * revisions of published articles can ever reach a template.
 */
@ApplicationScoped
public class ReaderArticles {

    /**
     * Live revisions of published articles with their section and the {@code web}, {@code thumbnail}
     * and {@code print} renditions of their lead image (all {@code null} without one).
     */
    static final String LIVE_PUBLISHED = "select a, r, s, w, t, p from ArticleEntity a "
            + "join ArticleRevisionEntity r on r.articleId = a.id and r.number = a.liveRevision "
            + "join SectionEntity s on s.id = a.sectionId "
            + "left join MediaRenditionEntity w on w.mediaId = r.leadImageMediaId and w.kind = 'web' "
            + "left join MediaRenditionEntity t on t.mediaId = r.leadImageMediaId and t.kind = 'thumbnail' "
            + "left join MediaRenditionEntity p on p.mediaId = r.leadImageMediaId and p.kind = 'print' "
            + "where a.status = :status";

    /**
     * A rendition of a media that is the lead image of at least one live revision of a published
     * article.
     */
    private static final String PUBLISHED_RENDITION = "select m from MediaRenditionEntity m "
            + "where m.mediaId = :id and m.kind = :kind and exists (select 1 from ArticleEntity a "
            + "join ArticleRevisionEntity r on r.articleId = a.id and r.number = a.liveRevision "
            + "where a.status = :status and r.leadImageMediaId = :id)";

    /**
     * The published articles, newest first publication first.
     */
    @WithSession
    public Uni<List<ReaderArticle>> frontPage(int limit) {
        return Panache.getSession().flatMap(session -> session
                .createSelectionQuery(LIVE_PUBLISHED + " order by a.publishedAt desc, a.id desc", Object[].class)
                .setParameter("status", ArticleStatus.PUBLISHED)
                .setMaxResults(limit)
                .getResultList())
                .map(rows -> rows.stream().map(ReaderArticles::toArticle).toList());
    }

    /**
     * The article, if it exists and is published.
     */
    @WithSession
    public Uni<Optional<ReaderArticle>> article(long id) {
        return Panache.getSession().flatMap(session -> session
                .createSelectionQuery(LIVE_PUBLISHED + " and a.id = :id", Object[].class)
                .setParameter("status", ArticleStatus.PUBLISHED)
                .setParameter("id", id)
                .getResultList())
                .map(rows -> rows.stream().findFirst().map(ReaderArticles::toArticle));
    }

    /**
     * The rendition, if media {@code mediaId} is the lead image of a live revision of a published
     * article; never the stored image itself.
     */
    @WithSession
    public Uni<Optional<MediaRenditionEntity>> publishedRendition(long mediaId, RenditionKind kind) {
        return Panache.getSession().flatMap(session -> session
                .createSelectionQuery(PUBLISHED_RENDITION, MediaRenditionEntity.class)
                .setParameter("id", mediaId)
                .setParameter("kind", kind.value())
                .setParameter("status", ArticleStatus.PUBLISHED)
                .getResultList())
                .map(rows -> rows.stream().findFirst());
    }

    /**
     * Every section in position order, for the section bar.
     */
    @WithSession
    public Uni<List<ReaderSection>> sections() {
        return SectionEntity.<SectionEntity>list("order by position, id")
                .map(sections -> sections.stream().map(ReaderSection::of).toList());
    }

    /**
     * Maps a row of {@link #LIVE_PUBLISHED}.
     */
    static ReaderArticle toArticle(Object[] row) {
        ArticleRevisionEntity live = (ArticleRevisionEntity) row[1];
        ReaderImage leadImage = ReaderImage.of(live.leadImageMediaId, live.leadImageCaption,
                (MediaRenditionEntity) row[3], (MediaRenditionEntity) row[4], (MediaRenditionEntity) row[5]);
        return ReaderArticle.of((ArticleEntity) row[0], live, (SectionEntity) row[2], leadImage);
    }
}
