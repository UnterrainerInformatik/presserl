package info.unterrainer.presserl.article;

import java.util.List;

/**
 * Error body of every refused article request: {@code {"errors": [{"field": ..., "message": ...}]}}.
 */
public record ApiErrorDto(List<FieldError> errors) {
}
