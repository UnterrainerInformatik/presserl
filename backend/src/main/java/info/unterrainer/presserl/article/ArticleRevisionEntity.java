package info.unterrainer.presserl.article;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * One numbered revision of an article. The latest revision is overwritten by saves until it has
 * been published.
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

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    /**
     * When this revision became live; {@code null} while it is a working revision.
     */
    @Column(name = "published_at")
    public Instant publishedAt;

    void apply(ArticleContent content) {
        kicker = content.kicker();
        headline = content.headline();
        subheadline = content.subheadline();
        lead = content.lead();
        body = content.body();
    }
}
