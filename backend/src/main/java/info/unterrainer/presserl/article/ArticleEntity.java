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
     * The section the article belongs to; set on every create and never cleared.
     */
    @Column(name = "section_id", nullable = false)
    public Long sectionId;

    /**
     * Number of the revision the reader shows; {@code null} until the first publication.
     */
    @Column(name = "live_revision")
    public Integer liveRevision;

    /**
     * The issue the article belongs to; {@code null} for none. Set together with
     * {@link #issuePosition}.
     */
    @Column(name = "issue_id")
    public Long issueId;

    /**
     * Place within the issue (ascending, ties by id); {@code null} exactly when {@link #issueId} is.
     */
    @Column(name = "issue_position")
    public Integer issuePosition;

    /**
     * The approval level the article waits for; {@code null} while no submission is pending.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "pending_level", columnDefinition = "text")
    public ApprovalLevel pendingLevel;

    /**
     * Emergency brake: set when a publisher takes the article offline; only an {@code OFFLINE} article
     * is locked. While set, the chain ends with the {@code PUBLISHER} level ({@link ApprovalChain#next}).
     */
    @Column(nullable = false)
    public boolean locked;

    /**
     * Front-page weight (1–999, lower comes first), {@code null} for none. Metadata, not content: not
     * updatable through the entity, so a content save never overwrites it, and written only by the
     * targeted update of {@code ArticleService#setFrontPageWeight}, which leaves {@link #version} alone.
     */
    @Column(name = "front_page_weight", updatable = false)
    public Integer frontPageWeight;

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
