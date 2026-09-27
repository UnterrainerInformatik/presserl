package info.unterrainer.presserl.reader;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.RestResponse.ResponseBuilder;
import org.jboss.resteasy.reactive.RestResponse.Status;

import info.unterrainer.presserl.newspaper.EffectiveSettings;
import info.unterrainer.presserl.newspaper.NewspaperSettings;
import info.unterrainer.presserl.newspaper.TextSize;
import info.unterrainer.presserl.newspaper.Visibility;
import info.unterrainer.presserl.reader.BodyRenderer.Block;
import info.unterrainer.presserl.reader.ReaderViewer.Access;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;

/**
 * Server-rendered reader pages. Texts follow the preferred language (English when preferred,
 * German otherwise).
 * <p>
 * The reader session comes from the OIDC tenant {@code reader} (code flow, session cookie). A
 * private newspaper is readable for visitors holding a newspaper role; anonymous visitors are sent to
 * {@code /login}, logged-in visitors without a role get a no-access note and {@code 404}. The decision
 * is made per request against the effective visibility. Private and personal pages are not cached.
 * <p>
 * Every page renders the effective text size (the reader's cookie, else the newspaper's
 * {@code reader.text-size}), links the fork's {@code custom.css} when present and shows the section bar
 * whenever it shows content.
 */
@Path("/")
@Produces(MediaType.TEXT_HTML + ";charset=UTF-8")
public class ReaderResource {

    static final Locale GERMAN = Locale.GERMAN;
    static final Locale ENGLISH = Locale.ENGLISH;
    static final int FRONT_PAGE_LIMIT = 30;
    static final String NO_STORE = "no-store";
    static final String PRIVATE_NO_STORE = "private, no-store";

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance frontpage(ReaderPage page, boolean loginRequired, boolean noAccess,
                ReaderArticle leadStory, List<ReaderArticle> stories);

        static native TemplateInstance article(ReaderPage page, ReaderArticle article, List<Block> blocks);

