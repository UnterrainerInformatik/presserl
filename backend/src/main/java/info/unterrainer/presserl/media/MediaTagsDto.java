package info.unterrainer.presserl.media;

import java.util.List;

/**
 * {@code GET /api/media/tags}: the newspaper's tags, most used first.
 */
public record MediaTagsDto(List<TagDto> items) {

    /**
     * A tag in its most frequent spelling and the number of media carrying it.
     */
    public record TagDto(String name, long count) {
    }
}
