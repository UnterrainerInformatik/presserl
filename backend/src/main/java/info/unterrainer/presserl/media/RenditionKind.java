package info.unterrainer.presserl.media;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * The smaller copies every media gets, largest first. URL and JSON use {@link #value()}.
 */
public enum RenditionKind {

    /** Print views, about 25 cm at 300 dpi. */
    PRINT(3000),
    /** Article page and front-page lead story. */
    WEB(1600),
    /** Front-page cards, editor preview, small screens. */
    THUMBNAIL(480);

    private final int maxLongSide;

    RenditionKind(int maxLongSide) {
        this.maxLongSide = maxLongSide;
    }

    /**
     * The longest side in pixels; smaller images are not enlarged.
     */
    public int maxLongSide() {
        return maxLongSide;
    }

    /**
     * The lower-case name used in URLs, JSON and the database.
     */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The kind named {@code value} exactly (lower case); empty for anything else.
     */
    public static Optional<RenditionKind> parse(String value) {
        return Arrays.stream(values()).filter(kind -> kind.value().equals(value)).findFirst();
    }
}
