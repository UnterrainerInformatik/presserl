package info.unterrainer.presserl.reader;

import info.unterrainer.presserl.newspaper.NewspaperSettings;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Server-rendered reader pages.
 */
@Path("/")
public class ReaderResource {

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance frontpage(String name, String subtitle);
    }

    @Inject
    NewspaperSettings settings;

    @GET
    @Produces(MediaType.TEXT_HTML + ";charset=UTF-8")
    public Uni<TemplateInstance> frontpage() {
        return settings.effective().map(s -> Templates.frontpage(s.name(), s.subtitle()));
    }
}
