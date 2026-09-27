package info.unterrainer.presserl.newspaper;

import java.util.HashMap;
import java.util.Map;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
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

    /**
     * Applies validated changes (see {@link WritableSettings#changes}) to the newspaper overrides in one
     * transaction: a value is stored, {@code null} removes the override.
     *
     * @return the effective settings after the change
     */
    @WithTransaction
    public Uni<EffectiveSettings> update(Map<String, String> changes) {
        return NewspaperEntity.<NewspaperEntity>findById(NewspaperEntity.SINGLETON_ID).map(row -> {
            // a new map, so Hibernate sees the JSON column as changed
            Map<String, Object> settings = new HashMap<>(row.settings == null ? Map.of() : row.settings);
            changes.forEach((key, value) -> {
                if (value == null) {
                    settings.remove(key);
                } else {
                    settings.put(key, value);
                }
            });
            row.settings = settings;
            return EffectiveSettings.resolve(config, row);
        });
    }
}
