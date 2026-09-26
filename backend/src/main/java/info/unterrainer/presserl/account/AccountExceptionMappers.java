package info.unterrainer.presserl.account;

import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import info.unterrainer.presserl.api.ApiErrorDto;
import jakarta.ws.rs.core.MediaType;

/**
 * Maps refused account operations to their status with the {@link ApiErrorDto} body.
 */
public class AccountExceptionMappers {

    @ServerExceptionMapper
    public RestResponse<ApiErrorDto> account(AccountException e) {
        return RestResponse.ResponseBuilder.create(e.status(), new ApiErrorDto(e.errors()))
                .type(MediaType.APPLICATION_JSON_TYPE)
                .build();
    }
}
