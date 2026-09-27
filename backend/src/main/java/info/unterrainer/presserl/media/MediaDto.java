package info.unterrainer.presserl.media;

import java.time.Instant;

import info.unterrainer.presserl.article.AuthorDto;

/**
 * An uploaded image: type and size of the stored (re-encoded) file and who uploaded it when.
 */
public record MediaDto(long id, String contentType, int width, int height, long size, AuthorDto uploadedBy,
        Instant uploadedAt) {

    static MediaDto of(MediaEntity media) {
        return new MediaDto(media.id, media.contentType, media.width, media.height, media.byteSize,
                new AuthorDto(media.uploaderUsername, media.uploaderDisplayName), media.createdAt);
    }
}
