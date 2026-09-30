package info.unterrainer.presserl.article;

import org.hibernate.annotations.Immutable;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * A media used by an article revision, as lead image or in an image block of its body. Read-only:
 * the rows are written by the database trigger {@code article_revision_media_sync} (migration V13)
 * whenever a revision is inserted or its lead image or body change, and go with the revision.
 */
@Entity
@Immutable
@Table(name = "article_revision_media")
@IdClass(ArticleRevisionMediaId.class)
public class ArticleRevisionMediaEntity extends PanacheEntityBase {

    @Id
    @Column(name = "article_id")
    public Long articleId;

    @Id
    public Integer number;

    @Id
    @Column(name = "media_id")
    public Long mediaId;
}
