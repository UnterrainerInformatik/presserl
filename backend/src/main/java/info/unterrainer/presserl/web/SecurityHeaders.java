package info.unterrainer.presserl.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import info.unterrainer.presserl.auth.OidcConfig;
import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Sets the Content-Security-Policy on every response (reader and API get the reader policy,
 * {@code /admin} the admin policy) and redirects {@code /admin} to {@code /admin/}.
 * Registered on the Vert.x router so static resources are covered too.
 * <p>
 * Compose injects a {@code <style>} into its shadow DOM; the admin bundle lists that style's CSP
 * hashes in {@code csp-style-hashes.txt} (verified by the admin build), and they are allowed here.
 */
@ApplicationScoped
public class SecurityHeaders {

    static final String CSP = "Content-Security-Policy";
    static final String READER_POLICY = "default-src 'self'; img-src 'self' data:; frame-ancestors 'none'; "
            + "base-uri 'self'; form-action 'self'";
    static final String STYLE_HASHES = "META-INF/resources/admin/csp-style-hashes.txt";

    @Inject
    OidcConfig oidc;

    void register(@Observes Router router) {
        String adminPolicy = adminPolicy(oidc.issuerOrigin(), styleHashes());
        router.route().order(Integer.MIN_VALUE).handler(ctx -> {
            String path = ctx.normalizedPath();
            if (path.equals("/admin")) {
                String query = ctx.request().query();
                ctx.redirect(query == null ? "/admin/" : "/admin/?" + query);
                return;
            }
            // Dev UI and health endpoints under /q/ bring their own resources
            if (!path.startsWith("/q/")) {
                String policy = path.startsWith("/admin/") ? adminPolicy : READER_POLICY;
                ctx.addHeadersEndHandler(v -> ctx.response().headers().set(CSP, policy));
            }
            ctx.next();
        });
    }

    static String adminPolicy(String issuerOrigin, List<String> styleHashes) {
        String styleSrc = styleHashes.isEmpty() ? "" : "; style-src 'self' " + String.join(" ", styleHashes);
        return "default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; connect-src 'self' " + issuerOrigin
                + styleSrc + "; img-src 'self' data:; frame-ancestors 'none'; base-uri 'self'; form-action 'self'";
    }

    /**
     * Hash source expressions from the admin bundle; none when the bundle is absent (backend-only dev).
     */
    static List<String> styleHashes() {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(STYLE_HASHES)) {
            if (in == null) {
                return List.of();
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .filter(line -> line.matches("'sha(256|384|512)-[A-Za-z0-9+/]+=*'"))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
