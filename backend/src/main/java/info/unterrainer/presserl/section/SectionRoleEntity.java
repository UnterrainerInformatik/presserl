package info.unterrainer.presserl.section;

import java.time.Instant;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * The role an account holds in a section; at most one per account and section. The account is the
 * Keycloak user id (token {@code sub}), as is {@code assignedBy}.
 */
@Entity
@Table(name = "section_role")
@IdClass(SectionRoleId.class)
public class SectionRoleEntity extends PanacheEntityBase {

    @Id
    @Column(name = "section_id")
    public Long sectionId;

    @Id
    @Column(name = "account_id", columnDefinition = "text")
    public String accountId;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "text", nullable = false)
    public SectionRole role;

    @Column(name = "assigned_by", columnDefinition = "text", nullable = false)
    public String assignedBy;

    @Column(name = "assigned_at", nullable = false)
    public Instant assignedAt;
}
