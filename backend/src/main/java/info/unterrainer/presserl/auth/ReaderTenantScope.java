package info.unterrainer.presserl.auth;

import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.oidc.runtime.OidcUtils;
import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Keeps the reader session to the reader paths. Quarkus OIDC selects the tenant from a
 * {@code q_session_<tenant>} cookie before it looks at {@code tenant-paths}, so a reader's session
 * cookie would otherwise authenticate {@code /api} requests. Every request outside
 * {@code quarkus.oidc.reader.tenant-paths} is pinned to the default (bearer token) tenant before
 * authentication runs.
 */
@ApplicationScoped
public class ReaderTenantScope {

    @ConfigProperty(name = "quarkus.oidc.reader.tenant-paths")
    List<String> readerPaths;

    void register(@Observes Router router) {
        router.route().order(Integer.MIN_VALUE).handler(ctx -> {
            if (!matches(readerPaths, ctx.normalizedPath())) {
                ctx.put(OidcUtils.TENANT_ID_ATTRIBUTE, OidcUtils.DEFAULT_TENANT_ID);
            }
            ctx.next();
        });
    }

    /**
     * Whether {@code path} matches one of the patterns: an exact path, or a prefix ending in {@code /*}.
     */
    static boolean matches(List<String> patterns, String path) {
        return patterns.stream().anyMatch(pattern -> pattern.endsWith("/*")
                ? path.startsWith(pattern.substring(0, pattern.length() - 1))
                : path.equals(pattern));
    }
}
