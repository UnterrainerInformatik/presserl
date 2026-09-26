package info.unterrainer.presserl.article;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite key of {@link ArticleRevisionEntity}.
 */
public class ArticleRevisionId implements Serializable {

    public Long articleId;
    public Integer number;

    public ArticleRevisionId() {
    }

    public ArticleRevisionId(Long articleId, Integer number) {
        this.articleId = articleId;
        this.number = number;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ArticleRevisionId other && Objects.equals(articleId, other.articleId)
                && Objects.equals(number, other.number);
    }

    @Override
    public int hashCode() {
        return Objects.hash(articleId, number);
    }
}
