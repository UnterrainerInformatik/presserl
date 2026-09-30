package info.unterrainer.presserl.media;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

import org.hibernate.reactive.mutiny.Mutiny;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;

import info.unterrainer.presserl.api.FieldError;
import info.unterrainer.presserl.article.ArticleEntity;
import info.unterrainer.presserl.article.ArticleRevisionEntity;
import info.unterrainer.presserl.article.ArticleRevisionMediaEntity;
import info.unterrainer.presserl.article.AuthorDto;
import info.unterrainer.presserl.article.SectionRefDto;
import info.unterrainer.presserl.section.Newsroom;
import info.unterrainer.presserl.section.SectionEntity;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.runtime.Startup;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;

/**
 * Uploading, listing, reading and editing media for writers ({@code WRITE_ARTICLES}). Decoding, encoding and the
 * blocking object-store calls run on worker threads; at most
 * {@code presserl.media.max-concurrent-processing} images are processed at once. An upload writes the
 * objects (image and renditions) first and the rows second, in one transaction, so no row ever
 * points to a missing object; a failure removes the objects already written.
 */
@Startup
@ApplicationScoped
public class MediaService {

    private static final Logger LOG = Logger.getLogger(MediaService.class);
    static final int DEFAULT_PAGE_SIZE = 60;
    static final int MAX_PAGE_SIZE = 200;

    @Inject
    MediaConfig config;

    @Inject
    MediaStore store;

    @Inject
    Vertx vertx;

    private final MediaProcessor processor = new MediaProcessor();
    private long maxBytes;
    private Semaphore processing;

    /**
     * Fails startup when {@code presserl.media.max-size} is unreadable or above 60M.
     */
    @PostConstruct
    void init() {
        maxBytes = MediaLimits.maxBytes(config.maxSize());
        processing = new Semaphore(Math.max(1, config.maxConcurrentProcessing()), true);
    }

    public Uni<MediaView> upload(Newsroom newsroom, Path file, long size) {
        requireMediaUser(newsroom);
        if (size == 0) {
            throw MediaException.invalid("the file is empty");
        }
        if (size > maxBytes) {
            throw MediaException.tooLarge("the file is larger than " + config.maxSize());
        }
        return vertx.executeBlocking(Uni.createFrom().item(() -> store(process(file))))
                .flatMap(stored -> insert(newsroom, stored)
                        .onFailure().call(e -> vertx.executeBlocking(Uni.createFrom().item(() -> {
                            discard(stored.keys(), "the media record failed");
                            return null;
                        }))));
    }

    public Uni<MediaView> get(Newsroom newsroom, long id) {
        requireMediaUser(newsroom);
        return Panache.withSession(() -> MediaEntity.<MediaEntity>findById(id)
                .onItem().ifNull().failWith(() -> MediaException.notFound("media " + id + " does not exist"))
                .flatMap(media -> MediaRenditionEntity.<MediaRenditionEntity>list("mediaId", id)
                        .map(renditions -> new MediaView(media, renditions))));
    }

    /**
     * The stored image; when {@code ifNoneMatch} matches its {@link #etag}, only the row is read and the
     * content has no bytes.
     */
    public Uni<Content> content(Newsroom newsroom, long id, String ifNoneMatch) {
        return get(newsroom, id).map(MediaView::media).flatMap(media -> {
            String etag = etag(media);
            if (matches(ifNoneMatch, etag)) {
                return Uni.createFrom().item(Content.notModified(etag));
            }
            return vertx.executeBlocking(Uni.createFrom()
                    .item(() -> new Content(media.contentType, store.get(media.objectKey), etag)));
        });
    }

    /**
     * The bytes of one rendition; {@code 404} for an unknown kind and for a rendition not produced yet.
     * Like {@link #content}, a matching {@code ifNoneMatch} skips reading the object store.
     */
    public Uni<Content> rendition(Newsroom newsroom, long id, String kind, String ifNoneMatch) {
        requireMediaUser(newsroom);
        RenditionKind parsed = RenditionKind.parse(kind)
                .orElseThrow(() -> MediaException.notFound("unknown rendition '" + kind + "'"));
        return Panache.withSession(() -> MediaRenditionEntity
                .<MediaRenditionEntity>findById(new MediaRenditionId(id, parsed.value()))
                .onItem().ifNull().failWith(() -> MediaException.notFound(
                        "media " + id + " has no rendition " + parsed.value()))
                .flatMap(rendition -> MediaEntity.<MediaEntity>findById(id)
                        .map(media -> new RenditionOf(rendition, etag(media)))))
                .flatMap(found -> {
                    if (matches(ifNoneMatch, found.etag())) {
                        return Uni.createFrom().item(Content.notModified(found.etag()));
                    }
                    return vertx.executeBlocking(Uni.createFrom().item(() -> new Content(
                            found.rendition().contentType, store.get(found.rendition().objectKey), found.etag())));
                });
    }

