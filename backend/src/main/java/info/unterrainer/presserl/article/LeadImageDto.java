package info.unterrainer.presserl.article;

/**
 * A revision's lead image with the stored image's width and height (for the editor's layout).
 */
public record LeadImageDto(long mediaId, String caption, int width, int height) {
}