        static native TemplateInstance notFound(ReaderPage page);
    }

    @Inject
    NewspaperSettings settings;

    @Inject
    ReaderArticles articles;

    @Inject
    SecurityIdentity identity;

    @Inject
    ThemeFiles theme;

    @GET
    public Uni<RestResponse<String>> frontpage(@Context HttpHeaders headers, @Context UriInfo uri,
            @CookieParam(TextSizeResource.COOKIE) String textSize) {
        Locale locale = locale(headers);
        ReaderViewer viewer = ReaderViewer.of(identity);
        return settings.effective().flatMap(s -> {
            boolean privateNewspaper = s.visibility() == Visibility.PRIVATE;
            boolean readable = !privateNewspaper || viewer.access() == Access.ENTITLED;
            ReaderPage page = page(locale, s, viewer, textSize, uri);
            if (!readable) {
                return render(Templates.frontpage(page, !viewer.loggedIn(), viewer.loggedIn(), null, List.of()),
                        locale, Status.OK, noStore(s, viewer));
            }
            // one after the other: both queries use the request's reactive session
            return articles.frontPage(FRONT_PAGE_LIMIT).flatMap(list -> articles.sections().flatMap(sections -> render(
                    Templates.frontpage(page.withSections(sections), false, false,
                            list.isEmpty() ? null : list.get(0),
                            list.isEmpty() ? List.of() : list.subList(1, list.size())),
                    locale, Status.OK, noStore(s, viewer))));
        });
    }

    /**
     * The article page; a malformed or unknown id and an unpublished article get the same 404 page.
     * In a private newspaper an anonymous visitor is sent to the login for every id, and a logged-in
     * visitor without a newspaper role gets the 404 page.
     */
    @GET
    @Path("articles/{id}")
    public Uni<RestResponse<String>> article(@PathParam("id") String id, @Context HttpHeaders headers,
            @Context UriInfo uri, @CookieParam(TextSizeResource.COOKIE) String textSize) {
        Locale locale = locale(headers);
        ReaderViewer viewer = ReaderViewer.of(identity);
        return settings.effective().flatMap(s -> {
            boolean privateNewspaper = s.visibility() == Visibility.PRIVATE;
            if (privateNewspaper && !viewer.loggedIn()) {
                return Uni.createFrom().item(redirect(LoginTarget.loginForArticle(id)));
            }
            boolean readable = !privateNewspaper || viewer.access() == Access.ENTITLED;
            ReaderPage page = page(locale, s, viewer, textSize, uri);
            boolean noStore = noStore(s, viewer);
            if (!readable) {
                return render(Templates.notFound(page), locale, Status.NOT_FOUND, noStore);
            }
            Uni<Optional<ReaderArticle>> found = id.matches("\\d{1,18}")
                    ? articles.article(Long.parseLong(id))
                    : Uni.createFrom().item(Optional.empty());
            // one after the other: both queries use the request's reactive session
            return found.flatMap(article -> articles.sections().flatMap(sections -> {
                ReaderPage withBar = page.withSections(sections);
                return article
                        .map(a -> render(Templates.article(withBar, a, BodyRenderer.blocks(a.body())), locale,
                                Status.OK, noStore))
                        .orElseGet(() -> render(Templates.notFound(withBar), locale, Status.NOT_FOUND, noStore));
            }));
        });
    }

    /**
     * Reached once the reader session exists: the tenant's code flow runs first for an anonymous
     * visitor ({@code /login} requires authentication), and restores {@code next} afterwards.
     */
    @GET
    @Path("login")
    public RestResponse<String> login(@QueryParam("next") String next) {
        return redirect(LoginTarget.of(next));
    }

    /**
     * For a logged-in visitor the tenant intercepts {@code /logout} (RP-initiated logout, back to
     * {@code /}); only anonymous visitors reach this method.
     */
    @GET
    @Path("logout")
    public RestResponse<String> logout() {
        return redirect(LoginTarget.HOME);
    }

    private static RestResponse<String> redirect(String location) {
        return ResponseBuilder.<String> seeOther(URI.create(location)).header(HttpHeaders.CACHE_CONTROL, NO_STORE)
                .build();
    }

    private ReaderPage page(Locale locale, EffectiveSettings s, ReaderViewer viewer, String textSizeCookie,
            UriInfo uri) {
        TextSize textSize = TextSizeResource.parse(textSizeCookie).orElse(s.readerTextSize());
        URI request = uri.getRequestUri();
        String path = request.getRawQuery() == null ? request.getRawPath()
                : request.getRawPath() + "?" + request.getRawQuery();
        return new ReaderPage(locale.getLanguage(), s.name(), s.subtitle(), viewer.displayName(), textSize.value(),
                theme.customCssPresent(), List.of(), path);
    }

    private static boolean noStore(EffectiveSettings s, ReaderViewer viewer) {
        return s.visibility() == Visibility.PRIVATE || viewer.loggedIn();
    }

    private static Uni<RestResponse<String>> render(TemplateInstance page, Locale locale, Status status,
            boolean noStore) {
        return page.setLocale(locale).createUni()
                .map(html -> {
                    ResponseBuilder<String> response = ResponseBuilder.create(status, html)
                            .header(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE + ", " + HttpHeaders.COOKIE);
                    if (noStore) {
                        response.header(HttpHeaders.CACHE_CONTROL, PRIVATE_NO_STORE);
                    }
                    return response.build();
                });
    }

    /**
     * English when the most preferred language is English, German otherwise (also without header).
     */
    static Locale locale(HttpHeaders headers) {
        List<Locale> accepted = headers.getAcceptableLanguages();
        return !accepted.isEmpty() && ENGLISH.getLanguage().equals(accepted.get(0).getLanguage()) ? ENGLISH : GERMAN;
    }
}
