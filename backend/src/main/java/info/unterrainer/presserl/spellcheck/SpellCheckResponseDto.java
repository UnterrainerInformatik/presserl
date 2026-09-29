package info.unterrainer.presserl.spellcheck;

import java.util.List;

/**
 * Response of {@code POST /api/spell-check}: the findings in text order.
 */
public record SpellCheckResponseDto(List<SpellMatchDto> matches) {
}
