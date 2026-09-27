package info.unterrainer.presserl.article;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.hibernate.reactive.mutiny.Mutiny;

import info.unterrainer.presserl.newspaper.NewspaperConfig;
import info.unterrainer.presserl.section.Newsroom;
import info.unterrainer.presserl.section.SectionEntity;
import info.unterrainer.presserl.section.SectionRole;
import info.unterrainer.presserl.section.SectionService;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Article operations. Articles the user does not see ({@link ArticlePolicy#visible}) are reported
 * as not existing. Every mutating operation asks {@link ArticlePolicy} first and fails with an
 * {@link ArticleException}; nothing is written in that case.
 */
@ApplicationScoped
public class ArticleService {

    private static final String LATEST_REVISION = "r.articleId = a.id and r.number = "
            + "(select max(r2.number) from ArticleRevisionEntity r2 where r2.articleId = a.id)";

    @Inject
    NewspaperConfig config;

    @Inject
    SectionService sections;

    /**
     * Summaries of the articles visible to the user with their latest revision and section, newest
     * change first, in one query.
     *
     * @param status only articles in this status, if not {@code null}
     * @param mine   only articles authored by the user
     */
    @WithSession
    public Uni<List<ArticleView>> list(Newsroom newsroom, ArticleStatus status, boolean mine) {
        List<Long> editedSections = newsroom.sectionRoles().entrySet().stream()
                .filter(entry -> entry.getValue() == SectionRole.SECTION_EDITOR)
                .map(Map.Entry::getKey)
                .toList();
        boolean restricted = !newsroom.isAdministrator();
        StringBuilder hql = new StringBuilder(
                "select a, r, s from ArticleEntity a, ArticleRevisionEntity r, SectionEntity s where s.id = a.sectionId and ")
                .append(LATEST_REVISION);
        if (status != null) {
            hql.append(" and a.status = :status");
        }
        if (mine) {
            hql.append(" and a.authorSub = :sub");
        }
        if (restricted) {
            hql.append(editedSections.isEmpty() ? " and a.authorSub = :viewer"
                    : " and (a.authorSub = :viewer or a.sectionId in :editedSections)");
        }
        hql.append(" order by a.updatedAt desc, a.id desc");
        return Panache.getSession().flatMap(session -> {
            Mutiny.SelectionQuery<Object[]> query = session.createSelectionQuery(hql.toString(), Object[].class);
            if (status != null) {
                query.setParameter("status", status);
            }
            if (mine) {
                query.setParameter("sub", newsroom.user().sub());
            }
            if (restricted) {
                query.setParameter("viewer", newsroom.user().sub());
                if (!editedSections.isEmpty()) {
                    query.setParameter("editedSections", editedSections);
                }
            }
            return query.getResultList();
        }).map(rows -> rows.stream()
                .map(row -> new ArticleView((ArticleEntity) row[0], (ArticleRevisionEntity) row[1],
                        (SectionEntity) row[2]))
                .toList());
    }

    @WithSession
    public Uni<ArticleView> get(Newsroom newsroom, long id) {
        return load(newsroom, id);
    }

    /**
     * Creates a draft in {@code sectionId} or, when {@code null}, in the default section if the
     * user may write there, else in the first section by position the user may write in; when no
     * section exists at all, the default section is created.
     */
    @WithTransaction
    public Uni<ArticleView> create(Newsroom newsroom, ArticleContent content, Long sectionId) {
        Uni<SectionEntity> target = sectionId != null ? writableSection(newsroom, sectionId) : fallbackSection(newsroom);
        return target.flatMap(section -> {
            Instant now = now();
            ArticleEntity article = new ArticleEntity();
            article.status = ArticleStatus.DRAFT;
            article.authorSub = newsroom.user().sub();
            article.authorUsername = newsroom.user().username();
            article.authorDisplayName = newsroom.user().displayName();
            article.sectionId = section.id;
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
                    .flatMap(persisted -> flushed(new ArticleView(article, revision, section)));
        });
    }

    /**
     * Moves the article to {@code sectionId} if given, and overwrites the latest revision while it
     * has never been published, otherwise adds a new one. Content equal to the latest revision
     * touches no revision, so moving a published article creates no revision.
     *
     * @param sectionId the new section, {@code null} to keep the current one
     * @param version   the article version the client last received
     */
    @WithTransaction
    public Uni<ArticleView> save(Newsroom newsroom, long id, ArticleContent content, Long sectionId, long version) {
        return load(newsroom, id).flatMap(view -> {
            require(ArticleAction.EDIT, newsroom, view);
            ArticleEntity article = view.article();
            if (article.version != version) {
                throw ArticleException.conflict("article was changed in the meantime (version " + article.version
                        + ", sent " + version + "); reload it");
            }
            Uni<SectionEntity> target = sectionId == null || sectionId.equals(article.sectionId)
                    ? Uni.createFrom().item(view.section())
                    : writableSection(newsroom, sectionId);
            return target.flatMap(section -> {
                Instant now = now();
                if (section != null) {
                    article.sectionId = section.id;
                }
                article.updatedAt = now;
                ArticleRevisionEntity latest = view.revision();
                if (latest.holds(content)) {
                    return flushed(new ArticleView(article, latest, section));
                }
                if (latest.publishedAt == null) {
                    latest.apply(content);
                    latest.updatedAt = now;
                    return flushed(new ArticleView(article, latest, section));
                }
                ArticleRevisionEntity next = new ArticleRevisionEntity();
                next.articleId = article.id;
                next.number = latest.number + 1;
                next.apply(content);
                next.createdAt = now;
                next.updatedAt = now;
                return next.persist().flatMap(persisted -> flushed(new ArticleView(article, next, section)));
            });
        });
    }

    /**
     * Deletes a never-published article with all its revisions.
     */
    @WithTransaction
    public Uni<Void> delete(Newsroom newsroom, long id) {
        return load(newsroom, id).flatMap(view -> {
            require(ArticleAction.DELETE, newsroom, view);
            // revisions go with the article (ON DELETE CASCADE)
            return view.article().delete();
        });
    }

    /**
     * Makes the latest revision live; requires a headline.
     */
    @WithTransaction
    public Uni<ArticleView> publish(Newsroom newsroom, long id) {
        return load(newsroom, id).flatMap(view -> {
            require(ArticleAction.PUBLISH, newsroom, view);
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
    public Uni<ArticleView> takeOffline(Newsroom newsroom, long id) {
        return load(newsroom, id).flatMap(view -> {
            require(ArticleAction.TAKE_OFFLINE, newsroom, view);
            view.article().status = ArticleStatus.OFFLINE;
            view.article().updatedAt = now();
            return flushed(view);
        });
    }

    /**
     * The article's revisions, newest first.
     */
    @WithSession
    public Uni<Revisions> revisions(Newsroom newsroom, long id) {
        return find(newsroom, id).flatMap(article -> ArticleRevisionEntity
                .<ArticleRevisionEntity>list("articleId = ?1 order by number desc", id)
                .map(revisions -> new Revisions(article, revisions)));
    }

    @WithSession
    public Uni<ArticleView> revision(Newsroom newsroom, long id, int number) {
        return find(newsroom, id).flatMap(article -> ArticleRevisionEntity
                .<ArticleRevisionEntity>findById(new ArticleRevisionId(id, number))
                .onItem().ifNull().failWith(() -> ArticleException.notFound(
                        "article " + id + " has no revision " + number))
                .flatMap(revision -> section(article).map(section -> new ArticleView(article, revision, section))));
    }

    public record Revisions(ArticleEntity article, List<ArticleRevisionEntity> revisions) {
    }

    /**
     * The article if it exists and the user sees it; otherwise {@code 404} alike.
     */
    private static Uni<ArticleEntity> find(Newsroom newsroom, long id) {
        return ArticleEntity.<ArticleEntity>findById(id).map(article -> {
            if (article == null || !ArticlePolicy.visible(newsroom, article)) {
                throw ArticleException.notFound("article " + id + " does not exist");
            }
            return article;
        });
    }

    private static Uni<ArticleView> load(Newsroom newsroom, long id) {
        return find(newsroom, id).flatMap(article -> ArticleRevisionEntity
                .<ArticleRevisionEntity>find("articleId = ?1 order by number desc", id).firstResult()
                .flatMap(latest -> section(article).map(section -> new ArticleView(article, latest, section))));
    }

    private static Uni<SectionEntity> section(ArticleEntity article) {
        return article.sectionId == null ? Uni.createFrom().nullItem() : SectionEntity.findById(article.sectionId);
    }

    /**
     * @throws ArticleException {@code 400 sectionId} for an unknown section, {@code 403 sectionId}
     *                          when the user may not write in it
     */
    private static Uni<SectionEntity> writableSection(Newsroom newsroom, long sectionId) {
        return SectionEntity.<SectionEntity>findById(sectionId).map(section -> {
            if (section == null) {
                throw ArticleException.invalid(ArticleContentValidator.SECTION_ID, "unknown section");
            }
            if (!newsroom.mayWriteIn(section.id)) {
                throw ArticleException.forbidden(ArticleContentValidator.SECTION_ID, "you may not write in this section");
            }
            return section;
        });
    }

    /**
     * The section of a create without {@code sectionId}. A writer always may write somewhere, so
     * only an administrator facing no section at all gets the default section created.
     */
    private Uni<SectionEntity> fallbackSection(Newsroom newsroom) {
        String defaultName = config.section().defaultName();
        return sections.findByNameIgnoreCase(defaultName).flatMap(defaultSection -> {
            if (defaultSection != null && newsroom.mayWriteIn(defaultSection.id)) {
                return Uni.createFrom().item(defaultSection);
            }
            return sections.list().flatMap(all -> all.stream()
                    .filter(section -> newsroom.mayWriteIn(section.id))
                    .findFirst()
                    .map(section -> Uni.createFrom().item(section))
                    .orElseGet(() -> {
                        if (!all.isEmpty()) {
                            throw ArticleException.forbidden(ArticleContentValidator.SECTION_ID,
                                    "you may not write in any section");
                        }
                        return sections.ensureSection(defaultName);
                    }));
        });
    }

    /**
     * Flushes so the view carries the incremented version.
     */
    private static Uni<ArticleView> flushed(ArticleView view) {
        return Panache.flush().replaceWith(view);
    }

    private static void require(ArticleAction action, Newsroom newsroom, ArticleView view) {
        switch (ArticlePolicy.verdict(action, newsroom, view.article(), view.revision().number)) {
            case ALLOWED -> {
            }
            case FORBIDDEN -> throw ArticleException.forbidden(switch (action) {
                case EDIT -> "only the author may edit this article, while they may write in its section";
                case DELETE -> "only the author may delete this article, while they may write in its section";
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
