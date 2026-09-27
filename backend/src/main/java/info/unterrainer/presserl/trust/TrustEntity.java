package info.unterrainer.presserl.trust;

import java.time.Instant;

import info.unterrainer.presserl.article.ApprovalLevel;
import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A trust entry: the approval level trusts the account (in the section, for {@code SECTION_EDITOR}),
 * so the level is skipped in the chain of the account's articles. Account and {@code setBy} are
 * Keycloak user ids (token {@code sub}).
 */
@Entity
@Table(name = "trust")
public class TrustEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "account_id", columnDefinition = "text", nullable = false)
    public String accountId;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "text", nullable = false)
    public ApprovalLevel level;

    @Column(name = "section_id")
    public Long sectionId;

    @Column(name = "set_by", columnDefinition = "text", nullable = false)
    public String setBy;

    @Column(name = "set_at", nullable = false)
    public Instant setAt;

    public TrustScope scope() {
        return new TrustScope(level, sectionId);
    }
}
