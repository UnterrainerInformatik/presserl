package info.unterrainer.presserl.account;

import java.time.Instant;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A pending request of a user to delete their own account. The account is the Keycloak user id
 * (token {@code sub}).
 */
@Entity
@Table(name = "account_deletion_request")
public class AccountDeletionRequestEntity extends PanacheEntityBase {

    @Id
    @Column(name = "account_id", columnDefinition = "text")
    public String accountId;

    @Column(name = "requested_at", nullable = false)
    public Instant requestedAt;
}
