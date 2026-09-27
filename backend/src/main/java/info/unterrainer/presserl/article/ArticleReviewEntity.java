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

/**
 * An approval or rejection of an article's latest revision. The reviewer's username and display
 * name are snapshots, like the article's author.
 */
@Entity
@Table(name = "article_review")
public class ArticleReviewEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "article_id", nullable = false)
    public Long articleId;

    /**
     * The revision that was reviewed.
     */
    @Column(nullable = false)
    public Integer revision;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "text", nullable = false)
    public ReviewDecision decision;

    /**
     * The level the article waited for.
     */
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "text", nullable = false)
    public ApprovalLevel level;

    @Column(name = "reviewer_sub", columnDefinition = "text", nullable = false)
    public String reviewerSub;

    @Column(name = "reviewer_username", columnDefinition = "text", nullable = false)
    public String reviewerUsername;

    @Column(name = "reviewer_display_name", columnDefinition = "text", nullable = false)
    public String reviewerDisplayName;

    /**
     * Set for rejections only.
     */
    @Column(columnDefinition = "text")
    public String note;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
