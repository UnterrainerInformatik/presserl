package info.unterrainer.presserl.account;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Derives a username from a first name: lower case, German umlauts and {@code ß} spelled out,
 * other diacritics dropped, every run outside {@code a-z0-9} turned into one {@code -}, at most
 * {@value #MAX_LENGTH} characters, {@code user} when nothing is left. Collisions get {@code -2},
 * {@code -3}, … A base shorter than {@value #MIN_LENGTH} characters is never used as is but numbered
 * from {@code -1} on ({@code li-1}, {@code li-2}, …).
 */
public final class UsernameDeriver {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 32;
    static final String FALLBACK = "user";

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_ALLOWED = Pattern.compile("[^a-z0-9]+");

    private UsernameDeriver() {
    }

    /**
     * The username for {@code firstName} before any collision handling.
     */
    public static String base(String firstName) {
        String folded = Normalizer.normalize(firstName, Normalizer.Form.NFC).toLowerCase(Locale.ROOT)
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace("ß", "ss");
        folded = COMBINING_MARKS.matcher(Normalizer.normalize(folded, Normalizer.Form.NFD)).replaceAll("");
        folded = NOT_ALLOWED.matcher(folded).replaceAll("-");
        String result = cut(trimDashes(folded), MAX_LENGTH);
        return result.isEmpty() ? FALLBACK : result;
    }

    /**
     * {@code base} for {@code n == 1}, otherwise {@code base-n} with the base shortened so that the
     * result stays within {@value #MAX_LENGTH} characters.
     */
    public static String withSuffix(String base, int n) {
        if (n <= 1) {
            return base;
        }
        String suffix = "-" + n;
        return cut(base, MAX_LENGTH - suffix.length()) + suffix;
    }

    /**
     * The first of {@code base}, {@code base-2}, {@code base-3}, … that is not {@code taken}; for a
     * base shorter than {@value #MIN_LENGTH} characters the first of {@code base-1}, {@code base-2},
     * ….
     */
    public static String firstFree(String base, Predicate<String> taken) {
        boolean tooShort = base.length() < MIN_LENGTH;
        for (int n = 1;; n++) {
            String candidate = tooShort ? base + "-" + n : withSuffix(base, n);
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
