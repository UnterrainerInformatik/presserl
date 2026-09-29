package info.unterrainer.presserl.spellcheck;

import java.util.List;

/**
 * One finding; {@code offset} and {@code length} are UTF-16 code units of the checked text.
 */
public record SpellMatchDto(int offset, int length, String message, List<String> replacements) {
}
