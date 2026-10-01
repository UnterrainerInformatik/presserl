package info.unterrainer.presserl.reader;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.jboss.logging.Logger;

import io.vertx.core.buffer.Buffer;
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
 * Also answers {@code /favicon.ico} at the root with the theme's {@code favicon.ico}, or the bundled
 * Presserl icon when the theme has none.
 * <p>
 * Registered on the Vert.x router, so it bypasses the API permissions and the reader OIDC tenant.
 */
@ApplicationScoped
public class ThemeFiles {

    static final String PREFIX = "/theme/";
    static final String CUSTOM_CSS = "custom.css";
    static final String LEGAL_NOTICE = "legal-notice.txt";
    static final long LEGAL_NOTICE_MAX_BYTES = 64 * 1024;
    static final String FAVICON_SVG = "favicon.svg";
    static final String FAVICON_ICO = "favicon.ico";
    static final String APPLE_TOUCH_ICON = "apple-touch-icon.png";
    static final String DEFAULT_ICONS = "/reader/icons/";
    static final String ROOT_FAVICON = "/favicon.ico";
    static final String BUNDLED_FAVICON = "META-INF/resources/reader/icons/" + FAVICON_ICO;
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
    private Buffer bundledFavicon;

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
        bundledFavicon = Buffer.buffer(bundled(BUNDLED_FAVICON));
        router.route(ROOT_FAVICON).method(HttpMethod.GET).method(HttpMethod.HEAD).blockingHandler(this::serveFavicon,
                false);
    }

    /**
     * Whether the theme directory holds a {@code custom.css} right now.
     */
    public boolean customCssPresent() {
        return resolve(CUSTOM_CSS).isPresent();
    }

    /**
     * The icons reader pages link right now: each one the theme's file under {@code /theme/} while the
     * theme directory holds it, else the bundled default under {@code /reader/icons/}.
     */
    public ReaderPage.Icons icons() {
        return new ReaderPage.Icons(icon(FAVICON_SVG), icon(FAVICON_ICO), icon(APPLE_TOUCH_ICON));
    }

    String icon(String name) {
        return (resolve(name).isPresent() ? PREFIX : DEFAULT_ICONS) + name;
    }

    /**
     * The theme's legal notice ({@code legal-notice.txt}, UTF-8) as paragraphs of lines, read on every
     * call so a change shows on the next page load. Blank lines separate paragraphs; line breaks within
     * a paragraph are kept, trailing whitespace is dropped. Empty when the file is missing, holds only
     * whitespace, is larger than 64 KiB or cannot be read as UTF-8 (the last two logged as a warning).
     * The file is never served under {@code /theme/}.
     */
    public Optional<List<List<String>>> legalNotice() {
        Optional<Path> file = file(LEGAL_NOTICE);
        if (file.isEmpty()) {
            return Optional.empty();
        }
        String text;
        try {
            if (Files.size(file.get()) > LEGAL_NOTICE_MAX_BYTES) {
                LOG.warnf("Ignoring %s: larger than %d bytes", LEGAL_NOTICE, LEGAL_NOTICE_MAX_BYTES);
                return Optional.empty();
            }
            text = Files.readString(file.get(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warnf("Ignoring %s: cannot be read as UTF-8 text (%s)", LEGAL_NOTICE, e.toString());
            return Optional.empty();
        }
        List<List<String>> paragraphs = paragraphs(text);
        return paragraphs.isEmpty() ? Optional.empty() : Optional.of(paragraphs);
    }

    /**
     * Splits {@code text} into paragraphs at lines holding only whitespace; lines lose trailing
     * whitespace, empty paragraphs are dropped.
     */
    static List<List<String>> paragraphs(String text) {
        List<List<String>> paragraphs = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String line : text.replace("\uFEFF", "").split("\\R", -1)) {
            String kept = line.stripTrailing();
            if (kept.isBlank()) {
                if (!current.isEmpty()) {
                    paragraphs.add(List.copyOf(current));
                    current.clear();
                }
            } else {
                current.add(kept);
            }
        }
        if (!current.isEmpty()) {
            paragraphs.add(List.copyOf(current));
        }
        return List.copyOf(paragraphs);
    }

    /**
     * The real path of {@code relative} inside the theme directory, when it is a servable file.
     */
    Optional<Path> resolve(String relative) {
        return type(relative).isEmpty() ? Optional.empty() : file(relative);
    }

    /**
     * The real path of {@code relative} inside the theme directory (symlinks resolved), when it is a
     * regular file there, whatever its type.
     */
    private Optional<Path> file(String relative) {
        if (relative.isEmpty() || relative.indexOf('\\') >= 0 || relative.indexOf('\0') >= 0) {
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
        send(ctx, file.get(), type(path).orElseThrow());
    }

    private void serveFavicon(RoutingContext ctx) {
        Optional<Path> file = resolve(FAVICON_ICO);
        if (file.isPresent()) {
            send(ctx, file.get(), TYPES.get("ico"));
            return;
        }
        ctx.response()
                .putHeader(HttpHeaders.CACHE_CONTROL, "no-cache")
                .putHeader("X-Content-Type-Options", "nosniff")
                .putHeader(HttpHeaders.CONTENT_TYPE, TYPES.get("ico"));
        if (ctx.request().method() == HttpMethod.HEAD) {
            ctx.response().putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(bundledFavicon.length())).end();
        } else {
            ctx.response().end(bundledFavicon);
        }
    }

    private static byte[] bundled(String resource) {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Bundled resource " + resource + " is missing");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void send(RoutingContext ctx, Path file, String contentType) {
        Instant modified;
        try {
            modified = Files.getLastModifiedTime(file).toInstant().truncatedTo(ChronoUnit.SECONDS);
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
        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, contentType);
        if (ctx.request().method() == HttpMethod.HEAD) {
            ctx.response().end();
        } else {
            ctx.response().sendFile(file.toString());
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
