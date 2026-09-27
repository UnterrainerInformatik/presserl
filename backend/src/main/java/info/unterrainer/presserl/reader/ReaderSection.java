package info.unterrainer.presserl.reader;

import info.unterrainer.presserl.section.SectionEntity;
import io.quarkus.qute.TemplateData;

/**
 * A section as the reader shows it.
 *
 * @param color the palette key ({@code red}, …), mapped to {@code --presserl-section-<key>} by the theme
 */
@TemplateData
public record ReaderSection(String name, String color) {

    static ReaderSection of(SectionEntity section) {
        return new ReaderSection(section.name, section.color.key());
    }
}
