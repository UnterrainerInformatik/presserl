package info.unterrainer.presserl.article;

import java.time.Instant;

/**
 * Entry of {@code GET /api/articles/{id}/reviews}; {@code note} is {@code null} for approvals.
 */
public record ReviewDto(ReviewDecision decision, ApprovalLevel level, int revision, AuthorDto reviewer, String note,
        Instant createdAt) {

    public static ReviewDto of(ArticleReviewEntity review) {
        return new ReviewDto(review.decision, review.level, review.revision,
                new AuthorDto(review.reviewerUsername, review.reviewerDisplayName), review.note, review.createdAt);
    }
}
