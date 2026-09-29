package info.unterrainer.presserl.spellcheck;

/**
 * The spell check is switched off or LanguageTool did not answer usefully; answered with {@code 503}.
 */
public class SpellCheckUnavailableException extends RuntimeException {

    public static final String MESSAGE = "the spell check is currently unavailable";

    public SpellCheckUnavailableException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
