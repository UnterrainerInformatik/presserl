package info.unterrainer.presserl.reader;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.RestResponse.ResponseBuilder;
import org.jboss.resteasy.reactive.RestResponse.Status;

import info.unterrainer.presserl.account.AccountRequestValidator;
import info.unterrainer.presserl.newspaper.EffectiveSettings;
import info.unterrainer.presserl.newspaper.NewspaperSettings;
import info.unterrainer.presserl.newspaper.TextSize;
import info.unterrainer.presserl.newspaper.Visibility;
import info.unterrainer.presserl.reader.BodyRenderer.Block;
import info.unterrainer.presserl.reader.ReaderViewer.Access;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateData;
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
 * whenever it shows content, except the print views.
 * <p>
 * The front page lists the articles visible to readers (published in a live issue), weighted ones
 * first; {@code ?section=<id>} filters it by section. {@code /legal-notice} shows the theme's legal
 * notice to every visitor, and every page but the print views links it from the footer;
 * {@code /account-deletion} explains to every visitor how accounts are deleted and is linked from the
 * legal notice.
 * <p>
 * Issues: {@code /issues} lists the published issues, {@code /issues/{id}} shows one; the front page
 * masthead names the newest published issue. Print views: {@code /print/article/{id}} and
 * {@code /print/issue/{id}}. All of them follow the same access rules as the article page.
 */
@Path("/")
@Produces(MediaType.TEXT_HTML + ";charset=UTF-8")
public class ReaderResource {

    static final Locale GERMAN = Locale.GERMAN;
    static final Locale ENGLISH = Locale.ENGLISH;
    static final int FRONT_PAGE_LIMIT = 30;
    static final String NO_STORE = "no-store";
    static final String PRIVATE_NO_STORE = "private, no-store";
    static final String ID = "\\d{1,18}";

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance frontpage(ReaderPage page, boolean loginRequired, boolean noAccess,
                ReaderSection activeSection, ReaderArticle leadStory, List<ReaderArticle> stories);

        static native TemplateInstance article(ReaderPage page, ReaderArticle article, List<Block> blocks);

        static native TemplateInstance notFound(ReaderPage page);

        static native TemplateInstance legalNotice(ReaderPage page, List<List<String>> paragraphs);

        static native TemplateInstance accountDeletion(ReaderPage page);

        static native TemplateInstance issues(ReaderPage page, List<ReaderIssue> issues);

        static native TemplateInstance issue(ReaderPage page, ReaderIssue issue, ReaderArticle leadStory,
                List<ReaderArticle> stories);

        static native TemplateInstance printArticle(ReaderPage page, PrintStory story);

