package info.unterrainer.presserl.issue;

import java.time.Instant;
import java.time.LocalDate;

/**
 * List entry of {@code GET /api/issues}.
 *
 * @param articleCount all articles of the issue, any status
 * @param newest       whether the issue has the highest number and so collects newly published
 *                     articles
 */
public record IssueDto(
        long id,
        int number,
        LocalDate publicationDate,
        boolean published,
        Instant publishedAt,
        long articleCount,
        boolean newest) {

    public static IssueDto of(IssueEntity issue, long articleCount, boolean newest) {
        return new IssueDto(issue.id, issue.number, issue.publicationDate, issue.published, issue.publishedAt,
                articleCount, newest);
    }
}
