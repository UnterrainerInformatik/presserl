package info.unterrainer.presserl.article;

import java.time.Instant;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * An article; its content lives in {@link ArticleRevisionEntity}. The author is identified by the
 * token subject; username and display name are snapshots for the byline.
 */
@Entity
@Table(name = "article")
public class ArticleEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "text", nullable = false)
    public ArticleStatus status;

    @Column(name = "author_sub", columnDefinition = "text", nullable = false)
    public String authorSub;

    @Column(name = "author_username", columnDefinition = "text", nullable = false)
    public String authorUsername;

    @Column(name = "author_display_name", columnDefinition = "text", nullable = false)
    public String authorDisplayName;

    /**
     * The section the article belongs to; set on every create and never cleared (the column is
     * nullable only for rows from before sections, filed by the default-section bootstrap).
     */
    @Column(name = "section_id")
    public Long sectionId;

    /**
     * Number of the revision the reader shows; {@code null} until the first publication.
     */
    @Column(name = "live_revision")
    public Integer liveRevision;

    /**
     * First publication.
     */
    @Column(name = "published_at")
    public Instant publishedAt;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @Version
    public long version;
}
