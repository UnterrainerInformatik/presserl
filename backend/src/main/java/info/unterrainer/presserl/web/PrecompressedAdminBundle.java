package info.unterrainer.presserl.web;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Sends the Brotli variant ({@code <file>.br}, produced by the image build) of the admin bundle's
 * {@code .wasm} and {@code .js} files to clients that accept {@code br}; all other clients get the
 * original file. The request is rerouted to the {@code .br} resource so the static handler keeps
 * doing {@code Last-Modified}, {@code 304}, {@code HEAD} and ranges; on the rerouted pass a
 * headers-end handler restores the original {@code Content-Type} and adds
 * {@code Content-Encoding: br}. {@link SecurityHeaders} derives {@code Cache-Control} from the path
 * without {@code .br}, so the caching stays the same.
 */
@ApplicationScoped
public class PrecompressedAdminBundle {

    static final String BROTLI_SUFFIX = ".br";
    static final String RESOURCE_ROOT = "META-INF/resources";
    static final String VARY = "Vary";
    static final String ACCEPT_ENCODING = "Accept-Encoding";
    static final String REROUTED_CONTENT_TYPE = "presserl.brotli.content-type";

    private final Map<String, Boolean> variants = new ConcurrentHashMap<>();

    void register(@Observes Router router) {
        router.route().order(Integer.MIN_VALUE + 1).handler(ctx -> {
            // Second pass after the reroute: headers-end handlers do not survive a reroute
            String rerouted = ctx.get(REROUTED_CONTENT_TYPE);
            if (rerouted != null) {
                ctx.addHeadersEndHandler(v -> ctx.response().headers()
                        .set("Content-Type", rerouted)
                        .set("Content-Encoding", "br")
                        .set(VARY, ACCEPT_ENCODING));
                ctx.next();
                return;
            }
            String path = ctx.normalizedPath();
            HttpMethod method = ctx.request().method();
            String contentType = contentType(path);
            if (contentType == null || !(method == HttpMethod.GET || method == HttpMethod.HEAD)) {
                ctx.next();
                return;
            }
            if (!acceptsBrotli(ctx.request().getHeader(ACCEPT_ENCODING)) || !hasVariant(path)) {
                // The static handler sets its own lower-case Vary; ours replaces it
                ctx.addHeadersEndHandler(v -> ctx.response().headers().set(VARY, ACCEPT_ENCODING));
                ctx.next();
                return;
            }
            ctx.put(REROUTED_CONTENT_TYPE, contentType);
            ctx.reroute(path + BROTLI_SUFFIX);
        });
    }

    /**
     * The original {@code Content-Type} of a bundle file that may have a Brotli variant, or
     * {@code null} for every other path.
     */
    static String contentType(String path) {
        if (!path.startsWith("/admin/")) {
            return null;
        }
        if (path.endsWith(".wasm")) {
            return "application/wasm";
        }
        if (path.endsWith(".js")) {
            return "text/javascript;charset=UTF-8";
        }
        return null;
    }

    /** {@code true} when {@code br} is listed with a non-zero q-value. */
    static boolean acceptsBrotli(String acceptEncoding) {
        if (acceptEncoding == null) {
            return false;
        }
        for (String entry : acceptEncoding.split(",")) {
            String[] parts = entry.split(";");
            if (!parts[0].trim().equalsIgnoreCase("br")) {
                continue;
            }
            for (int i = 1; i < parts.length; i++) {
                String parameter = parts[i].trim();
                if (parameter.startsWith("q=")) {
                    try {
                        return Double.parseDouble(parameter.substring(2)) > 0;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Looked up once per existing bundle file; paths without an original file are not cached, so
     * requests for arbitrary names cannot grow the map.
     */
    private boolean hasVariant(String path) {
        Boolean known = variants.get(path);
        if (known != null) {
            return known;
        }
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader.getResource(RESOURCE_ROOT + path) == null) {
            return false;
        }
        return variants.computeIfAbsent(path, p -> loader.getResource(RESOURCE_ROOT + p + BROTLI_SUFFIX) != null);
    }
}
