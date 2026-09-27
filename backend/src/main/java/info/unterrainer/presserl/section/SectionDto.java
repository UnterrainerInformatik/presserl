package info.unterrainer.presserl.section;

import java.util.List;

/**
 * One section with the section roles the requesting user may assign in it and whether they may
 * write articles in it.
 */
public record SectionDto(long id, String name, String slug, SectionColor color, int position,
        List<SectionRole> assignableRoles, boolean canWrite) {

    public static SectionDto of(SectionEntity section, Newsroom newsroom) {
        return new SectionDto(section.id, section.name, section.slug, section.color, section.position,
                SectionDelegation.assignable(newsroom, section.id), newsroom.mayWriteIn(section.id));
    }
}
