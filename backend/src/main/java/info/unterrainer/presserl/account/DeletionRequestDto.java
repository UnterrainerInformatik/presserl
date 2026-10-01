package info.unterrainer.presserl.account;

import java.time.Instant;

/**
 * Response of {@code POST}/{@code DELETE /api/me/deletion-request}; {@code deletionRequestedAt} is
 * {@code null} without a pending request.
 */
public record DeletionRequestDto(Instant deletionRequestedAt) {
}
