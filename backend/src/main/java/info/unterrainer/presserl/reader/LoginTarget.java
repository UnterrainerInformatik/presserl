package info.unterrainer.presserl.reader;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Where the reader login returns to. Only same-origin paths are accepted, so {@code /login} cannot be
 * used as an open redirect.
 */
final class LoginTarget {

    static final String HOME = "/";

    private LoginTarget() {
    }

    /**
     * {@code next} when it is a path starting with a single {@code /} (not {@code //} or {@code /\}),
     * without control characters and a valid URI reference; {@code /} otherwise.
     */
    static String of(String next) {
        if (next == null || !next.startsWith("/") || next.startsWith("//") || next.startsWith("/\\")
                || next.chars().anyMatch(c -> c < 0x20 || c == 0x7f)) {
            return HOME;
        }
        try {
            URI uri = new URI(next);
            return uri.getScheme() == null && uri.getRawAuthority() == null ? next : HOME;
        } catch (Exception e) {
            return HOME;
        }
    }

    /**
     * The login URL that returns to the article page of {@code id}; the id is encoded as it is, so
     * every id — known, unknown or malformed — gets the same kind of redirect.
     */
    static String loginForArticle(String id) {
        return loginFor("/articles/" + segment(id));
    }

    /**
     * The login URL that returns to {@code path}, a reader path whose segments are already encoded
     * ({@link #segment}).
     */
    static String loginFor(String path) {
        return "/login?next=" + encode(path).replace("%2F", "/");
    }

    /**
     * {@code value} encoded as one path segment.
     */
    static String segment(String value) {
        return encode(value).replace("+", "%20");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
