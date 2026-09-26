package info.unterrainer.presserl.article;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.hibernate.reactive.mutiny.Mutiny;

import info.unterrainer.presserl.auth.CurrentUser;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Article operations. Every mutating operation asks {@link ArticlePolicy} first and fails with an
 * {@link ArticleException}; nothing is written in that case.
 */
@ApplicationScoped
public class ArticleService {

    private static final String LATEST_REVISION = "r.articleId = a.id and r.number = "
            + "(select max(r2.number) from ArticleRevisionEntity r2 where r2.articleId = a.id)";

    /**
     * Summaries of all articles with their latest revision, newest change first, in one query.
     *
     * @param status only articles in this status, if not {@code null}
     * @param mine   only articles authored by {@code user}
     */
    @WithSession
    public Uni<List<ArticleView>> list(CurrentUser user, ArticleStatus status, boolean mine) {
        StringBuilder hql = new StringBuilder("select a, r from ArticleEntity a, ArticleRevisionEntity r where ")
                .append(LATEST_REVISION);
        if (status != null) {
            hql.append(" and a.status = :status");
        }
        if (mine) {
            hql.append(" and a.authorSub = :sub");
        }
        hql.append(" order by a.updatedAt desc, a.id desc");
        return Panache.getSession().flatMap(session -> {
            Mutiny.SelectionQuery<Object[]> query = session.createSelectionQuery(hql.toString(), Object[].class);
            if (status != null) {
                query.setParameter("status", status);
            }
            if (mine) {
                query.setParameter("sub", user.sub());
            }
            return query.getResultList();
        }).map(rows -> rows.stream()
                .map(row -> new ArticleView((ArticleEntity) row[0], (ArticleRevisionEntity) row[1]))
                .toList());
    }

    @WithSession
    public Uni<ArticleView> get(long id) {
        return load(id);
    }

    @WithTransaction
    public Uni<ArticleView> create(CurrentUser user, ArticleContent content) {
        Instant now = now();
        ArticleEntity article = new ArticleEntity();
        article.status = ArticleStatus.DRAFT;
        article.authorSub = user.sub();
        article.authorUsername = user.username();
        article.authorDisplayName = user.displayName();
        article.createdAt = now;
        article.updatedAt = now;
        ArticleRevisionEntity revision = new ArticleRevisionEntity();
        revision.number = 1;
        revision.apply(content);
        revision.createdAt = now;
        revision.updatedAt = now;
        return article.<ArticleEntity>persist()
                .flatMap(persisted -> {
                    revision.articleId = persisted.id;
                    return revision.<ArticleRevisionEntity>persist();
                })
                .flatMap(persisted -> flushed(new ArticleView(article, revision)));
    }

    /**
     * Overwrites the latest revision while it has never been published, otherwise adds a new one.
     *
     * @param version the article version the client last received
     */
    @WithTransaction
    public Uni<ArticleView> save(CurrentUser user, long id, ArticleContent content, long version) {
        return load(id).flatMap(view -> {
            require(ArticleAction.EDIT, user, view);
            ArticleEntity article = view.article();
            if (article.version != version) {
                throw ArticleException.conflict("article was changed in the meantime (version " + article.version
                        + ", sent " + version + "); reload it");
            }
            Instant now = now();
            article.updatedAt = now;
            ArticleRevisionEntity latest = view.revision();
            if (latest.publishedAt == null) {
                latest.apply(content);
                latest.updatedAt = now;
                return flushed(view);
            }
            ArticleRevisionEntity next = new ArticleRevisionEntity();
            next.articleId = article.id;
            next.number = latest.number + 1;
            next.apply(content);
            next.createdAt = now;
            next.updatedAt = now;
            return next.persist().flatMap(persisted -> flushed(new ArticleView(article, next)));
        });
    }

