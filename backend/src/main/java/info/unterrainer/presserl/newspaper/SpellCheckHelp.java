package info.unterrainer.presserl.newspaper;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * How much help the spell check gives writers: marks, messages and replacements, marks and messages,
 * or marks only.
 */
public enum SpellCheckHelp implements SettingValue {
    SUGGESTIONS("suggestions"),
    MESSAGES("messages"),
    MARKS("marks");

    private final String value;

    SpellCheckHelp(String value) {
        this.value = value;
    }

    @JsonValue
    @Override
    public String value() {
        return value;
    }
}
