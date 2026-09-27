package info.unterrainer.presserl.section;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonValue;

import jakarta.persistence.AttributeConverter;

/**
 * The section palette in palette order. Colours are keys ({@code red}, …), not colour values; the
 * reader theme maps them to {@code --presserl-section-<key>} tokens.
 */
public enum SectionColor {
    RED,
    ORANGE,
    YELLOW,
    GREEN,
    TEAL,
    BLUE,
    PURPLE,
    PINK;

    /**
     * The lower-case key used on the wire and in the database.
     */
    @JsonValue
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<SectionColor> ofKey(String key) {
        return Arrays.stream(values()).filter(color -> color.key().equals(key)).findFirst();
    }

    /**
     * The colour a new section gets when none is given: palette order, repeating.
     */
    public static SectionColor defaultFor(long existingSections) {
        return values()[(int) (existingSections % values().length)];
    }

    public static class Converter implements AttributeConverter<SectionColor, String> {

        @Override
        public String convertToDatabaseColumn(SectionColor color) {
            return color == null ? null : color.key();
        }

        @Override
        public SectionColor convertToEntityAttribute(String key) {
            return key == null ? null : ofKey(key).orElseThrow(() -> new IllegalStateException(
                    "unknown section colour '" + key + "'"));
        }
    }
}
