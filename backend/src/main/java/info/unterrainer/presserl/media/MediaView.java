package info.unterrainer.presserl.media;

import java.util.List;

/**
 * A media record with its renditions (empty while they are not produced yet) and its tags, sorted by
 * {@link MediaDetailsValidator#TAG_ORDER}.
 */
public record MediaView(MediaEntity media, List<MediaRenditionEntity> renditions, List<String> tags) {
}
