package info.unterrainer.presserl.media;

import java.util.List;

import info.unterrainer.presserl.api.FieldError;
import jakarta.ws.rs.core.Response.Status;

/**
 * A refused media operation, answered with {@link #status()} and the {@code errors} body.
 */
public class MediaException extends RuntimeException {

    public static final String FILE = "file";

    private final Status status;
    private final List<FieldError> errors;

    private MediaException(Status status, String field, String message, Throwable cause) {
        super(status.getStatusCode() + " " + message, cause);
        this.status = status;
        this.errors = List.of(new FieldError(field, message));
    }

    public static MediaException invalid(String message) {
        return new MediaException(Status.BAD_REQUEST, FILE, message, null);
    }

    public static MediaException invalid(String message, Throwable cause) {
        return new MediaException(Status.BAD_REQUEST, FILE, message, cause);
    }

    public static MediaException unsupported(String message) {
        return new MediaException(Status.UNSUPPORTED_MEDIA_TYPE, FILE, message, null);
    }

    public static MediaException tooLarge(String message) {
        return new MediaException(Status.REQUEST_ENTITY_TOO_LARGE, FILE, message, null);
    }

    public static MediaException forbidden(String message) {
        return new MediaException(Status.FORBIDDEN, null, message, null);
    }

    public static MediaException notFound(String message) {
        return new MediaException(Status.NOT_FOUND, null, message, null);
    }

    public static MediaException unavailable(String message, Throwable cause) {
        return new MediaException(Status.SERVICE_UNAVAILABLE, null, message, cause);
    }

    public Status status() {
        return status;
    }

    public List<FieldError> errors() {
        return errors;
    }
}
