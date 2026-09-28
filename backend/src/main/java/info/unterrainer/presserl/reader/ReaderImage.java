package info.unterrainer.presserl.reader;

import info.unterrainer.presserl.media.MediaRenditionEntity;

/**
 * The lead image of a live revision as the reader links it: {@code /media/{mediaId}/web} and
 * {@code /media/{mediaId}/thumbnail} with their sizes.
 *
 * @param caption plain text, empty for none
 */
public record ReaderImage(long mediaId, String caption, int webWidth, int webHeight, int thumbWidth,
        int thumbHeight) {

    /**
     * The image, or {@code null} without a lead image or while its renditions are not produced yet.
     */
    static ReaderImage of(Long mediaId, String caption, MediaRenditionEntity web, MediaRenditionEntity thumbnail) {
        if (mediaId == null || web == null || thumbnail == null) {
            return null;
        }
        return new ReaderImage(mediaId, caption, web.width, web.height, thumbnail.width, thumbnail.height);
    }
}
