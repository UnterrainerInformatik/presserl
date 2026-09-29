package info.unterrainer.presserl.spellcheck;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Reads a spell-check request body: an object whose {@code text} is a string of at most
 * {@value #MAX_TEXT} code points. Other fields are ignored.
 */
public final class SpellCheckRequestValidator {

    public static final String TEXT = "text";
    public static final int MAX_TEXT = 10_000;
    static final String MESSAGE = "text must be a string of at most " + MAX_TEXT + " code points";

    private SpellCheckRequestValidator() {
    }

    /**
     * @throws SpellCheckRequestException naming the field {@code text}
     */
    public static String text(JsonNode json) {
        JsonNode node = json == null || !json.isObject() ? null : json.get(TEXT);
        if (node == null || !node.isTextual()) {
            throw new SpellCheckRequestException(TEXT, MESSAGE);
        }
        String text = node.asText();
        if (text.codePointCount(0, text.length()) > MAX_TEXT) {
            throw new SpellCheckRequestException(TEXT, MESSAGE);
        }
        return text;
    }
}
