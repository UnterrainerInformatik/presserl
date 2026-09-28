package info.unterrainer.presserl.reader;

import java.time.LocalDate;

import info.unterrainer.presserl.issue.IssueEntity;
import io.quarkus.qute.TemplateData;

/**
 * A published issue as the reader shows it.
 *
 * @param publicationDate {@code null} for none
 * @param headline        the live headline of its first published article, {@code null} for none
 *                        or when not loaded
 */
@TemplateData
public record ReaderIssue(long id, int number, LocalDate publicationDate, String headline) {

    static ReaderIssue of(IssueEntity issue, String headline) {
        return new ReaderIssue(issue.id, issue.number, issue.publicationDate, headline);
    }
}
