package info.unterrainer.presserl.bootstrap;

import org.jboss.logging.Logger;

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
 * Creates the default section ({@code section.default}) at startup (after Flyway) when no section
 * exists at all. Articles always have a section (the database enforces it). A failure fails
 * startup; without a database nothing works anyway.
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
     * @return {@code true} when the default section was created (or found by name after a race),
     *         {@code false} when sections existed and nothing was done
     */
    public boolean run() throws Throwable {
        return VertxContextSupport.subscribeAndAwait(this::ensure);
    }

    private Uni<Boolean> ensure() {
        String name = config.section().defaultName();
        return Panache.withSession(() -> SectionEntity.count())
                .flatMap(sectionCount -> sectionCount > 0 ? Uni.createFrom().item(false)
                        : sections.ensureSection(name)
                                .invoke(section -> LOG.infof("Default section '%s' created", section.name))
                                .replaceWith(true));
    }
}
