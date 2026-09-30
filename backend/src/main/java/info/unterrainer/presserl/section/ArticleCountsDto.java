package info.unterrainer.presserl.section;

import java.util.List;

/**
 * How many articles a section holds now: {@code live} visible to readers (published in a live issue),
 * {@code total} in any status, and per issue that contains at least one of them (highest issue number first).
 */
public record ArticleCountsDto(long live, long total, List<IssueCount> issues) {

    public static final ArticleCountsDto EMPTY = new ArticleCountsDto(0, 0, List.of());

    public ArticleCountsDto {
        issues = List.copyOf(issues);
    }

    public record IssueCount(long issueId, int number, long count) {
    }
}
