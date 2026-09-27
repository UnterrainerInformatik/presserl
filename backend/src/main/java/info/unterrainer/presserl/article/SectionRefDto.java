package info.unterrainer.presserl.article;

import info.unterrainer.presserl.section.SectionColor;
import info.unterrainer.presserl.section.SectionEntity;

/**
 * The section an article belongs to, as embedded in article representations.
 */
public record SectionRefDto(long id, String name, String slug, SectionColor color) {

    /**
     * {@code null} for an article without a section.
     */
    public static SectionRefDto of(SectionEntity section) {
        return section == null ? null : new SectionRefDto(section.id, section.name, section.slug, section.color);
    }
}
