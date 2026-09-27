package info.unterrainer.presserl.newspaper;

import java.util.Map;

/**
 * Response of {@code GET /api/newspaper} and {@code PUT /api/newspaper/settings}; {@code overrides} holds
 * the entries of {@code settings} that come from a valid newspaper override.
 */
public record NewspaperDto(String name, String subtitle, Visibility visibility, Map<String, Object> settings,
        Map<String, Object> overrides) {

    public static NewspaperDto of(EffectiveSettings settings) {
        return new NewspaperDto(settings.name(), settings.subtitle(), settings.visibility(), settings.settingsMap(),
                settings.overridesMap());
    }
}
