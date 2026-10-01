package info.unterrainer.presserl.reader;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import com.fasterxml.jackson.databind.JsonNode;

import info.unterrainer.presserl.article.ArticleEntity;
import info.unterrainer.presserl.article.ArticleRevisionEntity;
import info.unterrainer.presserl.section.SectionEntity;

/**
 * A published article as the reader shows it: the content of its live revision only.
 *
 * @param byline      the author's display name, or the username when that is empty; {@code null} once the
 *                    author's account was deleted ("former newsroom member")
 * @param publishedAt first publication of the article
 * @param revisedAt   when the live revision was published
 * @param section     the article's section
 * @param leadImage   the live revision's lead image, {@code null} for none
 */
public record ReaderArticle(
        long id,
        String kicker,
        String headline,
        String subheadline,
        String lead,
        JsonNode body,
        String byline,
        Instant publishedAt,
        Instant revisedAt,
        ReaderSection section,
        ReaderImage leadImage) {

    static ReaderArticle of(ArticleEntity article, ArticleRevisionEntity live, SectionEntity section,
            ReaderImage leadImage) {
        String byline = article.authorDisplayName == null || article.authorDisplayName.isBlank() ? article.authorUsername
                : article.authorDisplayName;
        return new ReaderArticle(article.id, live.kicker, live.headline, live.subheadline, live.lead, live.body,
                byline, article.publishedAt, live.publishedAt, ReaderSection.of(section), leadImage);
    }

    /**
     * Whether the live revision was published on a later day than the article itself.
     */
    public boolean updated() {
        return revisedAt != null && localDate(revisedAt).isAfter(localDate(publishedAt));
    }

    static LocalDate localDate(Instant instant) {
        return instant.atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
