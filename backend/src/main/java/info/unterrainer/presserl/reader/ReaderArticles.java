package info.unterrainer.presserl.reader;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import info.unterrainer.presserl.article.ArticleEntity;
import info.unterrainer.presserl.article.ArticleRevisionEntity;
import info.unterrainer.presserl.article.ArticleStatus;
import info.unterrainer.presserl.media.MediaEntity;
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
     * and {@code print} renditions of their lead image and its media version (all {@code null} without
     * one).
     */
    static final String LIVE_PUBLISHED = "select a, r, s, w, t, p, m.version from ArticleEntity a "
            + "join ArticleRevisionEntity r on r.articleId = a.id and r.number = a.liveRevision "
            + "join SectionEntity s on s.id = a.sectionId "
            + "left join MediaRenditionEntity w on w.mediaId = r.leadImageMediaId and w.kind = 'web' "
            + "left join MediaRenditionEntity t on t.mediaId = r.leadImageMediaId and t.kind = 'thumbnail' "
            + "left join MediaRenditionEntity p on p.mediaId = r.leadImageMediaId and p.kind = 'print' "
            + "left join MediaEntity m on m.id = r.leadImageMediaId "
            + "where a.status = :status";

    /**
     * A rendition of a media that is used (as lead image or in the body) by at least one live revision
     * of a published article.
     */
    private static final String PUBLISHED_RENDITION = "select m from MediaRenditionEntity m "
            + "where m.mediaId = :id and m.kind = :kind and exists (select 1 from ArticleEntity a "
            + "join ArticleRevisionMediaEntity u on u.articleId = a.id and u.number = a.liveRevision "
            + "where a.status = :status and u.mediaId = :id)";

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
     * The rendition, if media {@code mediaId} is used by a live revision of a published article; never
     * the stored image itself.
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
     * The images of the image blocks of {@code bodies} by media id, with renditions and version and an
     * empty caption (the caption is the block's). Media that no longer exist or whose web and
     * thumbnail renditions are not produced yet are missing from the map. The bodies must be live
     * revisions of published articles.
     */
    @WithSession
    public Uni<Map<Long, ReaderImage>> bodyImages(List<JsonNode> bodies) {
        Set<Long> ids = new HashSet<>();
        for (JsonNode body : bodies) {
            for (JsonNode block : body.path("blocks")) {
                if ("image".equals(block.path("type").asText())) {
                    ids.add(block.path("mediaId").asLong());
                }
            }
        }
        if (ids.isEmpty()) {
            return Uni.createFrom().item(Map.of());
        }
        // one after the other: both queries use the request's reactive session
        return MediaEntity.<MediaEntity>list("id in ?1", ids).flatMap(media -> MediaRenditionEntity
                .<MediaRenditionEntity>list("mediaId in ?1", ids)
                .map(renditions -> {
                    Map<Long, ReaderImage> images = new HashMap<>();
                    for (MediaEntity m : media) {
                        ReaderImage image = ReaderImage.of(m.id, "", m.version,
                                rendition(renditions, m.id, RenditionKind.WEB),
                                rendition(renditions, m.id, RenditionKind.THUMBNAIL),
                                rendition(renditions, m.id, RenditionKind.PRINT));
                        if (image != null) {
                            images.put(m.id, image);
                        }
                    }
                    return images;
                }));
    }

    private static MediaRenditionEntity rendition(List<MediaRenditionEntity> renditions, long mediaId,
            RenditionKind kind) {
        return renditions.stream().filter(r -> r.mediaId == mediaId && r.kind.equals(kind.value())).findFirst()
                .orElse(null);
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
                (Long) row[6], (MediaRenditionEntity) row[3], (MediaRenditionEntity) row[4],
                (MediaRenditionEntity) row[5]);
        return ReaderArticle.of((ArticleEntity) row[0], live, (SectionEntity) row[2], leadImage);
    }
}
