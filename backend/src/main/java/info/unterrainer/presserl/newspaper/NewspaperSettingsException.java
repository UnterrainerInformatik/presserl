package info.unterrainer.presserl.newspaper;

import java.util.List;

import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import info.unterrainer.presserl.api.ApiErrorDto;
import info.unterrainer.presserl.api.FieldError;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;

/**
 * A refused settings update, answered with {@code 400} and the {@code errors} body.
 */
public class NewspaperSettingsException extends RuntimeException {

    private final List<FieldError> errors;

    public NewspaperSettingsException(List<FieldError> errors) {
        super("400 " + errors);
        this.errors = List.copyOf(errors);
    }

    public List<FieldError> errors() {
        return errors;
    }

    public static class Mapper {

        @ServerExceptionMapper
        public RestResponse<ApiErrorDto> invalid(NewspaperSettingsException e) {
            return RestResponse.ResponseBuilder.create(Status.BAD_REQUEST, new ApiErrorDto(e.errors()))
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .build();
        }
    }
}
