package info.unterrainer.presserl.newspaper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;

import info.unterrainer.presserl.api.FieldError;

/**
 * The newspaper settings that {@code PUT /api/newspaper/settings} may override, each with the parser of
 * its allowed values. Further keys are added here one by one.
 */
public final class WritableSettings {

    private static final Map<String, Setting> WRITABLE = Map.of(
            EffectiveSettings.READER_TEXT_SIZE, enumerated(TextSize.class));

    private record Setting(Function<String, Optional<String>> parser, String allowed) {
    }

    private WritableSettings() {
    }

    /**
     * Reads the request body completely before anything is written: every key must be writable and
     * every value an allowed value or {@code null}.
     *
     * @return the changes, keyed by setting name; a {@code null} value removes the override
     * @throws NewspaperSettingsException with every violation
     */
    public static Map<String, String> changes(JsonNode json) {
        if (json == null || !json.isObject()) {
            throw new NewspaperSettingsException(List.of(new FieldError(null, "request body must be a JSON object")));
        }
        Map<String, String> changes = new LinkedHashMap<>();
        List<FieldError> errors = new ArrayList<>();
        for (Iterator<Map.Entry<String, JsonNode>> fields = json.fields(); fields.hasNext();) {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = field.getKey();
            JsonNode value = field.getValue();
            Setting setting = WRITABLE.get(key);
            if (setting == null) {
                errors.add(new FieldError(key, "is not a writable setting"));
            } else if (value.isNull()) {
                changes.put(key, null);
            } else {
                Optional<String> parsed = value.isTextual() ? setting.parser().apply(value.asText()) : Optional.empty();
                parsed.ifPresentOrElse(v -> changes.put(key, v),
                        () -> errors.add(new FieldError(key, "must be one of " + setting.allowed())));
            }
        }
        if (!errors.isEmpty()) {
            throw new NewspaperSettingsException(errors);
        }
        return changes;
    }

    private static <E extends Enum<E> & SettingValue> Setting enumerated(Class<E> type) {
        return new Setting(value -> SettingValueConverter.parse(type, value).map(SettingValue::value),
                SettingValueConverter.allowedValues(type));
    }
}
