package info.unterrainer.presserl.media;

import io.quarkus.runtime.configuration.MemorySize;
import io.quarkus.runtime.configuration.MemorySizeConverter;

/**
 * Size limits of uploads. The HTTP body ceiling ({@code quarkus.http.limits.max-body-size=64M})
 * leaves room for the multipart envelope above {@link #MAX_SIZE_CEILING}.
 */
public final class MediaLimits {

    public static final String MAX_SIZE_CEILING = "60M";
    /**
     * Body limit for every request outside {@code /api/media}, the former global default.
     */
    public static final long OTHER_BODY_LIMIT = 10L * 1024 * 1024;

    private MediaLimits() {
    }

    /**
     * Parses {@code presserl.media.max-size}; fails when it is unreadable, not positive or above
     * {@link #MAX_SIZE_CEILING}.
     */
    public static long maxBytes(String maxSize) {
        long bytes;
        try {
            bytes = parse(maxSize);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("presserl.media.max-size (PRESSERL_MEDIA_MAX_SIZE) '" + maxSize
                    + "' is not a memory size such as 10M", e);
        }
        if (bytes <= 0 || bytes > parse(MAX_SIZE_CEILING)) {
            throw new IllegalArgumentException("presserl.media.max-size (PRESSERL_MEDIA_MAX_SIZE) must be between 1 and "
                    + MAX_SIZE_CEILING + ", is '" + maxSize + "'");
        }
        return bytes;
    }

    private static long parse(String value) {
        MemorySize size = new MemorySizeConverter().convert(value.trim());
        return size.asLongValue();
    }
}
