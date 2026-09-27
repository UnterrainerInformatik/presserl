package info.unterrainer.presserl.section;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.hibernate.reactive.mutiny.Mutiny;

import info.unterrainer.presserl.text.Slugs;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response.Status;

/**
 * Section operations. Access is checked by the callers; this class enforces the data rules: names
 * unique ignoring case, slugs derived once and never changed, positions {@code 0, 1, 2, …}.
 */
@ApplicationScoped
public class SectionService {

    public static final int SLUG_MAX = 40;
    static final String SLUG_FALLBACK = "section";
    static final String NAME_INDEX = "section_name_lower_idx";

    private static final String BY_POSITION = "order by position, id";

    @Inject
    Mutiny.SessionFactory sessionFactory;

    /**
     * All sections by position (ties by id).
     */
    @WithSession
    public Uni<List<SectionEntity>> list() {
        return SectionEntity.list(BY_POSITION);
    }

    /**
     * @throws NotFoundException when there is no section {@code id}
     */
    @WithSession
    public Uni<SectionEntity> get(long id) {
        return find(id);
    }

    /**
     * The section named {@code name}, compared ignoring case; {@code null} when there is none.
     */
    @WithSession
    public Uni<SectionEntity> findByNameIgnoreCase(String name) {
        return Panache.getSession().flatMap(session -> findByName(session, name));
    }

    /**
     * Creates a section at the last position; without a colour it gets the palette colour at
     * (number of sections modulo palette size).
     *
     * @throws SectionException {@code 409 name} when the name is taken
     */
    @WithTransaction
    public Uni<SectionEntity> create(SectionInput input) {
        return Panache.getSession().flatMap(session -> insert(session, input))
                .onFailure(SectionService::isNameTaken).transform(e -> nameTaken());
    }

    /**
     * The section named {@code name} (ignoring case), created by the rules of
     * {@link #create(SectionInput)} with the default colour when there is none. Runs in its own
     * session and transaction, independent of the caller's, so a concurrent creation of the same
     * name (unique index violation) is answered by finding the section again.
     */
    public Uni<SectionEntity> ensureSection(String name) {
        return independently(session -> session.withTransaction(tx -> findByName(session, name)
                .flatMap(found -> found != null ? Uni.createFrom().item(found)
                        : insert(session, new SectionInput(name, null)))))
                .onFailure(SectionService::isRaceForName)
                .recoverWithUni(() -> independently(session -> findByName(session, name)));
    }

    /**
     * Replaces name and colour; slug and position stay.
     *
     * @throws NotFoundException when there is no section {@code id}
     * @throws SectionException  {@code 409 name} when another section has the name
     */
    @WithTransaction
    public Uni<SectionEntity> update(long id, SectionInput input) {
        return find(id)
                .call(section -> Panache.getSession().flatMap(session -> requireFreeName(session, input.name(), id)))
                .invoke(section -> {
                    section.name = input.name();
                    section.color = input.color();
                })
                .call(Panache::flush)
                .onFailure(SectionService::isNameTaken).transform(e -> nameTaken());
    }

    /**
     * Sets the positions in the order of {@code ids}, which must name every section exactly once.
     *
     * @throws SectionException {@code 400 ids} otherwise; nothing is changed then
     */
    @WithTransaction
    public Uni<List<SectionEntity>> reorder(List<Long> ids) {
        return SectionEntity.<SectionEntity>listAll().map(sections -> {
            Map<Long, SectionEntity> byId = sections.stream()
                    .collect(Collectors.toMap(section -> section.id, Function.identity()));
            if (ids.size() != byId.size() || !byId.keySet().equals(new HashSet<>(ids))) {
                throw SectionException.invalid("ids", "must contain every section id exactly once: "
                        + byId.keySet().stream().sorted().toList());
            }
            for (int position = 0; position < ids.size(); position++) {
                byId.get(ids.get(position)).position = position;
            }
            return ids.stream().map(byId::get).toList();
        });
    }

    private static Uni<SectionEntity> find(long id) {
        return SectionEntity.<SectionEntity>findById(id)
                .onItem().ifNull().failWith(NotFoundException::new);
    }

    private Uni<SectionEntity> independently(Function<Mutiny.Session, Uni<SectionEntity>> work) {
        return sessionFactory.openSession().flatMap(session -> work.apply(session).eventually(session::close));
    }

    private static Uni<SectionEntity> findByName(Mutiny.Session session, String name) {
        return session.createSelectionQuery("from SectionEntity where lower(name) = lower(:name)", SectionEntity.class)
                .setParameter("name", name)
                .getSingleResultOrNull();
    }

    private static Uni<SectionEntity> insert(Mutiny.Session session, SectionInput input) {
        return requireFreeName(session, input.name(), null)
                .flatMap(ignored -> session.createSelectionQuery(
                        "select s.slug, s.position from SectionEntity s", Object[].class).getResultList())
                .flatMap(rows -> {
                    SectionEntity section = new SectionEntity();
                    section.name = input.name();
                    section.color = input.color() != null ? input.color() : SectionColor.defaultFor(rows.size());
                    section.position = rows.stream().mapToInt(row -> (Integer) row[1] + 1).max().orElse(0);
                    Set<String> slugs = new HashSet<>();
                    rows.forEach(row -> slugs.add((String) row[0]));
                    section.slug = Slugs.firstFree(Slugs.fold(input.name(), SLUG_MAX, SLUG_FALLBACK), SLUG_MAX,
                            slugs::contains);
                    section.createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
                    return session.persist(section).replaceWith(section);
                })
                .call(session::flush);
    }

    private static Uni<Void> requireFreeName(Mutiny.Session session, String name, Long exceptId) {
        String hql = "select count(s) from SectionEntity s where lower(s.name) = lower(:name)"
                + (exceptId == null ? "" : " and s.id <> :id");
        Mutiny.SelectionQuery<Long> query = session.createSelectionQuery(hql, Long.class).setParameter("name", name);
        if (exceptId != null) {
            query.setParameter("id", exceptId);
        }
        return query.getSingleResult().map(n -> {
            if (n > 0) {
                throw nameTaken();
            }
            return null;
        });
    }

    private static SectionException nameTaken() {
        return SectionException.conflict("name", "is already used by another section");
    }

    /**
     * A concurrent creation or rename won the race for the name (unique index violation).
     */
    private static boolean isRaceForName(Throwable e) {
        return isNameTaken(e) || e instanceof SectionException section && section.status() == Status.CONFLICT;
    }

    private static boolean isNameTaken(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(NAME_INDEX)) {
                return true;
            }
        }
        return false;
    }
}
