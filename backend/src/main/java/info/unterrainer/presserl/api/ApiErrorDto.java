package info.unterrainer.presserl.api;

import java.util.List;

/**
 * Error body of every refused API request: {@code {"errors": [{"field": ..., "message": ...}]}}.
 */
public record ApiErrorDto(List<FieldError> errors) {
}
