package info.unterrainer.presserl.article;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.hibernate.reactive.mutiny.Mutiny;

import info.unterrainer.presserl.auth.NewspaperRole;
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

    @Inject
    StaffingService staffing;

    /**
     * Summaries of the articles visible to the user with their latest revision and section, newest
     * change first, in one query.
     *
     * @param status     only articles in this status, if not {@code null}
     * @param mine       only articles authored by the user
     * @param pending    only articles with a pending submission
     * @param excludeOwn only articles authored by someone else
     */
    @WithSession
    public Uni<List<ArticleView>> list(Newsroom newsroom, ArticleStatus status, boolean mine, boolean pending,
            boolean excludeOwn) {
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
        if (pending) {
            hql.append(" and a.pendingLevel is not null");
        }
        if (excludeOwn) {
            hql.append(" and a.authorSub <> :viewer");
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
            if (restricted || excludeOwn) {
                query.setParameter("viewer", newsroom.user().sub());
            }
            if (restricted) {
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
            require(ArticleAction.EDIT, newsroom, view, Staffing.NOT_NEEDED);
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
     * Deletes a never-published article with all its revisions and reviews, also while it waits for
     * approval.
     */
    @WithTransaction
    public Uni<Void> delete(Newsroom newsroom, long id) {
        return load(newsroom, id).flatMap(view -> {
            require(ArticleAction.DELETE, newsroom, view, Staffing.NOT_NEEDED);
            // revisions and reviews go with the article (ON DELETE CASCADE)
            return view.article().delete();
        });
    }

    /**
     * Makes the latest revision live when the author's chain is empty; requires a headline.
     */
    @WithTransaction
    public Uni<ArticleView> publish(Newsroom newsroom, long id) {
        return load(newsroom, id).flatMap(view -> staffing.forArticles(newsroom, List.of(view.article()))
                .flatMap(staffed -> {
                    require(ArticleAction.PUBLISH, newsroom, view, staffed);
                    requireHeadline(view, "publishing");
                    goLive(view, now());
                    return flushed(view);
                }));
    }

    /**
     * Starts a submission: the article waits for the lowest level of the author's chain. A
     * never-published article becomes {@code SUBMITTED}; a published or offline one keeps its status
     * and live revision. Requires a headline.
     */
    @WithTransaction
    public Uni<ArticleView> submit(Newsroom newsroom, long id) {
        return load(newsroom, id).flatMap(view -> staffing.forArticles(newsroom, List.of(view.article()))
                .flatMap(staffed -> {
                    require(ArticleAction.SUBMIT, newsroom, view, staffed);
                    requireHeadline(view, "submitting");
                    ArticleEntity article = view.article();
                    article.pendingLevel = ApprovalChain.next(ApprovalChain.authorLevel(newsroom, article.sectionId),
                            article.sectionId, article.authorSub, staffed, article.locked).orElseThrow();
                    if (article.liveRevision == null) {
                        article.status = ArticleStatus.SUBMITTED;
                    }
                    article.updatedAt = now();
                    return flushed(view);
                }));
    }

    /**
     * Approves the pending submission up to the approver's level: the article then waits for the
     * next staffed level above it that does not trust the author or, when none remains, goes live
     * with its latest revision.
     */
    @WithTransaction
    public Uni<ArticleView> approve(Newsroom newsroom, long id) {
        return load(newsroom, id).flatMap(view -> {
            require(ArticleAction.APPROVE, newsroom, view, Staffing.NOT_NEEDED);
            return staffing.forApproval(view.article().authorSub).flatMap(staffed -> {
                ArticleEntity article = view.article();
                Instant now = now();
                ArticleReviewEntity review = review(newsroom, view, ReviewDecision.APPROVED, null, now);
                article.pendingLevel = ApprovalChain.next(ApprovalChain.approverLevel(newsroom, article.sectionId),
                        article.sectionId, article.authorSub, staffed, article.locked).orElse(null);
                if (article.pendingLevel == null) {
                    goLive(view, now);
                }
                article.updatedAt = now;
                return review.persist().flatMap(persisted -> flushed(view));
            });
        });
    }

    /**
     * Ends the pending submission with a note: a {@code SUBMITTED} article returns to {@code DRAFT},
     * a published or offline one keeps its status and live revision.
     *
     * @param note validated by {@link RejectRequestValidator}
     */
    @WithTransaction
    public Uni<ArticleView> reject(Newsroom newsroom, long id, String note) {
        return load(newsroom, id).flatMap(view -> {
            require(ArticleAction.REJECT, newsroom, view, Staffing.NOT_NEEDED);
            Instant now = now();
            ArticleReviewEntity review = review(newsroom, view, ReviewDecision.REJECTED, note, now);
            endSubmission(view.article(), now);
            return review.persist().flatMap(persisted -> flushed(view));
        });
    }

    /**
     * The author ends their pending submission, like a rejection but without a review entry.
     */
    @WithTransaction
    public Uni<ArticleView> withdraw(Newsroom newsroom, long id) {
        return load(newsroom, id).flatMap(view -> {
            require(ArticleAction.WITHDRAW, newsroom, view, Staffing.NOT_NEEDED);
            endSubmission(view.article(), now());
            return flushed(view);
        });
    }

    /**
     * The article's reviews, newest first.
     */
    @WithSession
    public Uni<List<ArticleReviewEntity>> reviews(Newsroom newsroom, long id) {
        return find(newsroom, id).flatMap(article -> ArticleReviewEntity
                .<ArticleReviewEntity>list("articleId = ?1 order by createdAt desc, id desc", id));
    }

    /**
     * Takes a published article offline; it keeps its live revision and a pending submission. Taken
     * offline by a publisher, it is locked (emergency brake).
     */
    @WithTransaction
    public Uni<ArticleView> takeOffline(Newsroom newsroom, long id) {
        return load(newsroom, id).flatMap(view -> {
            require(ArticleAction.TAKE_OFFLINE, newsroom, view, Staffing.NOT_NEEDED);
            view.article().status = ArticleStatus.OFFLINE;
            view.article().locked = newsroom.user().has(NewspaperRole.PUBLISHER);
            view.article().updatedAt = now();
            return flushed(view);
        });
    }

    /**
     * A publisher lifts the emergency-brake lock; the article stays offline and keeps a pending
     * submission, and the ordinary chain applies again.
     */
    @WithTransaction
    public Uni<ArticleView> unlock(Newsroom newsroom, long id) {
        return load(newsroom, id).flatMap(view -> {
            require(ArticleAction.UNLOCK, newsroom, view, Staffing.NOT_NEEDED);
            view.article().locked = false;
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
     * Makes the latest revision live: status {@code PUBLISHED}, publication timestamps set on first
     * publication, nothing pending, not locked.
     */
    private static void goLive(ArticleView view, Instant now) {
        ArticleEntity article = view.article();
        ArticleRevisionEntity latest = view.revision();
        if (latest.publishedAt == null) {
            latest.publishedAt = now;
        }
        if (article.publishedAt == null) {
            article.publishedAt = now;
        }
        article.liveRevision = latest.number;
        article.status = ArticleStatus.PUBLISHED;
        article.pendingLevel = null;
        article.locked = false;
        article.updatedAt = now;
    }

    private static void endSubmission(ArticleEntity article, Instant now) {
        article.pendingLevel = null;
        if (article.status == ArticleStatus.SUBMITTED) {
            article.status = ArticleStatus.DRAFT;
        }
        article.updatedAt = now;
    }

    private static ArticleReviewEntity review(Newsroom reviewer, ArticleView view, ReviewDecision decision, String note,
            Instant now) {
        ArticleReviewEntity review = new ArticleReviewEntity();
        review.articleId = view.article().id;
        review.revision = view.revision().number;
        review.decision = decision;
        review.level = view.article().pendingLevel;
        review.reviewerSub = reviewer.user().sub();
        review.reviewerUsername = reviewer.user().username();
        review.reviewerDisplayName = reviewer.user().displayName();
        review.note = note;
        review.createdAt = now;
        return review;
    }

    private static void requireHeadline(ArticleView view, String purpose) {
        if (view.revision().headline.isEmpty()) {
            throw ArticleException.invalid("headline", "is required for " + purpose);
        }
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

    private static void require(ArticleAction action, Newsroom newsroom, ArticleView view, Staffing staffed) {
        switch (ArticlePolicy.verdict(action, newsroom, view.article(), view.revision().number, staffed)) {
            case ALLOWED -> {
            }
            case FORBIDDEN -> throw ArticleException.forbidden(switch (action) {
                case EDIT -> "only the author may edit this article, while they may write in its section";
                case DELETE -> "only the author may delete this article, while they may write in its section";
                case SUBMIT -> "only the author may submit this article, while they may write in its section and "
                        + "an approval level applies; publish it instead";
                case PUBLISH -> "only the author may publish this article, while they may write in its section and "
                        + "no approval level applies; submit it instead";
                case WITHDRAW -> "only the author may withdraw this submission";
                case APPROVE -> "you may not approve this article at the level it waits for";
                case REJECT -> "you may not reject this article at the level it waits for";
                case TAKE_OFFLINE -> "you may not take this article offline";
                case UNLOCK -> "only a publisher may unlock this article";
            });
            case CONFLICT -> throw ArticleException.conflict(switch (action) {
                case EDIT -> "this article waits for approval and cannot be edited; withdraw the submission first";
                case DELETE -> "a published article cannot be deleted; take it offline instead";
                case SUBMIT, PUBLISH -> view.article().pendingLevel != null
                        ? "this article already waits for approval"
                        : "this article is already published and has no unpublished changes";
                case WITHDRAW, APPROVE, REJECT -> "this article does not wait for approval";
                case TAKE_OFFLINE -> "only a published article can be taken offline";
                case UNLOCK -> "this article is not locked";
            });
        }
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
