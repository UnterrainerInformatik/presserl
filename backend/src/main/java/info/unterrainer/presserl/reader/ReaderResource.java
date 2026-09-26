package info.unterrainer.presserl.reader;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.RestResponse.ResponseBuilder;
import org.jboss.resteasy.reactive.RestResponse.Status;

import info.unterrainer.presserl.newspaper.EffectiveSettings;
import info.unterrainer.presserl.newspaper.NewspaperSettings;
import info.unterrainer.presserl.newspaper.Visibility;
import info.unterrainer.presserl.reader.BodyRenderer.Block;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

/**
 * Server-rendered reader pages. Texts follow the preferred language (English when preferred,
 * German otherwise). A private newspaper shows no article content until reader login exists.
 */
@Path("/")
@Produces(MediaType.TEXT_HTML + ";charset=UTF-8")
public class ReaderResource {

    static final Locale GERMAN = Locale.GERMAN;
    static final Locale ENGLISH = Locale.ENGLISH;
    static final int FRONT_PAGE_LIMIT = 30;

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance frontpage(String lang, String name, String subtitle,
                boolean privateNewspaper, ReaderArticle leadStory, List<ReaderArticle> stories);

        static native TemplateInstance article(String lang, String name, String subtitle, ReaderArticle article,
                List<Block> blocks);

        static native TemplateInstance notFound(String lang, String name, String subtitle);
    }

    @Inject
    NewspaperSettings settings;

    @Inject
    ReaderArticles articles;

    @GET
    public Uni<RestResponse<String>> frontpage(@Context HttpHeaders headers) {
        Locale locale = locale(headers);
        return settings.effective().flatMap(s -> {
            boolean privateNewspaper = s.visibility() == Visibility.PRIVATE;
            Uni<List<ReaderArticle>> listed = privateNewspaper
                    ? Uni.createFrom().item(List.of())
                    : articles.frontPage(FRONT_PAGE_LIMIT);
            return listed.flatMap(list -> render(Templates.frontpage(locale.getLanguage(), s.name(), s.subtitle(),
                    privateNewspaper, list.isEmpty() ? null : list.get(0),
                    list.isEmpty() ? List.of() : list.subList(1, list.size())), locale, Status.OK));
        });
    }

    /**
     * The article page; a malformed or unknown id, an unpublished article and a private newspaper
     * all get the same 404 page.
     */
    @GET
    @Path("articles/{id}")
    public Uni<RestResponse<String>> article(@PathParam("id") String id, @Context HttpHeaders headers) {
        Locale locale = locale(headers);
        return settings.effective().flatMap(s -> {
            Uni<Optional<ReaderArticle>> found = s.visibility() == Visibility.PRIVATE || !id.matches("\\d{1,18}")
                    ? Uni.createFrom().item(Optional.empty())
                    : articles.article(Long.parseLong(id));
            return found.flatMap(article -> article
                    .map(a -> render(Templates.article(locale.getLanguage(), s.name(), s.subtitle(), a,
                            BodyRenderer.blocks(a.body())), locale, Status.OK))
                    .orElseGet(() -> notFound(s, locale)));
        });
    }

    private static Uni<RestResponse<String>> notFound(EffectiveSettings s, Locale locale) {
        return render(Templates.notFound(locale.getLanguage(), s.name(), s.subtitle()), locale, Status.NOT_FOUND);
    }

    private static Uni<RestResponse<String>> render(TemplateInstance page, Locale locale, Status status) {
        return page.setLocale(locale).createUni()
                .map(html -> ResponseBuilder.create(status, html).header(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE)
                        .build());
    }

    /**
     * English when the most preferred language is English, German otherwise (also without header).
     */
    static Locale locale(HttpHeaders headers) {
        List<Locale> accepted = headers.getAcceptableLanguages();
        return !accepted.isEmpty() && ENGLISH.getLanguage().equals(accepted.get(0).getLanguage()) ? ENGLISH : GERMAN;
    }
}
