package info.unterrainer.presserl.section;

import java.util.List;
import java.util.Map;

/**
 * One section with the section roles the requesting user may assign in it, whether they may write
 * articles in it and, for writers, how many articles it holds ({@code null} for everyone else).
 */
public record SectionDto(long id, String name, String slug, SectionColor color, int position,
        List<SectionRole> assignableRoles, boolean canWrite, ArticleCountsDto articleCounts) {

    /**
     * @param counts the counts of all sections by section id (sections without articles missing), from
     *               {@link SectionService#articleCounts}; ignored for non-writers
     */
    public static SectionDto of(SectionEntity section, Newsroom newsroom, Map<Long, ArticleCountsDto> counts) {
        return new SectionDto(section.id, section.name, section.slug, section.color, section.position,
                SectionDelegation.assignable(newsroom, section.id), newsroom.mayWriteIn(section.id),
                newsroom.isWriter() ? counts.getOrDefault(section.id, ArticleCountsDto.EMPTY) : null);
    }
}
