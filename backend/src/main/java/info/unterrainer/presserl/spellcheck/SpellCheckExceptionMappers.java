package info.unterrainer.presserl.spellcheck;

import java.util.List;

import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import info.unterrainer.presserl.api.ApiErrorDto;
import info.unterrainer.presserl.api.FieldError;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;

/**
 * Maps refused spell checks to their status with the {@link ApiErrorDto} body.
 */
public class SpellCheckExceptionMappers {

    @ServerExceptionMapper
    public RestResponse<ApiErrorDto> invalid(SpellCheckRequestException e) {
        return RestResponse.ResponseBuilder.create(Status.BAD_REQUEST, new ApiErrorDto(e.errors()))
                .type(MediaType.APPLICATION_JSON_TYPE)
                .build();
    }

    @ServerExceptionMapper
    public RestResponse<ApiErrorDto> unavailable(SpellCheckUnavailableException e) {
        return RestResponse.ResponseBuilder
                .create(Status.SERVICE_UNAVAILABLE,
                        new ApiErrorDto(List.of(new FieldError(null, SpellCheckUnavailableException.MESSAGE))))
                .type(MediaType.APPLICATION_JSON_TYPE)
                .build();
    }
}
