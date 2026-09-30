package info.unterrainer.presserl.article;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.hibernate.reactive.mutiny.Mutiny;

import com.fasterxml.jackson.databind.JsonNode;

import info.unterrainer.presserl.api.FieldError;
import info.unterrainer.presserl.auth.NewspaperRole;
import info.unterrainer.presserl.issue.IssueEntity;
import info.unterrainer.presserl.media.MediaEntity;
import info.unterrainer.presserl.newspaper.NewspaperConfig;
import info.unterrainer.presserl.newspaper.NewspaperSettings;
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

    @Inject
    NewspaperSettings settings;

    /**
     * The newspaper settings {@link ArticlePolicy} follows.
     */
    public Uni<ArticleRules> rules() {
        return settings.effective().map(ArticleRules::of);
    }

    /**
     * The contributors of the articles by article id, in one query: the distinct authors of their
     * revisions above the live revision (of all revisions while never published); the article's
     * author when there are none, i.e. an offline article without unpublished changes.
     */
    @WithSession
    public Uni<Map<Long, Set<String>>> contributors(Collection<ArticleEntity> articles) {
        List<Long> ids = articles.stream().map(article -> article.id).toList();
        return Panache.getSession().flatMap(session -> session.createSelectionQuery(
                "select distinct r.articleId, r.authorSub from ArticleRevisionEntity r, ArticleEntity a "
                        + "where a.id = r.articleId and r.articleId in :ids "
                        + "and (a.liveRevision is null or r.number > a.liveRevision)",
                Object[].class)
                .setParameter("ids", ids)
                .getResultList())
                .map(rows -> {
                    Map<Long, Set<String>> contributors = new HashMap<>();
                    rows.forEach(row -> contributors.computeIfAbsent((Long) row[0], id -> new HashSet<>())
                            .add((String) row[1]));
                    articles.forEach(article -> contributors.computeIfAbsent(article.id,
                            id -> new HashSet<>(Set.of(article.authorSub))));
                    return contributors;
                });
    }

    /**
     * Summaries of the articles visible to the user with their latest revision, section and issue, in
     * the order {@code sort}, in one query.
     *
     * @param status     only articles in this status, if not {@code null}
     * @param mine       only articles authored by the user
     * @param pending    only articles with a pending submission
     * @param excludeOwn only articles authored by someone else
     */
    @WithSession
    public Uni<List<ArticleView>> list(Newsroom newsroom, ArticleStatus status, boolean mine, boolean pending,
            boolean excludeOwn, ArticleSort sort) {
        List<Long> editedSections = newsroom.sectionRoles().entrySet().stream()
                .filter(entry -> entry.getValue() == SectionRole.SECTION_EDITOR)
                .map(Map.Entry::getKey)
                .toList();
        boolean restricted = !newsroom.isAdministrator();
        StringBuilder hql = new StringBuilder(
                "select a, r, s, i from ArticleEntity a left join IssueEntity i on i.id = a.issueId, "
                        + "ArticleRevisionEntity r, SectionEntity s where s.id = a.sectionId and ")
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
        hql.append(" order by ").append(sort.orderBy());
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
                        (SectionEntity) row[2], (IssueEntity) row[3]))
                .toList());
    }

    /**
     * The articles of an issue with their latest revision and section in issue order (position, ties
     * by id), in one query. Access is checked by the caller.
     */
    @WithSession
    public Uni<List<ArticleView>> listInIssue(IssueEntity issue) {
        return Panache.getSession().flatMap(session -> session.createSelectionQuery(
                "select a, r, s from ArticleEntity a, ArticleRevisionEntity r, SectionEntity s where s.id = a.sectionId and "
                        + LATEST_REVISION + " and a.issueId = :issue order by a.issuePosition, a.id",
                Object[].class)
                .setParameter("issue", issue.id)
                .getResultList())
                .map(rows -> rows.stream()
                        .map(row -> new ArticleView((ArticleEntity) row[0], (ArticleRevisionEntity) row[1],
                                (SectionEntity) row[2], issue))
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
        return target.call(section -> requireMedia(content)).flatMap(section -> {
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
            revision.writtenBy(newsroom.user());
            revision.apply(content);
            revision.createdAt = now;
            revision.updatedAt = now;
            return article.<ArticleEntity>persist()
                    .flatMap(persisted -> {
                        revision.articleId = persisted.id;
                        return revision.<ArticleRevisionEntity>persist();
                    })
                    .flatMap(persisted -> flushed(new ArticleView(article, revision, section, null)));
        });
    }

    /**
     * Moves the article to {@code sectionId} if given, and overwrites the latest revision while it
     * has never been published and the user wrote it, otherwise adds a new one written by the user.
     * Content equal to the latest revision touches no revision, so moving a published article
     * creates no revision. A corrector (see {@link ArticlePolicy}) saves like the author but keeps
     * the section.
     *
     * @param sectionId the new section, {@code null} to keep the current one
     * @param version   the article version the client last received
     */
    @WithTransaction
    public Uni<ArticleView> save(Newsroom newsroom, long id, ArticleContent content, Long sectionId, long version) {
        return load(newsroom, id).flatMap(view -> facts(view).flatMap(facts -> {
            require(ArticleAction.EDIT, newsroom, view, facts);
            ArticleEntity article = view.article();
            requireVersion(article, version);
            boolean correction = !newsroom.user().sub().equals(article.authorSub);
            if (correction && sectionId != null && !sectionId.equals(article.sectionId)) {
                throw ArticleException.forbidden(ArticleContentValidator.SECTION_ID,
                        "a correction keeps the article's section");
            }
            Uni<SectionEntity> target = sectionId == null || sectionId.equals(article.sectionId)
                    ? Uni.createFrom().item(view.section())
                    : writableSection(newsroom, sectionId);
            return target.call(section -> requireMedia(content)).flatMap(section -> {
                Instant now = now();
                if (section != null) {
                    article.sectionId = section.id;
                }
                article.updatedAt = now;
                ArticleRevisionEntity latest = view.revision();
                if (latest.holds(content)) {
                    return flushed(new ArticleView(article, latest, section, view.issue()));
                }
                if (latest.publishedAt == null && latest.authorSub.equals(newsroom.user().sub())) {
                    latest.apply(content);
                    latest.updatedAt = now;
                    return flushed(new ArticleView(article, latest, section, view.issue()));
                }
                ArticleRevisionEntity next = new ArticleRevisionEntity();
                next.articleId = article.id;
                next.number = latest.number + 1;
                next.writtenBy(newsroom.user());
                next.apply(content);
                next.createdAt = now;
                next.updatedAt = now;
                return next.persist().flatMap(persisted -> flushed(new ArticleView(article, next, section, view.issue())));
            });
        }));
    }

    /**
     * Deletes a never-published article with all its revisions and reviews, also while it waits for
     * approval.
     */
    @WithTransaction
    public Uni<Void> delete(Newsroom newsroom, long id) {
        return load(newsroom, id).flatMap(view -> {
            require(ArticleAction.DELETE, newsroom, view, Facts.AUTHOR_ONLY);
            // revisions and reviews go with the article (ON DELETE CASCADE)
            return view.article().delete();
        });
    }

    /**
     * Makes the latest revision live when the article's chain is empty; requires a headline.
     */
    @WithTransaction
    public Uni<ArticleView> publish(Newsroom newsroom, long id) {
        return load(newsroom, id).flatMap(view -> facts(view)
                .flatMap(facts -> {
                    require(ArticleAction.PUBLISH, newsroom, view, facts);
                    requireHeadline(view, "publishing");
                    return appendTarget(view).flatMap(target -> flushed(goLive(view, now(), target)));
                }));
    }

    /**
     * Starts a submission: the article waits for the lowest level of its chain (over all
     * contributors). A never-published article becomes {@code SUBMITTED}; a published or offline one
     * keeps its status and live revision. Requires a headline.
     */
    @WithTransaction
    public Uni<ArticleView> submit(Newsroom newsroom, long id) {
        return load(newsroom, id).flatMap(view -> facts(view)
                .flatMap(facts -> {
                    require(ArticleAction.SUBMIT, newsroom, view, facts);
                    requireHeadline(view, "submitting");
                    ArticleEntity article = view.article();
                    Staffing staffed = facts.staffing();
                    article.pendingLevel = ApprovalChain.next(Optional.empty(), article.sectionId,
                            staffed.contributorsOf(article), staffed, article.locked).orElseThrow();
                    if (article.liveRevision == null) {
                        article.status = ArticleStatus.SUBMITTED;
                    }
                    article.updatedAt = now();
                    return flushed(view);
                }));
    }

    /**
     * Approves the pending submission up to the approver's level: the article then waits for the
     * next level above it in its chain (over all contributors) or, when none remains, goes live with
     * its latest revision.
     *
     * @param version the article version the approver saw; {@code null} to skip the check
     */
    @WithTransaction
    public Uni<ArticleView> approve(Newsroom newsroom, long id, Long version) {
        return load(newsroom, id).flatMap(view -> facts(view).flatMap(facts -> {
            require(ArticleAction.APPROVE, newsroom, view, facts);
            requireVersion(view.article(), version);
            Staffing staffed = facts.staffing();
            ArticleEntity article = view.article();
            ApprovalLevel next = ApprovalChain.next(ApprovalChain.approverLevel(newsroom, article.sectionId),
                    article.sectionId, staffed.contributorsOf(article), staffed, article.locked).orElse(null);
            Uni<AppendTarget> target = next == null ? appendTarget(view) : Uni.createFrom().nullItem();
            return target.flatMap(appendTo -> {
                Instant now = now();
                ArticleReviewEntity review = review(newsroom, view, ReviewDecision.APPROVED, null, now);
                article.pendingLevel = next;
                ArticleView result = next == null ? goLive(view, now, appendTo) : view;
                article.updatedAt = now;
                return review.persist().flatMap(persisted -> flushed(result));
            });
        }));
    }

    /**
     * Ends the pending submission with a note: a {@code SUBMITTED} article returns to {@code DRAFT},
     * a published or offline one keeps its status and live revision.
     *
     * @param note    validated by {@link RejectRequestValidator}
     * @param version the article version the reviewer saw; {@code null} to skip the check
     */
    @WithTransaction
    public Uni<ArticleView> reject(Newsroom newsroom, long id, String note, Long version) {
        return load(newsroom, id).flatMap(view -> facts(view).flatMap(facts -> {
            require(ArticleAction.REJECT, newsroom, view, facts);
            requireVersion(view.article(), version);
            Instant now = now();
            ArticleReviewEntity review = review(newsroom, view, ReviewDecision.REJECTED, note, now);
            endSubmission(view.article(), now);
            return review.persist().flatMap(persisted -> flushed(view));
        }));
    }

    /**
     * The author ends their pending submission, like a rejection but without a review entry.
     */
    @WithTransaction
    public Uni<ArticleView> withdraw(Newsroom newsroom, long id) {
        return load(newsroom, id).flatMap(view -> {
            require(ArticleAction.WITHDRAW, newsroom, view, Facts.AUTHOR_ONLY);
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
            require(ArticleAction.TAKE_OFFLINE, newsroom, view, Facts.AUTHOR_ONLY);
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
            require(ArticleAction.UNLOCK, newsroom, view, Facts.AUTHOR_ONLY);
            view.article().locked = false;
            view.article().updatedAt = now();
            return flushed(view);
        });
    }

    /**
     * Sets or clears the front-page weight. Editors-in-chief and publishers only, in every status; no
     * revision, no change to approval state, lock or {@code version}: a targeted update, then the
     * article is re-read.
     *
     * @param weight 1–999, {@code null} clears; validated by the caller
     */
    @WithTransaction
    public Uni<ArticleView> setFrontPageWeight(Newsroom newsroom, long id, Integer weight) {
        return load(newsroom, id).flatMap(view -> {
            if (!newsroom.isAdministrator()) {
                throw ArticleException.forbidden("only an editor-in-chief or a publisher may set the front-page weight");
            }
            return Panache.getSession().flatMap(session -> session
                    .createMutationQuery("update ArticleEntity a set a.frontPageWeight = :weight where a.id = :id")
                    .setParameter("weight", weight)
                    .setParameter("id", id)
                    .executeUpdate()
                    .flatMap(updated -> session.refresh(view.article())))
                    .replaceWith(view);
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
                .flatMap(revision -> view(article, revision)));
    }

    public record Revisions(ArticleEntity article, List<ArticleRevisionEntity> revisions) {
    }

    /**
     * The revision's lead image with the media's dimensions; {@code null} for none.
     */
    @WithSession
    public Uni<LeadImageDto> leadImage(ArticleRevisionEntity revision) {
        ArticleContent.LeadImage leadImage = revision.leadImage();
        if (leadImage == null) {
            return Uni.createFrom().nullItem();
        }
        return MediaEntity.<MediaEntity>findById(leadImage.mediaId()).map(media -> new LeadImageDto(media.id,
                leadImage.caption(), media.width, media.height));
    }

    /**
     * Makes the latest revision live: status {@code PUBLISHED}, publication timestamps set on first
     * publication, nothing pending, not locked; appended to {@code target} if given.
     *
     * @param target from {@link #appendTarget}, queried before any change so no auto-flush writes the
     *               article twice
     */
    private static ArticleView goLive(ArticleView view, Instant now, AppendTarget target) {
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
        if (target == null) {
            return view;
        }
        article.issueId = target.issue().id;
        article.issuePosition = target.position();
        return new ArticleView(article, view.revision(), view.section(), target.issue());
    }

    /**
     * Where a publication appends the article: the end of the issue with the highest number,
     * published or not, when this is the article's first publication and it belongs to no issue;
     * {@code null} otherwise and when no issue exists. Two concurrent appends may get the same
     * position, which is harmless: articles of an issue are ordered by position, then id.
     */
    private static Uni<AppendTarget> appendTarget(ArticleView view) {
        if (view.article().publishedAt != null || view.article().issueId != null) {
            return Uni.createFrom().nullItem();
        }
        return Panache.getSession().flatMap(session -> session.createSelectionQuery(
                "select i, (select max(a.issuePosition) from ArticleEntity a where a.issueId = i.id) "
                        + "from IssueEntity i order by i.number desc",
                Object[].class)
                .setMaxResults(1)
                .getResultList())
                .map(rows -> {
                    if (rows.isEmpty()) {
                        return null;
                    }
                    Integer last = (Integer) rows.get(0)[1];
                    return new AppendTarget((IssueEntity) rows.get(0)[0], last == null ? 0 : last + 1);
                });
    }

    private record AppendTarget(IssueEntity issue, int position) {
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
                .flatMap(latest -> view(article, latest)));
    }

    private static Uni<ArticleView> view(ArticleEntity article, ArticleRevisionEntity revision) {
        return section(article).flatMap(section -> issue(article)
                .map(issue -> new ArticleView(article, revision, section, issue)));
    }

    private static Uni<IssueEntity> issue(ArticleEntity article) {
        return article.issueId == null ? Uni.createFrom().nullItem() : IssueEntity.findById(article.issueId);
    }

    private static Uni<SectionEntity> section(ArticleEntity article) {
        return article.sectionId == null ? Uni.createFrom().nullItem() : SectionEntity.findById(article.sectionId);
    }

    /**
     * The media of the lead image and of every image block of the body must exist (any media,
     * whoever uploaded it); checked in one query.
     *
     * @throws ArticleException {@code 400} naming {@code leadImage.mediaId} and every
     *                          {@code body.blocks[i].mediaId} whose media is unknown
     */
    private static Uni<Void> requireMedia(ArticleContent content) {
        Map<String, Long> uses = new LinkedHashMap<>();
        if (content.leadImage() != null) {
            uses.put(ArticleContentValidator.LEAD_IMAGE_MEDIA_ID, content.leadImage().mediaId());
        }
        JsonNode blocks = content.body().path("blocks");
        for (int i = 0; i < blocks.size(); i++) {
            JsonNode block = blocks.get(i);
            if ("image".equals(block.path("type").asText())) {
                uses.put("body.blocks[" + i + "].mediaId", block.get("mediaId").asLong());
            }
        }
        if (uses.isEmpty()) {
            return Uni.createFrom().voidItem();
        }
        Set<Long> ids = new HashSet<>(uses.values());
        return Panache.getSession().flatMap(session -> session
                .createSelectionQuery("select m.id from MediaEntity m where m.id in :ids", Long.class)
                .setParameter("ids", ids)
                .getResultList())
                .invoke(existing -> {
                    List<FieldError> errors = uses.entrySet().stream()
                            .filter(use -> !existing.contains(use.getValue()))
                            .map(use -> new FieldError(use.getKey(), "media " + use.getValue() + " does not exist"))
                            .toList();
                    if (!errors.isEmpty()) {
                        throw ArticleException.invalid(errors);
                    }
                }).replaceWithVoid();
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

    /**
     * What {@link ArticlePolicy} needs besides the user and the article.
     */
    private record Facts(Staffing staffing, ArticleRules rules) {

        /**
         * For actions whose verdict asks neither the chain nor the rules: DELETE, WITHDRAW,
         * TAKE_OFFLINE and UNLOCK.
         */
        static final Facts AUTHOR_ONLY = new Facts(Staffing.NOT_NEEDED, new ArticleRules(false));
    }

    private Uni<Facts> facts(ArticleView view) {
        return staffing.forArticles(List.of(view.article()))
                .flatMap(staffed -> rules().map(rules -> new Facts(staffed, rules)));
    }

    /**
     * @param version the article version the client last received; {@code null} skips the check
     */
    private static void requireVersion(ArticleEntity article, Long version) {
        if (version != null && article.version != version) {
            throw ArticleException.conflict("article was changed in the meantime (version " + article.version
                    + ", sent " + version + "); reload it");
        }
    }

    private static void require(ArticleAction action, Newsroom newsroom, ArticleView view, Facts facts) {
        boolean author = newsroom.user().sub().equals(view.article().authorSub);
        switch (ArticlePolicy.verdict(action, newsroom, view.article(), view.revision().number, facts.staffing(),
                facts.rules())) {
            case ALLOWED -> {
            }
            case FORBIDDEN -> throw ArticleException.forbidden(switch (action) {
                case EDIT -> author ? "you may not write in this article's section"
                        : "only the author may edit this article; a higher level may correct it while corrections "
                                + "are allowed, during a review only from the level it waits for";
                case DELETE -> "only the author may delete this article, while they may write in its section";
                case SUBMIT -> "only the author or a correcting contributor may submit this article, while an "
                        + "approval level applies; publish it instead";
                case PUBLISH -> "only the author or a correcting contributor may publish this article, while no "
                        + "approval level applies; submit it instead";
                case WITHDRAW -> "only the author may withdraw this submission";
                case APPROVE -> "you may not approve this article at the level it waits for";
                case REJECT -> "you may not reject this article at the level it waits for";
                case TAKE_OFFLINE -> "you may not take this article offline";
                case UNLOCK -> "only a publisher may unlock this article";
            });
            case CONFLICT -> throw ArticleException.conflict(switch (action) {
                case EDIT -> author ? "this article waits for approval and cannot be edited; withdraw the submission first"
                        : "a draft can only be edited by its author";
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
