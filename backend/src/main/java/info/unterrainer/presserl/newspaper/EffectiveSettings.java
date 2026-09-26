package info.unterrainer.presserl.newspaper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.jboss.logging.Logger;

/**
 * The effective newspaper settings after resolving code default, deployment and newspaper layers.
 */
public record EffectiveSettings(
        String name,
        String subtitle,
        Visibility visibility,
        boolean authorCanRetract,
        String sectionDefault,
        EditorLevel editorLevel,
        TextSize readerTextSize,
        String mediaMaxSize) {

    public static final String VISIBILITY = "visibility";
    public static final String RETRACT_AUTHOR_CAN_RETRACT = "retract.author-can-retract";
    public static final String SECTION_DEFAULT = "section.default";
    public static final String EDITOR_LEVEL = "editor.level";
    public static final String READER_TEXT_SIZE = "reader.text-size";
    public static final String MEDIA_MAX_SIZE = "media.max-size";

    private static final Logger LOG = Logger.getLogger(EffectiveSettings.class);

    /**
     * Resolves the settings; a value from the newspaper row wins over the configured one.
     * Keys that are deployment-only ({@code section.default}, {@code media.max-size}) and
     * unreadable overrides are ignored.
     */
    public static EffectiveSettings resolve(NewspaperConfig config, NewspaperEntity row) {
        Map<String, Object> overrides = row == null || row.settings == null ? Map.of() : row.settings;
        return new EffectiveSettings(
                row != null && row.name != null ? row.name : config.newspaper().name(),
                row != null && row.subtitle != null ? row.subtitle : config.newspaper().subtitle().orElse(""),
                override(overrides, VISIBILITY, Visibility.class).orElse(config.newspaper().visibility()),
                booleanOverride(overrides, RETRACT_AUTHOR_CAN_RETRACT).orElse(config.retract().authorCanRetract()),
                config.section().defaultName(),
                override(overrides, EDITOR_LEVEL, EditorLevel.class).orElse(config.editor().level()),
                override(overrides, READER_TEXT_SIZE, TextSize.class).orElse(config.reader().textSize()),
                config.media().maxSize());
    }

    /**
     * The settings other than name, subtitle and visibility, keyed by setting name.
     */
    public Map<String, Object> settingsMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(RETRACT_AUTHOR_CAN_RETRACT, authorCanRetract);
        map.put(SECTION_DEFAULT, sectionDefault);
        map.put(EDITOR_LEVEL, editorLevel.value());
        map.put(READER_TEXT_SIZE, readerTextSize.value());
        map.put(MEDIA_MAX_SIZE, mediaMaxSize);
        return map;
    }

    private static <E extends Enum<E> & SettingValue> Optional<E> override(
            Map<String, Object> overrides, String key, Class<E> type) {
        Object value = overrides.get(key);
        if (value == null) {
            return Optional.empty();
        }
        Optional<E> parsed = SettingValueConverter.parse(type, value.toString());
        if (parsed.isEmpty()) {
            LOG.warnf("Ignoring newspaper override %s=%s; allowed values: %s", key, value,
                    SettingValueConverter.allowedValues(type));
        }
        return parsed;
    }

    private static Optional<Boolean> booleanOverride(Map<String, Object> overrides, String key) {
        Object value = overrides.get(key);
        if (value instanceof Boolean b) {
            return Optional.of(b);
        }
        if (value != null) {
            LOG.warnf("Ignoring newspaper override %s=%s; expected true or false", key, value);
        }
        return Optional.empty();
    }
}
