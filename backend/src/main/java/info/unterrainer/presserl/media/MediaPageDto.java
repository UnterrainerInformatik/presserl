package info.unterrainer.presserl.media;

import java.util.List;

/**
 * One page of {@code GET /api/media}, newest first; {@code next} is the {@code before} value of the
 * following page, {@code null} on the last page.
 */
public record MediaPageDto(List<MediaListItemDto> items, Long next) {
}
