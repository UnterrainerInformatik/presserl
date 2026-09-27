package info.unterrainer.presserl.reader;

import java.util.List;
import java.util.Optional;

import info.unterrainer.presserl.article.ArticleEntity;
import info.unterrainer.presserl.article.ArticleRevisionEntity;
import info.unterrainer.presserl.article.ArticleStatus;
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

    private static final String LIVE_PUBLISHED = "select a, r, s from ArticleEntity a, ArticleRevisionEntity r, "
            + "SectionEntity s where r.articleId = a.id and r.number = a.liveRevision and s.id = a.sectionId "
            + "and a.status = :status";

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
     * Every section in position order, for the section bar.
     */
    @WithSession
    public Uni<List<ReaderSection>> sections() {
        return SectionEntity.<SectionEntity>list("order by position, id")
                .map(sections -> sections.stream().map(ReaderSection::of).toList());
    }

    private static ReaderArticle toArticle(Object[] row) {
        return ReaderArticle.of((ArticleEntity) row[0], (ArticleRevisionEntity) row[1], (SectionEntity) row[2]);
    }
}
