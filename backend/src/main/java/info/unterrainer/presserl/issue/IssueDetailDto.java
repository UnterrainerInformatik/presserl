package info.unterrainer.presserl.issue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import info.unterrainer.presserl.article.ArticleSummaryDto;

/**
 * An issue with its articles in issue order; the first one is the lead story.
 */
public record IssueDetailDto(
        long id,
        int number,
        LocalDate publicationDate,
        boolean published,
        Instant publishedAt,
        long articleCount,
        boolean newest,
        List<ArticleSummaryDto> articles) {

    public static IssueDetailDto of(IssueEntity issue, boolean newest, List<ArticleSummaryDto> articles) {
        return new IssueDetailDto(issue.id, issue.number, issue.publicationDate, issue.published, issue.publishedAt,
                articles.size(), newest, List.copyOf(articles));
    }
}
