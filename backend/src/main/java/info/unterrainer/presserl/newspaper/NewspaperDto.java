package info.unterrainer.presserl.newspaper;

import java.util.Map;

/**
 * Response of {@code GET /api/newspaper}.
 */
public record NewspaperDto(String name, String subtitle, Visibility visibility, Map<String, Object> settings) {

    public static NewspaperDto of(EffectiveSettings settings) {
        return new NewspaperDto(settings.name(), settings.subtitle(), settings.visibility(), settings.settingsMap());
    }
}