        static native TemplateInstance printIssue(ReaderPage page, ReaderIssue issue, PrintStory leadStory,
                List<PrintStory> stories);
    }

    /**
     * An article of a print view with its body blocks.
     */
    @TemplateData
    public record PrintStory(ReaderArticle article, List<Block> blocks) {

        /**
         * @param images the body images from {@link ReaderArticles#bodyImages}
         */
        static PrintStory of(ReaderArticle article, Map<Long, ReaderImage> images) {
            return new PrintStory(article, BodyRenderer.blocks(article.body(), images));
        }
    }

    /**
     * A visitor who may read the newspaper, with the page chrome to render for them.
     */
    private record Visit(Locale locale, ReaderPage page, boolean noStore) {
    }

    @Inject
    NewspaperSettings settings;

    @Inject
    ReaderArticles articles;

    @Inject
    ReaderIssues issues;

    @Inject
    SecurityIdentity identity;

    @Inject
    ThemeFiles theme;

    /**
     * The front page; {@code ?section=<id>} keeps only that section's articles in the same order and
     * layout. A malformed or unknown section id gets the 404 page, other query parameters are ignored.
     */
    @GET
    public Uni<RestResponse<String>> frontpage(@Context HttpHeaders headers, @Context UriInfo uri,
            @CookieParam(TextSizeResource.COOKIE) String textSize) {
        String section = sectionFilter(uri);
        Locale locale = locale(headers);
        ReaderViewer viewer = ReaderViewer.of(identity);
        return settings.effective().flatMap(s -> {
            boolean privateNewspaper = s.visibility() == Visibility.PRIVATE;
            boolean readable = !privateNewspaper || viewer.access() == Access.ENTITLED;
            ReaderPage page = page(locale, s, viewer, textSize, uri);
            if (!readable) {
                return render(Templates.frontpage(page, !viewer.loggedIn(), viewer.loggedIn(), null, null, List.of()),
                        locale, Status.OK, noStore(s, viewer));
            }
            Visit visit = new Visit(locale, page, noStore(s, viewer));
            // one after the other: all queries use the request's reactive session
            return articles.sections().flatMap(sections -> {
                Optional<ReaderSection> active = section == null ? Optional.empty()
                        : sections.stream().filter(candidate -> section.matches(ID)
                                && candidate.id() == Long.parseLong(section)).findFirst();
                ReaderPage withSections = page.withSections(sections)
                        .withActiveSection(active.map(ReaderSection::id).orElse(null));
                if (section != null && active.isEmpty()) {
                    return notFound(visit, withSections);
                }
                return articles.frontPage(active.map(ReaderSection::id).orElse(null), FRONT_PAGE_LIMIT)
                        .flatMap(list -> issues.current().flatMap(current -> render(
                                Templates.frontpage(withSections.withIssueLine(current
                                        .map(c -> new ReaderPage.IssueLine(c.issue(), true, c.publishedCount() > 1))
                                        .orElse(null)), false, false, active.orElse(null),
                                        list.isEmpty() ? null : list.get(0),
                                        list.isEmpty() ? List.of() : list.subList(1, list.size())),
                                locale, Status.OK, visit.noStore())));
            });
        });
    }

    /**
     * The article page; a malformed or unknown id and an unpublished article get the same 404 page.
     */
    @GET
    @Path("articles/{id}")
    public Uni<RestResponse<String>> article(@PathParam("id") String id, @Context HttpHeaders headers,
            @Context UriInfo uri, @CookieParam(TextSizeResource.COOKIE) String textSize) {
        return guarded("/articles/" + LoginTarget.segment(id), headers, uri, textSize,
                visit -> publishedArticle(id).flatMap(article -> withSections(visit).flatMap(page -> article
                        .map(a -> articles.bodyImages(List.of(a.body())).flatMap(images -> render(
                                Templates.article(page, a, BodyRenderer.blocks(a.body(), images)), visit.locale(),
                                Status.OK, visit.noStore())))
                        .orElseGet(() -> notFound(visit, page)))));
    }

    /**
     * The archive of the published issues, highest number first.
     */
    @GET
    @Path("issues")
    public Uni<RestResponse<String>> issues(@Context HttpHeaders headers, @Context UriInfo uri,
            @CookieParam(TextSizeResource.COOKIE) String textSize) {
        return guarded("/issues", headers, uri, textSize, visit -> issues.archive()
                .flatMap(list -> withSections(visit).flatMap(page -> render(Templates.issues(page, list),
                        visit.locale(), Status.OK, visit.noStore()))));
    }

    /**
     * A published issue as a newspaper page: its published articles in issue order, the first as lead
     * story. A malformed or unknown id and an unpublished issue get the 404 page.
     */
    @GET
    @Path("issues/{id}")
    public Uni<RestResponse<String>> issue(@PathParam("id") String id, @Context HttpHeaders headers,
            @Context UriInfo uri, @CookieParam(TextSizeResource.COOKIE) String textSize) {
        return guarded("/issues/" + LoginTarget.segment(id), headers, uri, textSize,
                visit -> publishedIssue(id).flatMap(issue -> issue.isEmpty()
                        ? withSections(visit).flatMap(page -> notFound(visit, page))
                        : issues.articles(issue.get().id()).flatMap(list -> issues.current()
                                .flatMap(current -> withSections(visit).flatMap(page -> render(
                                        Templates.issue(page.withIssueLine(new ReaderPage.IssueLine(issue.get(), false,
                                                current.map(c -> c.publishedCount() > 1).orElse(false))),
                                                issue.get(), list.isEmpty() ? null : list.get(0),
                                                list.isEmpty() ? List.of() : list.subList(1, list.size())),
                                        visit.locale(), Status.OK, visit.noStore()))))));
    }

    /**
     * The print view of a published article; otherwise the 404 page.
     */
    @GET
    @Path("print/article/{id}")
    public Uni<RestResponse<String>> printArticle(@PathParam("id") String id, @Context HttpHeaders headers,
            @Context UriInfo uri, @CookieParam(TextSizeResource.COOKIE) String textSize) {
        return guarded("/print/article/" + LoginTarget.segment(id), headers, uri, textSize,
                visit -> publishedArticle(id).flatMap(article -> article.isEmpty()
                        ? withSections(visit).flatMap(page -> notFound(visit, page))
                        : articles.bodyImages(List.of(article.get().body())).flatMap(images -> render(
                                Templates.printArticle(visit.page(), PrintStory.of(article.get(), images)),
                                visit.locale(), Status.OK, visit.noStore()))));
    }

    /**
     * The print view of a published issue: its published articles in issue order, the first on the
     * front page; otherwise the 404 page.
     */
    @GET
    @Path("print/issue/{id}")
    public Uni<RestResponse<String>> printIssue(@PathParam("id") String id, @Context HttpHeaders headers,
            @Context UriInfo uri, @CookieParam(TextSizeResource.COOKIE) String textSize) {
        return guarded("/print/issue/" + LoginTarget.segment(id), headers, uri, textSize,
                visit -> publishedIssue(id).flatMap(issue -> issue.isEmpty()
                        ? withSections(visit).flatMap(page -> notFound(visit, page))
                        : issues.articles(issue.get().id()).flatMap(list -> articles
                                .bodyImages(list.stream().map(ReaderArticle::body).toList()).flatMap(images -> {
                                    List<PrintStory> stories = list.stream().map(a -> PrintStory.of(a, images))
                                            .toList();
                                    return render(Templates.printIssue(
                                            visit.page().withIssueLine(new ReaderPage.IssueLine(issue.get(), false,
                                                    false)),
                                            issue.get(),
                                            stories.isEmpty() ? null : stories.get(0),
                                            stories.isEmpty() ? List.of() : stories.subList(1, stories.size())),
                                            visit.locale(), Status.OK, visit.noStore());
                                }))));
    }

    /**
     * The theme's legal notice; the 404 page without one. Public in every newspaper: no login redirect,
     * and the section bar only for visitors who may read the newspaper.
     */
    @GET
    @Path("legal-notice")
    public Uni<RestResponse<String>> legalNotice(@Context HttpHeaders headers, @Context UriInfo uri,
            @CookieParam(TextSizeResource.COOKIE) String textSize) {
        Locale locale = locale(headers);
        ReaderViewer viewer = ReaderViewer.of(identity);
        return settings.effective().flatMap(s -> {
            boolean readable = s.visibility() != Visibility.PRIVATE || viewer.access() == Access.ENTITLED;
            // the public page's cache headers also in a private newspaper; personal only when logged in
            Visit visit = new Visit(locale, page(locale, s, viewer, textSize, uri), viewer.loggedIn());
            Uni<ReaderPage> page = readable ? withSections(visit) : Uni.createFrom().item(visit.page());
            return page.flatMap(p -> theme.legalNotice()
                    .map(paragraphs -> render(Templates.legalNotice(p, paragraphs), locale, Status.OK, visit.noStore()))
                    .orElseGet(() -> notFound(visit, p)));
        });
    }

    /**
     * How accounts of this newspaper are deleted, linking the legal notice when there is one. Public
     * like {@link #legalNotice}, with the same cache headers.
     */
    @GET
    @Path("account-deletion")
    public Uni<RestResponse<String>> accountDeletion(@Context HttpHeaders headers, @Context UriInfo uri,
            @CookieParam(TextSizeResource.COOKIE) String textSize) {
        Locale locale = locale(headers);
        ReaderViewer viewer = ReaderViewer.of(identity);
        return settings.effective().flatMap(s -> {
            boolean readable = s.visibility() != Visibility.PRIVATE || viewer.access() == Access.ENTITLED;
            Visit visit = new Visit(locale, page(locale, s, viewer, textSize, uri), viewer.loggedIn());
            Uni<ReaderPage> page = readable ? withSections(visit) : Uni.createFrom().item(visit.page());
            return page.flatMap(p -> render(Templates.accountDeletion(p), locale, Status.OK, visit.noStore()));
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
     * The address of the QR code on the account slip ({@code /qr?u=<username>#pw=<pass-phrase>}). Lies
     * outside the reader tenant, so it neither reads nor creates a session. Sends the visitor to the
     * login with {@code u} as {@code login_hint} when it is a valid username; the empty fragment of the
     * {@code Location} replaces the scanned one, so the pass-phrase does not travel on.
     */
    @GET
    @Path("qr")
    public RestResponse<String> qr(@QueryParam("u") String username) {
        return redirect(
                AccountRequestValidator.isUsername(username) ? "/login?login_hint=" + username + "#" : "/login#");
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

    /**
     * Applies the newspaper's visibility before {@code content} renders the page: in a private
     * newspaper an anonymous visitor is sent to the login (returning to {@code path}) for every id, and
     * a logged-in visitor without a newspaper role gets the 404 page.
     *
     * @param path the requested reader path, segments encoded ({@link LoginTarget#segment})
     */
    private Uni<RestResponse<String>> guarded(String path, HttpHeaders headers, UriInfo uri, String textSize,
            Function<Visit, Uni<RestResponse<String>>> content) {
        Locale locale = locale(headers);
        ReaderViewer viewer = ReaderViewer.of(identity);
        return settings.effective().flatMap(s -> {
            boolean privateNewspaper = s.visibility() == Visibility.PRIVATE;
            if (privateNewspaper && !viewer.loggedIn()) {
                return Uni.createFrom().item(redirect(LoginTarget.loginFor(path)));
            }
            Visit visit = new Visit(locale, page(locale, s, viewer, textSize, uri), noStore(s, viewer));
            if (privateNewspaper && viewer.access() != Access.ENTITLED) {
                return notFound(visit, visit.page());
            }
            return content.apply(visit);
        });
    }

    /**
     * The value of the {@code section} query parameter, {@code ""} when it is given without value and
     * {@code null} when it is absent.
     */
    private static String sectionFilter(UriInfo uri) {
        List<String> values = uri.getQueryParameters().get("section");
        return values == null ? null : values.isEmpty() || values.get(0) == null ? "" : values.get(0);
    }

    private Uni<Optional<ReaderArticle>> publishedArticle(String id) {
        return id.matches(ID) ? articles.article(Long.parseLong(id)) : Uni.createFrom().item(Optional.empty());
    }

    private Uni<Optional<ReaderIssue>> publishedIssue(String id) {
        return id.matches(ID) ? issues.published(Long.parseLong(id)) : Uni.createFrom().item(Optional.empty());
    }

    /**
     * The visitor's page with the section bar. Queries run one after the other: they share the
     * request's reactive session.
     */
    private Uni<ReaderPage> withSections(Visit visit) {
        return articles.sections().map(sections -> visit.page().withSections(sections));
    }

    private static Uni<RestResponse<String>> notFound(Visit visit, ReaderPage page) {
        return render(Templates.notFound(page), visit.locale(), Status.NOT_FOUND, visit.noStore());
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
                theme.customCssPresent(), List.of(), path, null, null, theme.legalNotice().isPresent());
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
