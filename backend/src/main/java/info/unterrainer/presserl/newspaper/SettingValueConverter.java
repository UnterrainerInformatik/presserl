package info.unterrainer.presserl.newspaper;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.microprofile.config.spi.Converter;

/**
 * Converts deployment values into enumerated settings. The error names the environment
 * variable the operator sets and the allowed values, so a typo in {@code .env} is obvious.
 */
public final class SettingValueConverter {

    private SettingValueConverter() {
    }

    static <E extends Enum<E> & SettingValue> E convert(Class<E> type, String property, String raw) {
        String value = raw == null ? "" : raw.trim();
        return parse(type, value).orElseThrow(() -> new IllegalArgumentException(
                "Invalid value '%s' for %s (%s). Allowed values: %s".formatted(
                        value, envName(property), property, allowedValues(type))));
    }

    /**
     * Parses a value by its external spelling, ignoring case.
     */
    public static <E extends Enum<E> & SettingValue> Optional<E> parse(Class<E> type, String value) {
        return Arrays.stream(type.getEnumConstants())
                .filter(constant -> constant.value().equalsIgnoreCase(value))
                .findFirst();
    }

    static String envName(String property) {
        return property.replaceAll("[^A-Za-z0-9]", "_").toUpperCase();
    }

    static String allowedValues(Class<? extends SettingValue> type) {
        return Arrays.stream(type.getEnumConstants())
                .map(SettingValue::value)
                .collect(Collectors.joining(", "));
    }

    /**
     * A strict boolean: {@code true} or {@code false} (ignoring case), unlike the lenient default
     * converter, which reads every unknown value as {@code false}.
     */
    static boolean convertBoolean(String property, String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(value);
        }
        throw new IllegalArgumentException("Invalid value '%s' for %s (%s). Allowed values: true, false".formatted(
                value, envName(property), property));
    }

    public static class VisibilityConverter implements Converter<Visibility> {
        @Override
        public Visibility convert(String raw) {
            return SettingValueConverter.convert(Visibility.class, "presserl.newspaper.visibility", raw);
        }
    }

    public static class EditorLevelConverter implements Converter<EditorLevel> {
        @Override
        public EditorLevel convert(String raw) {
            return SettingValueConverter.convert(EditorLevel.class, "presserl.editor.level", raw);
        }
    }

    public static class TextSizeConverter implements Converter<TextSize> {
        @Override
        public TextSize convert(String raw) {
            return SettingValueConverter.convert(TextSize.class, "presserl.reader.text-size", raw);
        }
    }

    public static class SpellCheckHelpConverter implements Converter<SpellCheckHelp> {
        @Override
        public SpellCheckHelp convert(String raw) {
            return SettingValueConverter.convert(SpellCheckHelp.class, "presserl.spell-check.help", raw);
        }
    }

    public static class CorrectionsConverter implements Converter<Boolean> {
        @Override
        public Boolean convert(String raw) {
            return SettingValueConverter.convertBoolean("presserl.article.corrections", raw);
        }
    }
}
