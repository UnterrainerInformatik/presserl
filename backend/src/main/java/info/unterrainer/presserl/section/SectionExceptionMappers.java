package info.unterrainer.presserl.section;

import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import info.unterrainer.presserl.api.ApiErrorDto;
import jakarta.ws.rs.core.MediaType;

/**
 * Maps refused section operations to their status with the {@link ApiErrorDto} body.
 */
public class SectionExceptionMappers {

    @ServerExceptionMapper
    public RestResponse<ApiErrorDto> section(SectionException e) {
        return RestResponse.ResponseBuilder.create(e.status(), new ApiErrorDto(e.errors()))
                .type(MediaType.APPLICATION_JSON_TYPE)
                .build();
    }
}
