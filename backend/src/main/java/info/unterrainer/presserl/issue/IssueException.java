package info.unterrainer.presserl.issue;

import java.util.List;

import info.unterrainer.presserl.api.FieldError;
import jakarta.ws.rs.core.Response.Status;

/**
 * A refused issue operation, answered with {@link #status()} and the {@code errors} body. Access
 * refusals are answered with an empty body instead ({@code ForbiddenException}).
 */
public class IssueException extends RuntimeException {

    private final Status status;
    private final List<FieldError> errors;

    private IssueException(Status status, List<FieldError> errors) {
        super(status.getStatusCode() + " " + errors);
        this.status = status;
        this.errors = List.copyOf(errors);
    }

    public static IssueException invalid(List<FieldError> errors) {
        return new IssueException(Status.BAD_REQUEST, errors);
    }

    public static IssueException invalid(String field, String message) {
        return invalid(List.of(new FieldError(field, message)));
    }

    public static IssueException notFound(long id) {
        return new IssueException(Status.NOT_FOUND, List.of(new FieldError(null, "issue " + id + " does not exist")));
    }

    public static IssueException conflict(String message) {
        return new IssueException(Status.CONFLICT, List.of(new FieldError(null, message)));
    }

    public Status status() {
        return status;
    }

    public List<FieldError> errors() {
        return errors;
    }
}
