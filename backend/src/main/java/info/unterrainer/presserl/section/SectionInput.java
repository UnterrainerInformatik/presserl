package info.unterrainer.presserl.section;

/**
 * A validated section body: name trimmed, {@code color} {@code null} when absent on creation.
 */
public record SectionInput(String name, SectionColor color) {
}
