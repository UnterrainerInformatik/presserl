package info.unterrainer.presserl.media;

import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import info.unterrainer.presserl.api.ApiErrorDto;
import jakarta.ws.rs.core.MediaType;

/**
 * Maps refused media operations to their status with the {@link ApiErrorDto} body.
 */
public class MediaExceptionMappers {

    @ServerExceptionMapper
    public RestResponse<ApiErrorDto> media(MediaException e) {
        return RestResponse.ResponseBuilder.create(e.status(), new ApiErrorDto(e.errors()))
                .type(MediaType.APPLICATION_JSON_TYPE)
                .build();
    }
}
