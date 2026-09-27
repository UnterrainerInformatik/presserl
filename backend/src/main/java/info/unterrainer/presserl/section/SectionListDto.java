package info.unterrainer.presserl.section;

import java.util.List;

/**
 * Response of {@code GET /api/sections}: whether the requesting user may create, change and
 * reorder sections, and all sections by position.
 */
public record SectionListDto(boolean canManage, List<SectionDto> sections) {

    public static SectionListDto of(List<SectionEntity> sections, Newsroom newsroom) {
        return new SectionListDto(newsroom.isAdministrator(),
                sections.stream().map(section -> SectionDto.of(section, newsroom)).toList());
    }
}
