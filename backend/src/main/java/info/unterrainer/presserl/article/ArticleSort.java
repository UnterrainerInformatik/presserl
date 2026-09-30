package info.unterrainer.presserl.article;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Orders of {@code GET /api/articles?sort=...}; ties are broken by descending id.
 */
public enum ArticleSort {
    /**
     * Newest change first (the default).
     */
    CHANGED("a.updatedAt desc, a.id desc"),
    /**
     * Newest creation first.
     */
    NEWEST("a.createdAt desc, a.id desc"),
    /**
     * By section position, then newest change first.
     */
    SECTION("s.position asc, s.id asc, a.updatedAt desc, a.id desc");

    private final String orderBy;

    ArticleSort(String orderBy) {
        this.orderBy = orderBy;
    }

    /**
     * The HQL order clause over the article {@code a} and its section {@code s}.
     */
    String orderBy() {
        return orderBy;
    }

    /**
     * The external spelling, e.g. {@code changed}.
     */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<ArticleSort> parse(String value) {
        return Arrays.stream(values()).filter(sort -> sort.value().equals(value)).findFirst();
    }
}
