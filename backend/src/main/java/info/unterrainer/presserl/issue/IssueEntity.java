package info.unterrainer.presserl.issue;

import java.time.Instant;
import java.time.LocalDate;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An issue of the newspaper. Its articles point to it ({@code ArticleEntity.issueId}); the number
 * is assigned on creation and never changed.
 */
@Entity
@Table(name = "issue")
public class IssueEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, updatable = false)
    public int number;

    /**
     * Shown to readers; {@code null} for none (blog mode).
     */
    @Column(name = "publication_date")
    public LocalDate publicationDate;

    @Column(nullable = false)
    public boolean published;

    /**
     * The latest switch to published; {@code null} while not published.
     */
    @Column(name = "published_at")
    public Instant publishedAt;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
