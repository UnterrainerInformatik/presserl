package info.unterrainer.presserl.reader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import info.unterrainer.presserl.article.ArticleStatus;
import info.unterrainer.presserl.issue.IssueEntity;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Read-only issue queries for the reader. Only published issues are returned, and of their articles
 * only the live revisions of published ones.
 */
@ApplicationScoped
public class ReaderIssues {

    /**
     * The published issue with the highest number and the number of published issues.
     */
    public record Current(ReaderIssue issue, long publishedCount) {
    }

    /**
     * The issue, if it exists and is published.
     */
    @WithSession
    public Uni<Optional<ReaderIssue>> published(long id) {
        return IssueEntity.<IssueEntity>findById(id)
                .map(issue -> Optional.ofNullable(issue).filter(i -> i.published).map(i -> ReaderIssue.of(i, null)));
    }

    /**
     * The published articles of the issue in issue order (position, ties by id).
     */
    @WithSession
    public Uni<List<ReaderArticle>> articles(long issueId) {
        return Panache.getSession().flatMap(session -> session
                .createSelectionQuery(ReaderArticles.LIVE_PUBLISHED + " and a.issueId = :issue "
                        + "order by a.issuePosition, a.id", Object[].class)
                .setParameter("status", ArticleStatus.PUBLISHED)
                .setParameter("issue", issueId)
                .getResultList())
                .map(rows -> rows.stream().map(ReaderArticles::toArticle).toList());
    }

    /**
     * The published issues, highest number first, each with the live headline of its first published
     * article; two queries.
     */
    @WithSession
    public Uni<List<ReaderIssue>> archive() {
        return Panache.getSession().flatMap(session -> session
                .createSelectionQuery("from IssueEntity i where i.published = true order by i.number desc",
                        IssueEntity.class)
                .getResultList()
                .flatMap(issues -> issues.isEmpty() ? Uni.createFrom().item(List.<ReaderIssue>of())
                        : session.createSelectionQuery("select a.issueId, r.headline from ArticleEntity a "
                                + "join ArticleRevisionEntity r on r.articleId = a.id and r.number = a.liveRevision "
                                + "where a.status = :status and a.issueId in :issues "
                                + "order by a.issueId, a.issuePosition, a.id", Object[].class)
                                .setParameter("status", ArticleStatus.PUBLISHED)
                                .setParameter("issues", issues.stream().map(issue -> issue.id).toList())
                                .getResultList()
                                .map(rows -> {
                                    Map<Long, String> first = new HashMap<>();
                                    rows.forEach(row -> first.putIfAbsent((Long) row[0], (String) row[1]));
                                    return issues.stream()
                                            .map(issue -> ReaderIssue.of(issue, first.get(issue.id)))
                                            .toList();
                                })));
    }

    /**
     * The newest published issue and how many are published; empty when none is.
     */
    @WithSession
    public Uni<Optional<Current>> current() {
        return Panache.getSession().flatMap(session -> session
                .createSelectionQuery("select i, (select count(i2) from IssueEntity i2 where i2.published = true) "
                        + "from IssueEntity i where i.published = true order by i.number desc", Object[].class)
                .setMaxResults(1)
                .getResultList())
                .map(rows -> rows.stream().findFirst()
                        .map(row -> new Current(ReaderIssue.of((IssueEntity) row[0], null), (Long) row[1])));
    }
}
