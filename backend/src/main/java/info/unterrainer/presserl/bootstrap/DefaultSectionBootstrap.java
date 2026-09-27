package info.unterrainer.presserl.bootstrap;

import org.jboss.logging.Logger;

import info.unterrainer.presserl.article.ArticleEntity;
import info.unterrainer.presserl.newspaper.NewspaperConfig;
import info.unterrainer.presserl.section.SectionEntity;
import info.unterrainer.presserl.section.SectionService;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.vertx.VertxContextSupport;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Makes sure every article has a section: at startup (after Flyway) it ensures the default section
 * ({@code section.default}) when no section exists at all or articles without a section exist, and
 * files those articles under it. A failure fails startup; without a database nothing works anyway.
 */
@Singleton
public class DefaultSectionBootstrap {

    private static final Logger LOG = Logger.getLogger(DefaultSectionBootstrap.class);

    @Inject
    NewspaperConfig config;

    @Inject
    SectionService sections;

    void onStart(@Observes StartupEvent event) throws Throwable {
        run();
    }

    /**
     * Runs the bootstrap on a Vert.x context and waits for it.
     *
     * @return the number of articles filed under the default section; {@code -1} when nothing had
     *         to be done
     */
    public int run() throws Throwable {
        return VertxContextSupport.subscribeAndAwait(this::ensure);
    }

    private Uni<Integer> ensure() {
        String name = config.section().defaultName();
        return Panache.withSession(() -> SectionEntity.count()
                .flatMap(sectionCount -> ArticleEntity.count("sectionId is null")
                        .map(unfiled -> sectionCount == 0 || unfiled > 0)))
                .flatMap(needed -> !needed ? Uni.createFrom().item(-1)
                        : sections.ensureSection(name).flatMap(section -> Panache.withTransaction(
                                () -> ArticleEntity.update("sectionId = ?1 where sectionId is null", section.id)))
                                .invoke(filed -> LOG.infof("Default section '%s' ensured; %d article(s) filed under it",
                                        name, filed)));
    }
}
