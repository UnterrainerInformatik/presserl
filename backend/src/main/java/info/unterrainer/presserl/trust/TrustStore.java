package info.unterrainer.presserl.trust;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import info.unterrainer.presserl.article.ApprovalLevel;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Reads and writes {@link TrustEntity} rows. Lists are ordered {@code PUBLISHER},
 * {@code EDITOR_IN_CHIEF}, then {@code SECTION_EDITOR} entries by section position (ties by section
 * id).
 */
@ApplicationScoped
public class TrustStore {

    /**
     * Highest level first; {@link List#sort} is stable, so rows already ordered by section position
     * keep that order within {@code SECTION_EDITOR}.
     */
    private static final Comparator<TrustScope> LEVEL_DESCENDING = Comparator.comparing(TrustScope::level,
            Comparator.reverseOrder());

    /**
     * The account's trust entries.
     */
    @WithSession
    public Uni<Set<TrustScope>> scopesOf(String accountId) {
        return TrustEntity.<TrustEntity>list("accountId", accountId)
                .map(rows -> rows.stream().map(TrustEntity::scope).collect(Collectors.toUnmodifiableSet()));
    }

    /**
     * The trust entries of the accounts by account id, in one query; accounts without entries are
     * missing.
     */
    @WithSession
    public Uni<Map<String, Set<TrustScope>>> scopesOf(Collection<String> accountIds) {
        if (accountIds.isEmpty()) {
            return Uni.createFrom().item(Map.of());
        }
        return TrustEntity.<TrustEntity>list("accountId in ?1", accountIds)
                .map(rows -> rows.stream().collect(Collectors.groupingBy(row -> row.accountId,
                        Collectors.mapping(TrustEntity::scope, Collectors.toUnmodifiableSet()))));
    }

    /**
     * Every account's trust entries by account id, each ordered as described above.
     */
    @WithSession
    public Uni<Map<String, List<TrustScope>>> byAccount() {
        return Panache.getSession().flatMap(session -> session.createSelectionQuery(
                "select t.accountId, t.level, t.sectionId from TrustEntity t "
                        + "left join SectionEntity s on s.id = t.sectionId order by s.position, s.id",
                Object[].class)
                .getResultList())
                .map(rows -> {
                    Map<String, List<TrustScope>> trusts = new HashMap<>();
                    rows.forEach(row -> trusts.computeIfAbsent((String) row[0], id -> new ArrayList<>())
                            .add(new TrustScope((ApprovalLevel) row[1], (Long) row[2])));
                    trusts.values().forEach(scopes -> scopes.sort(LEVEL_DESCENDING));
                    return trusts;
                });
    }

    /**
     * Stores the entry unless it exists; an existing entry keeps its setter and time. A racing insert
     * of the same entry counts as existing.
     *
     * @return whether a row was inserted
     */
    @WithTransaction
    public Uni<Boolean> set(String accountId, TrustScope scope, String setBy) {
        return Panache.getSession().flatMap(session -> session.createNativeQuery(
                "insert into trust (account_id, level, section_id, set_by, set_at) values (?1, ?2, ?3, ?4, ?5) "
                        + "on conflict do nothing")
                .setParameter(1, accountId)
                .setParameter(2, scope.level().name())
                .setParameter(3, scope.sectionId())
                .setParameter(4, setBy)
                .setParameter(5, Instant.now().truncatedTo(ChronoUnit.MILLIS))
                .executeUpdate())
                .map(count -> count > 0);
    }

    /**
     * Deletes the entry.
     *
     * @return whether a row was deleted
     */
    @WithTransaction
    public Uni<Boolean> clear(String accountId, TrustScope scope) {
        Uni<Long> deleted = scope.sectionId() == null
                ? TrustEntity.delete("accountId = ?1 and level = ?2 and sectionId is null", accountId, scope.level())
                : TrustEntity.delete("accountId = ?1 and level = ?2 and sectionId = ?3", accountId, scope.level(),
                        scope.sectionId());
        return deleted.map(count -> count > 0);
    }
}
