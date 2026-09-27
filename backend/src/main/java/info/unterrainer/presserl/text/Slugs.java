package info.unterrainer.presserl.text;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * URL- and login-safe identifiers derived from free text: lower case, German umlauts and {@code ß}
 * spelled out, other diacritics dropped, every run outside {@code a-z0-9} turned into one
 * {@code -}, dashes trimmed at the edges, cut to a maximum length. Collisions get {@code -2},
 * {@code -3}, … with the base shortened so that the result keeps the maximum length.
 */
public final class Slugs {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_ALLOWED = Pattern.compile("[^a-z0-9]+");

    private Slugs() {
    }

    /**
     * The folded {@code text}, at most {@code maxLength} characters; {@code fallback} when nothing
     * is left.
     */
    public static String fold(String text, int maxLength, String fallback) {
        String folded = Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT)
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace("ß", "ss");
        folded = COMBINING_MARKS.matcher(Normalizer.normalize(folded, Normalizer.Form.NFD)).replaceAll("");
        folded = NOT_ALLOWED.matcher(folded).replaceAll("-");
        String result = cut(trimDashes(folded), maxLength);
        return result.isEmpty() ? fallback : result;
    }

    /**
     * {@code base} for {@code n <= 1}, otherwise {@code base-n} with the base shortened so that the
     * result stays within {@code maxLength} characters.
     */
    public static String withSuffix(String base, int n, int maxLength) {
        if (n <= 1) {
            return base;
        }
        String suffix = "-" + n;
        return cut(base, maxLength - suffix.length()) + suffix;
    }

    /**
     * The first of {@code base}, {@code base-2}, {@code base-3}, … that is not {@code taken}.
     */
    public static String firstFree(String base, int maxLength, Predicate<String> taken) {
        for (int n = 1;; n++) {
            String candidate = withSuffix(base, n, maxLength);
            if (!taken.test(candidate)) {
                return candidate;
            }
        }
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : trimDashes(value.substring(0, max));
    }

    private static String trimDashes(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '-') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '-') {
            end--;
        }
        return value.substring(start, end);
    }
}
