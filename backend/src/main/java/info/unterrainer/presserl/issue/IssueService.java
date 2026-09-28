package info.unterrainer.presserl.issue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.JDBCException;
import org.hibernate.reactive.mutiny.Mutiny;
import org.jboss.logging.Logger;

import info.unterrainer.presserl.article.ArticleService;
import info.unterrainer.presserl.article.ArticleView;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import io.vertx.pgclient.PgException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Issue operations. Access is checked by the callers; this class enforces the data rules: numbers
 * assigned once (next after the highest), an article in at most one issue, positions
 * {@code 0, 1, 2, …} after every change of the article list, published issues not deletable.
 * Article membership is written with bulk updates, so assembling an issue does not change the
 * articles' versions.
 */
@ApplicationScoped
public class IssueService {

    private static final Logger LOG = Logger.getLogger(IssueService.class);
    private static final String UNIQUE_VIOLATION = "23505";

    @Inject
    ArticleService articles;

    /**
     * An issue summary: the issue, the number of its articles (any status) and whether it is the
     * newest.
     */
    public record Summary(IssueEntity issue, long articleCount, boolean newest) {
    }

    /**
     * An issue with its articles in issue order.
     */
    public record Details(IssueEntity issue, boolean newest, List<ArticleView> articles) {
    }

    /**
     * All issues, highest number first, in one query.
     */
    @WithSession
    public Uni<List<Summary>> list() {
        return Panache.getSession().flatMap(session -> session.createSelectionQuery(
                "select i, (select count(a) from ArticleEntity a where a.issueId = i.id) from IssueEntity i "
                        + "order by i.number desc",
                Object[].class).getResultList())
                .map(rows -> {
                    List<Summary> summaries = new ArrayList<>();
                    for (int i = 0; i < rows.size(); i++) {
                        summaries.add(new Summary((IssueEntity) rows.get(i)[0], (Long) rows.get(i)[1], i == 0));
                    }
                    return List.copyOf(summaries);
                });
    }

    /**
     * @throws IssueException {@code 404} for an unknown id
     */
    @WithSession
    public Uni<Details> get(long id) {
        return find(id).flatMap(this::details);
    }

    /**
     * Creates an issue, not published and without articles, numbered after the highest existing one.
     *
     * @throws IssueException {@code 409} when a concurrent creation took the number; nothing is
     *                        created then
     */
    @WithTransaction
    public Uni<Details> create(IssueDateInput input, String by) {
        return Panache.getSession().flatMap(session -> session.createSelectionQuery(
                "select coalesce(max(i.number), 0) from IssueEntity i", Integer.class).getSingleResult())
                .flatMap(highest -> {
                    IssueEntity issue = new IssueEntity();
                    issue.number = highest + 1;
                    issue.publicationDate = input.publicationDate();
                    issue.createdAt = now();
                    return issue.<IssueEntity>persist().call(Panache::flush);
                })
                .onFailure(IssueService::isUniqueViolation)
                .transform(e -> IssueException.conflict("another issue was created at the same time; try again"))
                .invoke(issue -> LOG.infof("Issue %d (id %d) created by '%s'", issue.number, issue.id, by))
                .map(issue -> new Details(issue, true, List.of()));
    }

    /**
     * Sets or clears the publication date, for published issues as well.
     */
    @WithTransaction
    public Uni<Details> setDate(long id, IssueDateInput input) {
        return find(id).invoke(issue -> issue.publicationDate = input.publicationDate())
                .call(Panache::flush)
                .flatMap(this::details);
    }

    /**
     * Makes the issue visible to readers; publishing a published issue keeps its {@code publishedAt}.
     * Articles are not touched.
     */
    @WithTransaction
    public Uni<Details> publish(long id, String by) {
        return find(id).invoke(issue -> {
            if (!issue.published) {
                issue.published = true;
                issue.publishedAt = now();
            }
            LOG.infof("Issue %d (id %d) published by '%s'", issue.number, issue.id, by);
        }).call(Panache::flush).flatMap(this::details);
    }

    /**
     * Hides the issue from readers again. Articles are not touched.
     */
    @WithTransaction
    public Uni<Details> unpublish(long id, String by) {
        return find(id).invoke(issue -> {
            issue.published = false;
            issue.publishedAt = null;
            LOG.infof("Issue %d (id %d) unpublished by '%s'", issue.number, issue.id, by);
        }).call(Panache::flush).flatMap(this::details);
    }

