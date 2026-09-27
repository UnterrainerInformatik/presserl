package info.unterrainer.presserl.section;

import java.util.List;

import info.unterrainer.presserl.api.FieldError;
import jakarta.ws.rs.core.Response.Status;

/**
 * A refused section operation, answered with {@link #status()} and the {@code errors} body. Access
 * refusals and unknown ids are answered with an empty body instead ({@code ForbiddenException},
 * {@code NotFoundException}).
 */
public class SectionException extends RuntimeException {

    private final Status status;
    private final List<FieldError> errors;

    private SectionException(Status status, List<FieldError> errors) {
        super(status.getStatusCode() + " " + errors);
        this.status = status;
        this.errors = List.copyOf(errors);
    }

    public static SectionException invalid(List<FieldError> errors) {
        return new SectionException(Status.BAD_REQUEST, errors);
    }

    public static SectionException invalid(String field, String message) {
        return invalid(List.of(new FieldError(field, message)));
    }

    public static SectionException forbidden(String field, String message) {
        return new SectionException(Status.FORBIDDEN, List.of(new FieldError(field, message)));
    }

    public static SectionException conflict(String field, String message) {
        return new SectionException(Status.CONFLICT, List.of(new FieldError(field, message)));
    }

    public Status status() {
        return status;
    }

    public List<FieldError> errors() {
        return errors;
    }
}
