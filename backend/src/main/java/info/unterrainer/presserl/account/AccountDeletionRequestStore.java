package info.unterrainer.presserl.account;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.stream.Collectors;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Reads and writes {@link AccountDeletionRequestEntity} rows.
 */
@ApplicationScoped
public class AccountDeletionRequestStore {

    /**
     * When the account requested its deletion; {@code null} without a pending request.
     */
    @WithSession
    public Uni<Instant> requestedAt(String accountId) {
        return AccountDeletionRequestEntity.<AccountDeletionRequestEntity>findById(accountId)
                .map(row -> row == null ? null : row.requestedAt);
    }

    /**
     * The time of every pending request by account id.
     */
    @WithSession
    public Uni<Map<String, Instant>> byAccount() {
        return AccountDeletionRequestEntity.<AccountDeletionRequestEntity>listAll()
                .map(rows -> rows.stream().collect(Collectors.toUnmodifiableMap(row -> row.accountId,
                        row -> row.requestedAt)));
    }

    /**
     * Stores the request unless one is pending; a pending request keeps its time. A racing insert
     * counts as pending.
     *
     * @return the time of the pending request
     */
    @WithTransaction
    public Uni<Instant> request(String accountId) {
        return Panache.getSession().flatMap(session -> session.createNativeQuery(
                "insert into account_deletion_request (account_id, requested_at) values (?1, ?2) "
                        + "on conflict do nothing")
                .setParameter(1, accountId)
                .setParameter(2, Instant.now().truncatedTo(ChronoUnit.MILLIS))
                .executeUpdate())
                .flatMap(count -> requestedAt(accountId));
    }

    /**
     * Removes the pending request, if any.
     *
     * @return whether a row was deleted
     */
    @WithTransaction
    public Uni<Boolean> withdraw(String accountId) {
        return AccountDeletionRequestEntity.deleteById(accountId);
    }
}