    /**
     * Deletes a never-published article with all its revisions.
     */
    @WithTransaction
    public Uni<Void> delete(CurrentUser user, long id) {
        return load(id).flatMap(view -> {
            require(ArticleAction.DELETE, user, view);
            // revisions go with the article (ON DELETE CASCADE)
            return view.article().delete();
        });
    }

    /**
     * Makes the latest revision live; requires a headline.
     */
    @WithTransaction
    public Uni<ArticleView> publish(CurrentUser user, long id) {
        return load(id).flatMap(view -> {
            require(ArticleAction.PUBLISH, user, view);
            ArticleEntity article = view.article();
            ArticleRevisionEntity latest = view.revision();
            if (latest.headline.isEmpty()) {
                throw ArticleException.invalid("headline", "is required for publishing");
            }
            Instant now = now();
            if (latest.publishedAt == null) {
                latest.publishedAt = now;
            }
            if (article.publishedAt == null) {
                article.publishedAt = now;
            }
            article.liveRevision = latest.number;
            article.status = ArticleStatus.PUBLISHED;
            article.updatedAt = now;
            return flushed(view);
        });
    }

    /**
     * Takes a published article offline; it keeps its live revision.
     */
    @WithTransaction
    public Uni<ArticleView> takeOffline(CurrentUser user, long id) {
        return load(id).flatMap(view -> {
            require(ArticleAction.TAKE_OFFLINE, user, view);
            view.article().status = ArticleStatus.OFFLINE;
            view.article().updatedAt = now();
            return flushed(view);
        });
    }

    /**
     * The article's revisions, newest first.
     */
    @WithSession
    public Uni<Revisions> revisions(long id) {
        return find(id).flatMap(article -> ArticleRevisionEntity
                .<ArticleRevisionEntity>list("articleId = ?1 order by number desc", id)
                .map(revisions -> new Revisions(article, revisions)));
    }

    @WithSession
    public Uni<ArticleView> revision(long id, int number) {
        return find(id).flatMap(article -> ArticleRevisionEntity
                .<ArticleRevisionEntity>findById(new ArticleRevisionId(id, number))
                .onItem().ifNull().failWith(() -> ArticleException.notFound(
                        "article " + id + " has no revision " + number))
                .map(revision -> new ArticleView(article, revision)));
    }

    public record Revisions(ArticleEntity article, List<ArticleRevisionEntity> revisions) {
    }

    private static Uni<ArticleEntity> find(long id) {
        return ArticleEntity.<ArticleEntity>findById(id)
                .onItem().ifNull().failWith(() -> ArticleException.notFound("article " + id + " does not exist"));
    }

    private static Uni<ArticleView> load(long id) {
        return find(id).flatMap(article -> ArticleRevisionEntity
                .<ArticleRevisionEntity>find("articleId = ?1 order by number desc", id).firstResult()
                .map(latest -> new ArticleView(article, latest)));
    }

    /**
     * Flushes so the view carries the incremented version.
     */
    private static Uni<ArticleView> flushed(ArticleView view) {
        return Panache.flush().replaceWith(view);
    }

    private static void require(ArticleAction action, CurrentUser user, ArticleView view) {
        switch (ArticlePolicy.verdict(action, user, view.article(), view.revision().number)) {
            case ALLOWED -> {
            }
            case FORBIDDEN -> throw ArticleException.forbidden(switch (action) {
                case EDIT -> "only the author may edit this article";
                case DELETE -> "only the author may delete this article";
                case PUBLISH -> "only the author may publish this article, and only as a publisher";
                case TAKE_OFFLINE -> "you may not take this article offline";
            });
            case CONFLICT -> throw ArticleException.conflict(switch (action) {
                case EDIT -> "this article cannot be edited in its current state";
                case DELETE -> "a published article cannot be deleted; take it offline instead";
                case PUBLISH -> "this article is already published and has no unpublished changes";
                case TAKE_OFFLINE -> "only a published article can be taken offline";
            });
        }
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
