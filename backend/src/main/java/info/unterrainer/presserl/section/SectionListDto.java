package info.unterrainer.presserl.section;

import java.util.List;
import java.util.Map;

/**
 * Response of {@code GET /api/sections}: whether the requesting user may create, change and
 * reorder sections, and all sections by position.
 */
public record SectionListDto(boolean canManage, List<SectionDto> sections) {

    /**
     * @param counts see {@link SectionDto#of}
     */
    public static SectionListDto of(List<SectionEntity> sections, Newsroom newsroom,
            Map<Long, ArticleCountsDto> counts) {
        return new SectionListDto(newsroom.mayManageSections(),
                sections.stream().map(section -> SectionDto.of(section, newsroom, counts)).toList());
    }
}
