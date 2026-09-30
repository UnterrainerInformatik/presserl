package info.unterrainer.presserl.article;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite key of {@link ArticleRevisionMediaEntity}.
 */
public class ArticleRevisionMediaId implements Serializable {

    public Long articleId;
    public Integer number;
    public Long mediaId;

    public ArticleRevisionMediaId() {
    }

    public ArticleRevisionMediaId(Long articleId, Integer number, Long mediaId) {
        this.articleId = articleId;
        this.number = number;
        this.mediaId = mediaId;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ArticleRevisionMediaId other && Objects.equals(articleId, other.articleId)
                && Objects.equals(number, other.number) && Objects.equals(mediaId, other.mediaId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(articleId, number, mediaId);
    }
}