    /**
     * Makes exactly {@code articleIds} the issue's articles in this order: listed articles of other
     * issues move here, unlisted articles of this issue belong to no issue afterwards.
     *
     * @param articleIds without repetition ({@link IssueRequestValidator#articleIds})
     * @throws IssueException {@code 400 articleIds} naming unknown articles; nothing is changed then
     */
    @WithTransaction
    public Uni<Details> setArticles(long id, List<Long> articleIds) {
        return find(id).call(issue -> requireArticles(articleIds))
                .call(issue -> Panache.getSession().flatMap(session -> {
                    Mutiny.MutationQuery release = session.createMutationQuery(
                            "update ArticleEntity a set a.issueId = null, a.issuePosition = null where a.issueId = :issue"
                                    + (articleIds.isEmpty() ? "" : " and a.id not in :ids"))
                            .setParameter("issue", issue.id);
                    if (!articleIds.isEmpty()) {
                        release.setParameter("ids", articleIds);
                    }
                    return release.executeUpdate().flatMap(released -> assign(session, issue, articleIds, 0));
                }))
                .flatMap(this::details);
    }

    /**
     * Deletes an issue that is not published; its articles belong to no issue afterwards and are
     * otherwise unchanged.
     *
     * @throws IssueException {@code 409} for a published issue; nothing is changed then
     */
    @WithTransaction
    public Uni<Void> delete(long id, String by) {
        return find(id).invoke(issue -> {
            if (issue.published) {
                throw IssueException.conflict("issue " + issue.number + " is published; unpublish it first");
            }
        })
                .call(issue -> Panache.getSession().flatMap(session -> session.createMutationQuery(
                        "update ArticleEntity a set a.issueId = null, a.issuePosition = null where a.issueId = :issue")
                        .setParameter("issue", issue.id)
                        .executeUpdate()))
                .call(issue -> issue.delete())
                .call(Panache::flush)
                .invoke(issue -> LOG.infof("Issue %d (id %d) deleted by '%s'", issue.number, issue.id, by))
                .replaceWithVoid();
    }

    private Uni<Details> details(IssueEntity issue) {
        return Panache.getSession().flatMap(session -> session.createSelectionQuery(
                "select max(i.number) from IssueEntity i", Integer.class).getSingleResult())
                .flatMap(highest -> articles.listInIssue(issue)
                        .map(views -> new Details(issue, highest == issue.number, views)));
    }

    private static Uni<Void> assign(Mutiny.Session session, IssueEntity issue,
            List<Long> articleIds, int position) {
        if (position == articleIds.size()) {
            return Uni.createFrom().voidItem();
        }
        return session.createMutationQuery(
                "update ArticleEntity a set a.issueId = :issue, a.issuePosition = :position where a.id = :id")
                .setParameter("issue", issue.id)
                .setParameter("position", position)
                .setParameter("id", articleIds.get(position))
                .executeUpdate()
                .flatMap(ignored -> assign(session, issue, articleIds, position + 1));
    }

    private static Uni<Void> requireArticles(List<Long> articleIds) {
        if (articleIds.isEmpty()) {
            return Uni.createFrom().voidItem();
        }
        return Panache.getSession().flatMap(session -> session.createSelectionQuery(
                "select a.id from ArticleEntity a where a.id in :ids", Long.class)
                .setParameter("ids", articleIds)
                .getResultList())
                .invoke(found -> {
                    Set<Long> known = new HashSet<>(found);
                    List<Long> unknown = articleIds.stream().filter(articleId -> !known.contains(articleId)).toList();
                    if (!unknown.isEmpty()) {
                        throw IssueException.invalid(IssueRequestValidator.ARTICLE_IDS,
                                "unknown article" + (unknown.size() > 1 ? "s " : " ")
                                        + String.join(", ", unknown.stream().map(String::valueOf).toList()));
                    }
                })
                .replaceWithVoid();
    }

    private static Uni<IssueEntity> find(long id) {
        return IssueEntity.<IssueEntity>findById(id).onItem().ifNull().failWith(() -> IssueException.notFound(id));
    }

    private static boolean isUniqueViolation(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof PgException pg && UNIQUE_VIOLATION.equals(pg.getSqlState())
                    || cause instanceof JDBCException jdbc && UNIQUE_VIOLATION.equals(jdbc.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
