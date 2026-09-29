package info.unterrainer.presserl.spellcheck;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Spell-check settings from the deployment ({@code PRESSERL_SPELL_CHECK_*}): whether the check is
 * offered, where LanguageTool answers and which LanguageTool language code every check uses.
 */
@ConfigMapping(prefix = "presserl.spell-check")
public interface SpellCheckConfig {

    @WithDefault("true")
    boolean enabled();

    /**
     * Base URL of the LanguageTool server; {@code /v2/check} is appended.
     */
    @WithDefault("http://languagetool:8010")
    String url();

    /**
     * A LanguageTool language code such as {@code de-DE} or {@code de-AT}.
     */
    @WithDefault("de-DE")
    String language();
}
