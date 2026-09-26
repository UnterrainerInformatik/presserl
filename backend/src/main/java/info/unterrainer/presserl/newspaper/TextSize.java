package info.unterrainer.presserl.newspaper;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Default text size of the reader.
 */
public enum TextSize implements SettingValue {
    S("s"),
    M("m"),
    L("l"),
    XL("xl");

    private final String value;

    TextSize(String value) {
        this.value = value;
    }

    @JsonValue
    @Override
    public String value() {
        return value;
    }
}