    private record RenditionOf(MediaRenditionEntity rendition, String etag) {
    }

    /**
     * {@code "{id}-{version}"}, quotes included: an edit changes it.
     */
    static String etag(MediaEntity media) {
        return "\"" + media.id + "-" + media.version + "\"";
    }

    /**
     * Whether an {@code If-None-Match} header (a list of entity tags, weak ones included, or {@code *})
     * matches {@code etag}.
     */
    static boolean matches(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        for (String candidate : ifNoneMatch.split(",")) {
            String tag = candidate.strip();
            if (tag.startsWith("W/")) {
                tag = tag.substring(2);
            }
            if (tag.equals("*") || tag.equals(etag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The newspaper's media, newest first, at most {@code limit} with an id below {@code before}
     * ({@code null} for the first page), each with the number of articles using it.
     *
     * @param limitParam  1 to {@value #MAX_PAGE_SIZE}, {@code null} for {@value #DEFAULT_PAGE_SIZE}
     * @param beforeParam a positive media id or {@code null}
     */
    public Uni<MediaPageDto> list(Newsroom newsroom, String limitParam, String beforeParam) {
        requireMediaUser(newsroom);
        List<FieldError> errors = new ArrayList<>();
        int limit = DEFAULT_PAGE_SIZE;
        if (limitParam != null) {
            Long parsed = parsePositive(limitParam);
            if (parsed == null || parsed > MAX_PAGE_SIZE) {
                errors.add(new FieldError("limit", "must be an integer from 1 to " + MAX_PAGE_SIZE));
            } else {
                limit = parsed.intValue();
            }
        }
        Long before = null;
        if (beforeParam != null) {
            before = parsePositive(beforeParam);
            if (before == null) {
                errors.add(new FieldError("before", "must be a positive media id"));
            }
        }
        if (!errors.isEmpty()) {
            throw MediaException.invalid(errors);
        }
        int pageSize = limit;
        Long beforeId = before;
        return Panache.withSession(() -> Panache.getSession().flatMap(session -> {
            Mutiny.SelectionQuery<Object[]> query = session.createSelectionQuery("select m, (select count(distinct "
                    + "u.articleId) from ArticleRevisionMediaEntity u where u.mediaId = m.id) from MediaEntity m"
                    + (beforeId != null ? " where m.id < :before" : "") + " order by m.id desc", Object[].class);
            if (beforeId != null) {
                query.setParameter("before", beforeId);
            }
            return query.setMaxResults(pageSize + 1).getResultList();
        }).flatMap(rows -> {
            List<Object[]> page = rows.subList(0, Math.min(pageSize, rows.size()));
            Long next = rows.size() > pageSize ? ((MediaEntity) page.getLast()[0]).id : null;
            List<Long> ids = page.stream().map(row -> ((MediaEntity) row[0]).id).toList();
            Uni<List<MediaRenditionEntity>> renditions = ids.isEmpty() ? Uni.createFrom().item(List.of())
                    : MediaRenditionEntity.list("mediaId in ?1", ids);
            return renditions.map(all -> new MediaPageDto(page.stream().map(row -> {
                MediaEntity media = (MediaEntity) row[0];
                return MediaListItemDto.of(new MediaView(media,
                        all.stream().filter(r -> r.mediaId.equals(media.id)).toList()), (Long) row[1]);
            }).toList(), next));
        }));
    }

    private static Long parsePositive(String value) {
        try {
            long parsed = Long.parseLong(value.strip());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * The articles using the media (as lead image or in the body) in any revision, most recently
     * changed first, and whether the caller may edit the media.
     */
    public Uni<MediaUsageDto> usage(Newsroom newsroom, long id) {
        requireMediaUser(newsroom);
        return Panache.withSession(() -> findMedia(id)
                .flatMap(media -> blockingUse(id)
                        .flatMap(blocking -> uses(id)
                                .map(articles -> new MediaUsageDto(mayEdit(newsroom, media, blocking), articles)))));
    }

    private static Uni<MediaEntity> findMedia(long id) {
        return MediaEntity.<MediaEntity>findById(id)
                .onItem().ifNull().failWith(() -> MediaException.notFound("media " + id + " does not exist"));
    }

    private static Uni<List<MediaUsageDto.ArticleUseDto>> uses(long id) {
        return Panache.getSession().flatMap(session -> session.createSelectionQuery(
                "select u.articleId, u.number from ArticleRevisionMediaEntity u where u.mediaId = :id",
                Object[].class)
                .setParameter("id", id)
                .getResultList()
                .flatMap(revisions -> {
                    Map<Long, Set<Integer>> numbers = new HashMap<>();
                    revisions.forEach(row -> numbers.computeIfAbsent((Long) row[0], k -> new HashSet<>())
                            .add((Integer) row[1]));
                    if (numbers.isEmpty()) {
                        return Uni.createFrom().item(List.<MediaUsageDto.ArticleUseDto>of());
                    }
                    return session.createSelectionQuery("select a, r, s from ArticleEntity a, "
                            + "ArticleRevisionEntity r, SectionEntity s where s.id = a.sectionId and "
                            + "r.articleId = a.id and r.number = (select max(r2.number) from "
                            + "ArticleRevisionEntity r2 where r2.articleId = a.id) and a.id in :ids "
                            + "order by a.updatedAt desc, a.id desc", Object[].class)
                            .setParameter("ids", numbers.keySet())
                            .getResultList()
                            .map(rows -> rows.stream().map(row -> {
                                ArticleEntity article = (ArticleEntity) row[0];
                                ArticleRevisionEntity latest = (ArticleRevisionEntity) row[1];
                                Set<Integer> using = numbers.get(article.id);
                                boolean live = article.liveRevision != null && using.contains(article.liveRevision);
                                boolean isLatest = using.contains(latest.number);
                                return new MediaUsageDto.ArticleUseDto(article.id, latest.headline,
                                        SectionRefDto.of((SectionEntity) row[2]),
                                        new AuthorDto(article.authorUsername, article.authorDisplayName),
                                        article.status, article.pendingLevel, article.publishedAt,
                                        article.updatedAt, live, isLatest, !live && !isLatest);
                            }).toList());
                }));
    }

    /**
     * Whether an article's live revision, or the latest revision of an article waiting for approval,
     * uses the media as lead image or in its body; such a use keeps the uploader from editing it.
     */
    private static Uni<Boolean> blockingUse(long id) {
        return Panache.getSession().flatMap(session -> session.createSelectionQuery(
                "select count(u) from ArticleEntity a, ArticleRevisionMediaEntity u where u.articleId = a.id "
                        + "and u.mediaId = :id and (u.number = a.liveRevision or (a.pendingLevel is "
                        + "not null and u.number = (select max(r2.number) from ArticleRevisionEntity r2 "
                        + "where r2.articleId = a.id)))",
                Long.class)
                .setParameter("id", id)
                .getSingleResult()
                .map(count -> count > 0));
    }

    /**
     * Administrators edit every media; the uploader their own while no {@link #blockingUse} exists.
     */
    static boolean mayEdit(Newsroom newsroom, MediaEntity media, boolean blockingUse) {
        return newsroom.isAdministrator() || (media.uploaderSub.equals(newsroom.user().sub()) && !blockingUse);
    }

    /**
     * Crops and pixelates the stored image and replaces it and its renditions under the same id. The
     * new objects are written first; the row is then locked, permission and version are checked again,
     * the row and renditions are updated and the old keys go to the trash in one transaction. After
     * commit the old objects are deleted (best effort; {@link MediaTrashSweeper} retries). Any failure
     * removes the new objects and leaves the media unchanged.
     */
    public Uni<MediaView> edit(Newsroom newsroom, long id, JsonNode json) {
        requireMediaUser(newsroom);
        MediaEditValidator.EditRequest request = MediaEditValidator.parse(json);
        return Panache.withSession(() -> findMedia(id)
                .flatMap(media -> blockingUse(id).map(blocking -> {
                    checkEditable(newsroom, media, blocking, request);
                    return media.objectKey;
                })))
                .flatMap(key -> vertx.executeBlocking(Uni.createFrom().item(() -> store(underProcessingLimit(
                        () -> processor.edit(store.get(key), request.crop(), request.ellipses()))))))
                .flatMap(stored -> replace(newsroom, id, request, stored)
                        .onFailure().call(e -> vertx.executeBlocking(Uni.createFrom().item(() -> {
                            discard(stored.keys(), "the edit was not saved");
                            return null;
                        }))))
                .flatMap(replaced -> deleteReplaced(replaced.oldKeys()).replaceWith(replaced.view()));
    }

    private record Replaced(MediaView view, List<String> oldKeys) {
    }

    private static void checkEditable(Newsroom newsroom, MediaEntity media, boolean blockingUse,
            MediaEditValidator.EditRequest request) {
        if (!mayEdit(newsroom, media, blockingUse)) {
            throw MediaException.forbidden(newsroom.isAdministrator() || media.uploaderSub.equals(newsroom.user().sub())
                    ? "media " + media.id + " is used by a live article or one waiting for approval"
                    : "only publishers, editors-in-chief and the uploader may edit media " + media.id);
        }
        if (request.version() != media.version) {
            throw MediaException.conflict(MediaEditValidator.VERSION, "media " + media.id + " was changed meanwhile");
        }
        MediaEditValidator.checkBounds(request, media.width, media.height);
    }

    private Uni<Replaced> replace(Newsroom newsroom, long id, MediaEditValidator.EditRequest request, Stored stored) {
        MediaProcessor.Processed processed = stored.processed();
        List<String> renditionKeys = stored.keys().subList(1, stored.keys().size());
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return Panache.withTransaction(() -> MediaEntity.<MediaEntity>findById(id, LockModeType.PESSIMISTIC_WRITE)
                .onItem().ifNull().failWith(() -> MediaException.notFound("media " + id + " does not exist"))
                .flatMap(media -> blockingUse(id).flatMap(blocking -> {
                    checkEditable(newsroom, media, blocking, request);
                    return MediaRenditionEntity.<MediaRenditionEntity>list("mediaId", id).flatMap(existing -> {
                        List<String> oldKeys = new ArrayList<>();
                        oldKeys.add(media.objectKey);
                        media.objectKey = stored.keys().getFirst();
                        media.contentType = processed.contentType();
                        media.width = processed.width();
                        media.height = processed.height();
                        media.byteSize = processed.bytes().length;
                        media.version++;
                        List<MediaRenditionEntity> rows = new ArrayList<>();
                        List<MediaRenditionEntity> added = new ArrayList<>();
                        for (MediaRenditionEntity fresh : renditionRows(id, processed.renditions(), renditionKeys)) {
                            MediaRenditionEntity row = existing.stream().filter(r -> r.kind.equals(fresh.kind))
                                    .findFirst().orElse(null);
                            if (row == null) {
                                row = fresh;
                                added.add(row);
                            } else {
                                oldKeys.add(row.objectKey);
                                row.objectKey = fresh.objectKey;
                                row.contentType = fresh.contentType;
                                row.width = fresh.width;
                                row.height = fresh.height;
                                row.byteSize = fresh.byteSize;
                            }
                            rows.add(row);
                        }
                        List<MediaObjectTrashEntity> trash = oldKeys.stream().map(key -> {
                            MediaObjectTrashEntity entry = new MediaObjectTrashEntity();
                            entry.objectKey = key;
                            entry.createdAt = now;
                            return entry;
                        }).toList();
                        // persist() refuses an empty list
                        Uni<Void> persisted = added.isEmpty() ? Uni.createFrom().voidItem()
                                : MediaRenditionEntity.persist(added);
                        return persisted.flatMap(ignored -> MediaObjectTrashEntity.persist(trash))
                                .replaceWith(new Replaced(new MediaView(media, rows), List.copyOf(oldKeys)));
                    });
                })));
    }

    /**
     * Deletes the objects of a replaced image and their trash rows; whatever fails stays in the trash
     * for {@link MediaTrashSweeper}.
     */
    private Uni<Void> deleteReplaced(List<String> keys) {
        return vertx.executeBlocking(Uni.createFrom().item(() -> deleteObjects(keys)))
                .flatMap(deleted -> deleted.isEmpty() ? Uni.createFrom().voidItem()
                        : Panache.withTransaction(() -> MediaObjectTrashEntity.delete("objectKey in ?1", deleted))
                                .replaceWithVoid())
                .onFailure().recoverWithUni(e -> {
                    LOG.warnf("Could not clear the trash after a media edit, retried later: %s", e.toString());
                    return Uni.createFrom().voidItem();
                });
    }

    /**
     * Deletes the objects; a missing object counts as deleted.
     *
     * @return the keys whose objects are gone
     */
    List<String> deleteObjects(List<String> keys) {
        List<String> deleted = new ArrayList<>();
        for (String key : keys) {
            try {
                store.delete(key);
                deleted.add(key);
            } catch (RuntimeException e) {
                LOG.warnf("Could not delete replaced media object %s, retried later: %s", key, e.toString());
            }
        }
        return deleted;
    }

    /**
     * Stored bytes, their content type and entity tag; {@code bytes} is {@code null} when the caller's
     * copy is current ({@code 304}).
     */
    public record Content(String contentType, byte[] bytes, String etag) {

        static Content notModified(String etag) {
            return new Content(null, null, etag);
        }
    }

    /**
     * The processed upload and its object keys: the image first, then the renditions in the order of
     * {@link MediaProcessor.Processed#renditions()}.
     */
    private record Stored(MediaProcessor.Processed processed, List<String> keys) {
    }

    MediaProcessor.Processed process(Path file) {
        return underProcessingLimit(() -> processor.process(file));
    }

    /**
     * Runs one decode/encode job while holding a processing slot.
     */
    <T> T underProcessingLimit(Supplier<T> job) {
        try {
            processing.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting to process an image", e);
        }
        try {
            return job.get();
        } finally {
            processing.release();
        }
    }

    MediaProcessor processor() {
        return processor;
    }

    /**
     * Puts the image and its renditions; when one put fails, the objects already written are deleted.
     */
    private Stored store(MediaProcessor.Processed processed) {
        List<String> keys = new ArrayList<>();
        try {
            keys.add(store.put(processed.bytes(), processed.contentType(), processed.extension()));
            keys.addAll(putRenditions(processed.renditions()));
        } catch (RuntimeException e) {
            discard(keys, "storing the upload failed");
            throw e;
        }
        return new Stored(processed, keys);
    }

    /**
     * Puts the renditions in order; when one put fails, the ones already written are deleted.
     *
     * @return their object keys, in the order of {@code renditions}
     */
    List<String> putRenditions(List<MediaProcessor.Rendition> renditions) {
        List<String> keys = new ArrayList<>();
        try {
            for (MediaProcessor.Rendition rendition : renditions) {
                keys.add(store.put(rendition.bytes(), rendition.contentType(), rendition.extension()));
            }
        } catch (RuntimeException e) {
            discard(keys, "storing a rendition failed");
            throw e;
        }
        return keys;
    }

    private Uni<MediaView> insert(Newsroom newsroom, Stored stored) {
        MediaProcessor.Processed processed = stored.processed();
        MediaEntity media = new MediaEntity();
        media.objectKey = stored.keys().getFirst();
        media.contentType = processed.contentType();
        media.width = processed.width();
        media.height = processed.height();
        media.byteSize = processed.bytes().length;
        media.uploaderSub = newsroom.user().sub();
        media.uploaderUsername = newsroom.user().username();
        media.uploaderDisplayName = newsroom.user().displayName();
        // PostgreSQL keeps microseconds
        media.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return Panache.withTransaction(() -> media.<MediaEntity>persist().flatMap(persisted -> {
            List<MediaRenditionEntity> rows = renditionRows(persisted.id, processed.renditions(),
                    stored.keys().subList(1, stored.keys().size()));
            return MediaRenditionEntity.persist(rows).replaceWith(new MediaView(persisted, rows));
        }));
    }

    /**
     * The rows for renditions stored under {@code keys} (same order).
     */
    static List<MediaRenditionEntity> renditionRows(long mediaId, List<MediaProcessor.Rendition> renditions,
            List<String> keys) {
        List<MediaRenditionEntity> rows = new ArrayList<>();
        for (int i = 0; i < renditions.size(); i++) {
            MediaProcessor.Rendition rendition = renditions.get(i);
            MediaRenditionEntity row = new MediaRenditionEntity();
            row.mediaId = mediaId;
            row.kind = rendition.kind().value();
            row.objectKey = keys.get(i);
            row.contentType = rendition.contentType();
            row.width = rendition.width();
            row.height = rendition.height();
            row.byteSize = rendition.bytes().length;
            rows.add(row);
        }
        return rows;
    }

    /**
     * Best effort: an orphaned object is harmless, it is only logged.
     */
    void discard(List<String> keys, String reason) {
        for (String key : keys) {
            try {
                store.delete(key);
            } catch (RuntimeException e) {
                LOG.warnf("Could not delete media object %s after %s: %s", key, reason, e.toString());
            }
        }
    }

    /**
     * Writers and sectionless reporters ({@link Newsroom#mayUseMedia()}, {@code USE_MEDIA}).
     */
    static void requireMediaUser(Newsroom newsroom) {
        if (!newsroom.mayUseMedia()) {
            throw MediaException.forbidden("only writers and sectionless reporters may upload and read media");
        }
    }
}
