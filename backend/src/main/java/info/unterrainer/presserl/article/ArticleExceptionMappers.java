package info.unterrainer.presserl.article;

import java.util.List;

import org.hibernate.StaleStateException;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;

/**
 * Maps refused article operations to their status with the {@link ApiErrorDto} body.
 */
public class ArticleExceptionMappers {

    private static final String CONCURRENT_CHANGE = "article was changed concurrently; reload it";

    @ServerExceptionMapper
    public RestResponse<ApiErrorDto> article(ArticleException e) {
        return error(e.status(), new ApiErrorDto(e.errors()));
    }

    @ServerExceptionMapper
    public RestResponse<ApiErrorDto> optimisticLock(OptimisticLockException e) {
        return error(Status.CONFLICT, new ApiErrorDto(List.of(new FieldError(null, CONCURRENT_CHANGE))));
    }

    @ServerExceptionMapper
    public RestResponse<ApiErrorDto> staleState(StaleStateException e) {
        return error(Status.CONFLICT, new ApiErrorDto(List.of(new FieldError(null, CONCURRENT_CHANGE))));
    }

    private static RestResponse<ApiErrorDto> error(Status status, ApiErrorDto body) {
        return RestResponse.ResponseBuilder.create(status, body).type(MediaType.APPLICATION_JSON_TYPE).build();
    }
}
