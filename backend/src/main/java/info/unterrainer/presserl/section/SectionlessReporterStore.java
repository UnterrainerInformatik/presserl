package info.unterrainer.presserl.section;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.stream.Collectors;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Reads and writes {@link SectionlessReporterEntity} rows.
 */
@ApplicationScoped
public class SectionlessReporterStore {

    @WithSession
    public Uni<Boolean> isMarked(String accountId) {
        return SectionlessReporterEntity.<SectionlessReporterEntity>findById(accountId).map(row -> row != null);
    }

    /**
     * The account ids carrying the marker.
     */
    @WithSession
    public Uni<Set<String>> marked() {
        return SectionlessReporterEntity.<SectionlessReporterEntity>listAll()
                .map(rows -> rows.stream().map(row -> row.accountId).collect(Collectors.toUnmodifiableSet()));
    }

    /**
     * Sets the marker unless the account carries it; an existing marker keeps its setter and time.
     * A racing insert counts as existing.
     *
     * @return whether a row was inserted
     */
    @WithTransaction
    public Uni<Boolean> set(String accountId, String assignedBy) {
        return Panache.getSession().flatMap(session -> session.createNativeQuery(
                "insert into sectionless_reporter (account_id, assigned_by, assigned_at) values (?1, ?2, ?3) "
                        + "on conflict do nothing")
                .setParameter(1, accountId)
                .setParameter(2, assignedBy)
                .setParameter(3, Instant.now().truncatedTo(ChronoUnit.MILLIS))
                .executeUpdate())
                .map(count -> count > 0);
    }

    /**
     * Removes the marker.
     *
     * @return whether a row was deleted
     */
    @WithTransaction
    public Uni<Boolean> clear(String accountId) {
        return SectionlessReporterEntity.deleteById(accountId);
    }
}
