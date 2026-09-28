package info.unterrainer.presserl.article;

/**
 * Size limits for article content and review notes, counted in Unicode code points.
 */
public final class ArticleLimits {

    public static final int TEXT_FIELD_MAX = 200;
    public static final int LEAD_MAX = 1000;
    public static final int CAPTION_MAX = 300;
    public static final int REVIEW_NOTE_MAX = 1000;
    public static final int BODY_BLOCKS_MAX = 500;
    public static final int BODY_TEXT_MAX = 200_000;
    public static final int BODY_FORMAT_VERSION = 1;

    private ArticleLimits() {
    }
}
