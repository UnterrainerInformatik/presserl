package info.unterrainer.presserl.spellcheck;

import java.util.List;

import info.unterrainer.presserl.api.FieldError;

/**
 * An invalid spell-check request, answered with {@code 400} and the {@code errors} body.
 */
public class SpellCheckRequestException extends RuntimeException {

    private final List<FieldError> errors;

    public SpellCheckRequestException(String field, String message) {
        super("400 " + message);
        this.errors = List.of(new FieldError(field, message));
    }

    public List<FieldError> errors() {
        return errors;
    }
}
