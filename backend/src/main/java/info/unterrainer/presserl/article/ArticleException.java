package info.unterrainer.presserl.article;

import java.util.List;

import info.unterrainer.presserl.api.FieldError;
import jakarta.ws.rs.core.Response.Status;

/**
 * A refused article operation, answered with {@link #status()} and the {@code errors} body.
 */
public class ArticleException extends RuntimeException {

    private final Status status;
    private final List<FieldError> errors;

    private ArticleException(Status status, List<FieldError> errors) {
        super(status.getStatusCode() + " " + errors);
        this.status = status;
        this.errors = List.copyOf(errors);
    }

    public static ArticleException invalid(List<FieldError> errors) {
        return new ArticleException(Status.BAD_REQUEST, errors);
    }

    public static ArticleException invalid(String field, String message) {
        return invalid(List.of(new FieldError(field, message)));
    }

    public static ArticleException forbidden(String message) {
        return new ArticleException(Status.FORBIDDEN, List.of(new FieldError(null, message)));
    }

    public static ArticleException forbidden(String field, String message) {
        return new ArticleException(Status.FORBIDDEN, List.of(new FieldError(field, message)));
    }

    public static ArticleException notFound(String message) {
        return new ArticleException(Status.NOT_FOUND, List.of(new FieldError(null, message)));
    }

    public static ArticleException conflict(String message) {
        return new ArticleException(Status.CONFLICT, List.of(new FieldError(null, message)));
    }

    public Status status() {
        return status;
    }

    public List<FieldError> errors() {
        return errors;
    }
}
