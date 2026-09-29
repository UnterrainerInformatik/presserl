package info.unterrainer.presserl.media;

import java.time.Instant;
import java.util.Map;

import info.unterrainer.presserl.article.AuthorDto;

/**
 * A media record ({@link MediaDto}) with {@code usageCount}, the number of distinct articles any of
 * whose revisions uses it as lead image.
 */
public record MediaListItemDto(long id, long version, String contentType, int width, int height, long size,
        AuthorDto uploadedBy, Instant uploadedAt, Map<String, MediaDto.RenditionDto> renditions, long usageCount) {

    static MediaListItemDto of(MediaView view, long usageCount) {
        MediaDto dto = MediaDto.of(view);
        return new MediaListItemDto(dto.id(), dto.version(), dto.contentType(), dto.width(), dto.height(),
                dto.size(), dto.uploadedBy(), dto.uploadedAt(), dto.renditions(), usageCount);
    }
}
