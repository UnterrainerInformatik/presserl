package info.unterrainer.presserl.newspaper;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Resolves the effective newspaper settings from configuration and the newspaper row.
 */
@ApplicationScoped
public class NewspaperSettings {

    @Inject
    NewspaperConfig config;

    @WithSession
    public Uni<EffectiveSettings> effective() {
        return NewspaperEntity.<NewspaperEntity>findById(NewspaperEntity.SINGLETON_ID)
                .map(row -> EffectiveSettings.resolve(config, row));
    }
}
