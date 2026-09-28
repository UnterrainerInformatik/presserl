package info.unterrainer.presserl.issue;

import java.util.List;

/**
 * Response of {@code GET /api/issues}: highest number first.
 */
public record IssueListDto(List<IssueDto> issues) {
}
