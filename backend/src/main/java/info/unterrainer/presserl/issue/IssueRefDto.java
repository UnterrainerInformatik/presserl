package info.unterrainer.presserl.issue;

/**
 * The issue an article belongs to, as embedded in article representations.
 */
public record IssueRefDto(long id, int number) {

    /**
     * {@code null} for an article without issue.
     */
    public static IssueRefDto of(IssueEntity issue) {
        return issue == null ? null : new IssueRefDto(issue.id, issue.number);
    }
}
