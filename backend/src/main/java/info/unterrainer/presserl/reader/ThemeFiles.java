package info.unterrainer.presserl.reader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.jboss.logging.Logger;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Serves the fork's theme directory ({@code presserl.theme.dir}) read-only at {@code /theme/*}: only
 * allow-listed file types, only regular files whose real path (symlinks resolved) lies inside the
 * directory; everything else is {@code 404}. Responses are revalidated ({@code no-cache}) so a changed
 * theme applies on the next page load. A missing directory is served as an empty one.
 * <p>
 * Registered on the Vert.x router, so it bypasses the API permissions and the reader OIDC tenant.
 */
@ApplicationScoped
public class ThemeFiles {

    static final String PREFIX = "/theme/";
    static final String CUSTOM_CSS = "custom.css";
    static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("css", "text/css;charset=UTF-8"),
            Map.entry("woff2", "font/woff2"),
            Map.entry("woff", "font/woff"),
            Map.entry("ttf", "font/ttf"),
            Map.entry("otf", "font/otf"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("ico", "image/x-icon"));

    private static final Logger LOG = Logger.getLogger(ThemeFiles.class);
    private static final DateTimeFormatter HTTP_DATE = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC);

    Path dir;

    @Inject
    ThemeFiles(ReaderConfig config) {
        this(config.theme().dir());
    }

    ThemeFiles(String dir) {
        this.dir = Path.of(dir);
    }

    void register(@Observes Router router) {
        if (!Files.isDirectory(dir)) {
            LOG.infof("Theme directory %s does not exist; the reader uses the default theme only", dir);
        }
        router.route(PREFIX + "*").method(HttpMethod.GET).method(HttpMethod.HEAD).blockingHandler(this::serve, false);
    }

    /**
     * Whether the theme directory holds a {@code custom.css} right now.
     */
    public boolean customCssPresent() {
        return resolve(CUSTOM_CSS).isPresent();
    }

    /**
     * The real path of {@code relative} inside the theme directory, when it is a servable file.
     */
    Optional<Path> resolve(String relative) {
        if (relative.isEmpty() || relative.indexOf('\\') >= 0 || relative.indexOf('\0') >= 0
                || type(relative).isEmpty()) {
            return Optional.empty();
        }
        try {
            Path root = dir.toRealPath();
            Path candidate = root.resolve(relative).normalize();
            if (!candidate.startsWith(root)) {
                return Optional.empty();
            }
            Path real = candidate.toRealPath();
            return real.startsWith(root) && Files.isRegularFile(real) ? Optional.of(real) : Optional.empty();
        } catch (IOException | InvalidPathException e) {
            return Optional.empty();
        }
    }

    /**
     * The content type for the extension of {@code path}, if the type is allowed.
     */
    static Optional<String> type(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 || dot < path.lastIndexOf('/') ? Optional.empty()
                : Optional.ofNullable(TYPES.get(path.substring(dot + 1).toLowerCase(Locale.ROOT)));
    }

    private void serve(RoutingContext ctx) {
        String path = ctx.normalizedPath();
        Optional<Path> file = path.startsWith(PREFIX) ? resolve(path.substring(PREFIX.length())) : Optional.empty();
        if (file.isEmpty()) {
            ctx.response().setStatusCode(404).end();
            return;
        }
        Instant modified;
        try {
            modified = Files.getLastModifiedTime(file.get()).toInstant().truncatedTo(ChronoUnit.SECONDS);
        } catch (IOException e) {
            ctx.response().setStatusCode(404).end();
            return;
        }
        ctx.response()
                .putHeader(HttpHeaders.CACHE_CONTROL, "no-cache")
                .putHeader(HttpHeaders.LAST_MODIFIED, HTTP_DATE.format(modified))
                .putHeader("X-Content-Type-Options", "nosniff");
        if (notModifiedSince(ctx.request().getHeader(HttpHeaders.IF_MODIFIED_SINCE), modified)) {
            ctx.response().setStatusCode(304).end();
            return;
        }
        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, type(path).orElseThrow());
        if (ctx.request().method() == HttpMethod.HEAD) {
            ctx.response().end();
        } else {
            ctx.response().sendFile(file.get().toString());
        }
    }

    private static boolean notModifiedSince(String header, Instant modified) {
        if (header == null) {
            return false;
        }
        try {
            return !modified.isAfter(Instant.from(HTTP_DATE.parse(header)));
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
