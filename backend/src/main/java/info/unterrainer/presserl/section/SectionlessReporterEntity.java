package info.unterrainer.presserl.section;

import java.time.Instant;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The sectionless-reporter marker of an account. The account is the Keycloak user id (token
 * {@code sub}), as is {@code assignedBy}.
 */
@Entity
@Table(name = "sectionless_reporter")
public class SectionlessReporterEntity extends PanacheEntityBase {

    @Id
    @Column(name = "account_id", columnDefinition = "text")
    public String accountId;

    @Column(name = "assigned_by", columnDefinition = "text", nullable = false)
    public String assignedBy;

    @Column(name = "assigned_at", nullable = false)
    public Instant assignedAt;
}
