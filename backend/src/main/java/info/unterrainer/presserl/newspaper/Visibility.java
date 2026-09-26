package info.unterrainer.presserl.newspaper;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Who may read the newspaper.
 */
public enum Visibility implements SettingValue {
    PUBLIC("public"),
    PRIVATE("private");

    private final String value;

    Visibility(String value) {
        this.value = value;
    }

    @JsonValue
    @Override
    public String value() {
        return value;
    }
}
