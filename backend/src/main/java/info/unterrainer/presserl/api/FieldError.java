package info.unterrainer.presserl.api;

/**
 * One violation; {@code field} is a path such as {@code body.blocks[2].content[0].text}, or
 * {@code null} when the error is not about a field.
 */
public record FieldError(String field, String message) {
}
