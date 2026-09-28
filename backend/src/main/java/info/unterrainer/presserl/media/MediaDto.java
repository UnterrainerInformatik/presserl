package info.unterrainer.presserl.media;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import info.unterrainer.presserl.article.AuthorDto;

/**
 * An uploaded image: type and size of the stored (re-encoded) file, who uploaded it when, and its
 * renditions by {@link RenditionKind#value()}, smallest first ({@code {}} while not produced yet).
 */
public record MediaDto(long id, String contentType, int width, int height, long size, AuthorDto uploadedBy,
        Instant uploadedAt, Map<String, RenditionDto> renditions) {

    /**
     * Size of one rendition.
     */
    public record RenditionDto(int width, int height, long size) {
    }

    static MediaDto of(MediaView view) {
        MediaEntity media = view.media();
        Map<String, RenditionDto> renditions = new LinkedHashMap<>();
        view.renditions().stream()
                .sorted(Comparator.comparingInt(r -> RenditionKind.parse(r.kind).map(k -> -k.ordinal()).orElse(0)))
                .forEach(r -> renditions.put(r.kind, new RenditionDto(r.width, r.height, r.byteSize)));
        return new MediaDto(media.id, media.contentType, media.width, media.height, media.byteSize,
                new AuthorDto(media.uploaderUsername, media.uploaderDisplayName), media.createdAt, renditions);
    }
}
