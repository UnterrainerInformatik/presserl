package info.unterrainer.presserl.account;

import java.util.List;

import info.unterrainer.presserl.api.FieldError;
import jakarta.ws.rs.core.Response.Status;

/**
 * A refused account operation, answered with {@link #status()} and the {@code errors} body.
 */
public class AccountException extends RuntimeException {

    static final String UNAVAILABLE = "the account service is unavailable; try again later";

    private final Status status;
    private final List<FieldError> errors;

    private AccountException(Status status, List<FieldError> errors, Throwable cause) {
        super(status.getStatusCode() + " " + errors, cause);
        this.status = status;
        this.errors = List.copyOf(errors);
    }

    public static AccountException invalid(List<FieldError> errors) {
        return new AccountException(Status.BAD_REQUEST, errors, null);
    }

    public static AccountException invalid(String field, String message) {
        return invalid(List.of(new FieldError(field, message)));
    }

    public static AccountException forbidden(String field, String message) {
        return new AccountException(Status.FORBIDDEN, List.of(new FieldError(field, message)), null);
    }

    public static AccountException conflict(String field, String message) {
        return new AccountException(Status.CONFLICT, List.of(new FieldError(field, message)), null);
    }

    /**
     * Keycloak is unreachable or refuses the backend's service account.
     */
    public static AccountException unavailable(Throwable cause) {
        return new AccountException(Status.SERVICE_UNAVAILABLE, List.of(new FieldError(null, UNAVAILABLE)), cause);
    }

    public Status status() {
        return status;
    }

    public List<FieldError> errors() {
        return errors;
    }
}
