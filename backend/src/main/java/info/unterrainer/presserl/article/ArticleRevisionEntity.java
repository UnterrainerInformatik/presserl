package info.unterrainer.presserl.article;

import java.time.Instant;
import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

import info.unterrainer.presserl.auth.CurrentUser;
import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * One numbered revision of an article. The latest revision is overwritten by saves of its own author
 * until it has been published. The author is identified by the token subject; username and display
 * name are snapshots taken when the revision was created.
 */
@Entity
@Table(name = "article_revision")
@IdClass(ArticleRevisionId.class)
public class ArticleRevisionEntity extends PanacheEntityBase {

    @Id
    @Column(name = "article_id")
    public Long articleId;

    @Id
    public Integer number;

    @Column(columnDefinition = "text", nullable = false)
    public String kicker = "";

    @Column(columnDefinition = "text", nullable = false)
    public String headline = "";

    @Column(columnDefinition = "text", nullable = false)
    public String subheadline = "";

    @Column(columnDefinition = "text", nullable = false)
    public String lead = "";

    /**
     * Validated body in format version 1, stored as sent.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    public JsonNode body;

    /**
     * The lead image's media; {@code null} for none.
     */
    @Column(name = "lead_image_media_id")
    public Long leadImageMediaId;

    /**
     * The lead image's caption; empty without a lead image.
     */
    @Column(name = "lead_image_caption", columnDefinition = "text", nullable = false)
    public String leadImageCaption = "";

    @Column(name = "author_sub", columnDefinition = "text", nullable = false)
    public String authorSub;

    @Column(name = "author_username", columnDefinition = "text", nullable = false)
    public String authorUsername;

    @Column(name = "author_display_name", columnDefinition = "text", nullable = false)
    public String authorDisplayName;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    /**
     * When this revision became live; {@code null} while it is a working revision.
     */
    @Column(name = "published_at")
    public Instant publishedAt;

    /**
     * Whether this revision holds exactly {@code content}; bodies compare as JSON trees, so key
     * order does not matter.
     */
    boolean holds(ArticleContent content) {
        return kicker.equals(content.kicker()) && headline.equals(content.headline())
                && subheadline.equals(content.subheadline()) && lead.equals(content.lead())
                && body.equals(content.body()) && Objects.equals(leadImage(), content.leadImage());
    }

    /**
     * The lead image, {@code null} for none.
     */
    public ArticleContent.LeadImage leadImage() {
        return leadImageMediaId == null ? null : new ArticleContent.LeadImage(leadImageMediaId, leadImageCaption);
    }

    /**
     * Records the user as the revision's author.
     */
    void writtenBy(CurrentUser user) {
        authorSub = user.sub();
        authorUsername = user.username();
        authorDisplayName = user.displayName();
    }

    void apply(ArticleContent content) {
        kicker = content.kicker();
        headline = content.headline();
        subheadline = content.subheadline();
        lead = content.lead();
        body = content.body();
        leadImageMediaId = content.leadImage() == null ? null : content.leadImage().mediaId();
        leadImageCaption = content.leadImage() == null ? "" : content.leadImage().caption();
    }
}
