package info.unterrainer.presserl.web;

import info.unterrainer.presserl.media.MediaLimits;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Keeps the former 10M body limit for every path except the media upload: the HTTP layer allows up to
 * {@code quarkus.http.limits.max-body-size} (64M) so {@code POST /api/media} can accept
 * {@code presserl.media.max-size}. Requests declaring a larger {@code Content-Length} are answered
 * {@code 413} before routing; bodies without a length are only bounded by the HTTP layer.
 */
@ApplicationScoped
public class BodyLimits {

    static final String MEDIA_UPLOAD_PATH = "/api/media";
    static final String TOO_LARGE_BODY = "{\"errors\":[{\"field\":null,\"message\":\"request body larger than 10M\"}]}";

    void register(@Observes Router router) {
        router.route().order(Integer.MIN_VALUE + 1).handler(ctx -> {
            String length = ctx.request().getHeader(HttpHeaders.CONTENT_LENGTH);
            if (!MEDIA_UPLOAD_PATH.equals(ctx.normalizedPath()) && exceeds(length, MediaLimits.OTHER_BODY_LIMIT)) {
                ctx.response()
                        .setStatusCode(413)
                        .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .putHeader(HttpHeaders.CONNECTION, "close")
                        .end(TOO_LARGE_BODY);
                return;
            }
            ctx.next();
        });
    }

    static boolean exceeds(String contentLength, long limit) {
        if (contentLength == null) {
            return false;
        }
        try {
            return Long.parseLong(contentLength.trim()) > limit;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
