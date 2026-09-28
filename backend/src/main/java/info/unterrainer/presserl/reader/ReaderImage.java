package info.unterrainer.presserl.reader;

import info.unterrainer.presserl.media.MediaRenditionEntity;

/**
 * The lead image of a live revision as the reader links it: {@code /media/{mediaId}/web},
 * {@code /media/{mediaId}/thumbnail} and {@code /media/{mediaId}/{printKind}} with their sizes.
 *
 * @param caption   plain text, empty for none
 * @param printKind {@code print}, or {@code web} while the print rendition is not produced yet
 */
public record ReaderImage(long mediaId, String caption, int webWidth, int webHeight, int thumbWidth,
        int thumbHeight, String printKind, int printWidth, int printHeight) {

    /**
     * The image, or {@code null} without a lead image or while its web and thumbnail renditions are
     * not produced yet.
     */
    static ReaderImage of(Long mediaId, String caption, MediaRenditionEntity web, MediaRenditionEntity thumbnail,
            MediaRenditionEntity print) {
        if (mediaId == null || web == null || thumbnail == null) {
            return null;
        }
        MediaRenditionEntity forPrint = print != null ? print : web;
        return new ReaderImage(mediaId, caption, web.width, web.height, thumbnail.width, thumbnail.height,
                print != null ? "print" : "web", forPrint.width, forPrint.height);
    }
}
