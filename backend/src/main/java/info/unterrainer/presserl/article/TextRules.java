package info.unterrainer.presserl.article;

/**
 * Character rules shared by the text fields and the body.
 */
final class TextRules {

    private TextRules() {
    }

    /**
     * Whether the text contains a control character (Unicode category Cc); {@code \n} is allowed
     * if {@code allowLineFeed} is set.
     */
    static boolean hasControlCharacter(String text, boolean allowLineFeed) {
        return text.codePoints().anyMatch(c -> Character.getType(c) == Character.CONTROL
                && !(allowLineFeed && c == '\n'));
    }

    static int length(String text) {
        return text.codePointCount(0, text.length());
    }
}
