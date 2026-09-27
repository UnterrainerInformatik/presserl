package info.unterrainer.presserl.account;

import java.util.function.Predicate;

import info.unterrainer.presserl.text.Slugs;

/**
 * Derives a username from a first name with {@link Slugs}: at most {@value #MAX_LENGTH} characters,
 * {@code user} when nothing is left. Collisions get {@code -2}, {@code -3}, … A base shorter than
 * {@value #MIN_LENGTH} characters is never used as is but numbered from {@code -1} on
 * ({@code li-1}, {@code li-2}, …).
 */
public final class UsernameDeriver {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 32;
    static final String FALLBACK = "user";

    private UsernameDeriver() {
    }

    /**
     * The username for {@code firstName} before any collision handling.
     */
    public static String base(String firstName) {
        return Slugs.fold(firstName, MAX_LENGTH, FALLBACK);
    }

    /**
     * {@code base} for {@code n == 1}, otherwise {@code base-n} with the base shortened so that the
     * result stays within {@value #MAX_LENGTH} characters.
     */
    public static String withSuffix(String base, int n) {
        return Slugs.withSuffix(base, n, MAX_LENGTH);
    }

    /**
     * The first of {@code base}, {@code base-2}, {@code base-3}, … that is not {@code taken}; for a
     * base shorter than {@value #MIN_LENGTH} characters the first of {@code base-1}, {@code base-2},
     * ….
     */
    public static String firstFree(String base, Predicate<String> taken) {
        if (base.length() >= MIN_LENGTH) {
            return Slugs.firstFree(base, MAX_LENGTH, taken);
        }
        for (int n = 1;; n++) {
            String candidate = base + "-" + n;
            if (!taken.test(candidate)) {
                return candidate;
            }
        }
    }
}
