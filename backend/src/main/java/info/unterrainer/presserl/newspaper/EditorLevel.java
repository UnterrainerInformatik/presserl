package info.unterrainer.presserl.newspaper;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Complexity level of the article editor.
 */
public enum EditorLevel implements SettingValue {
    STARTER("starter"),
    STANDARD("standard"),
    PROFI("profi");

    private final String value;

    EditorLevel(String value) {
        this.value = value;
    }

    @JsonValue
    @Override
    public String value() {
        return value;
    }
}
