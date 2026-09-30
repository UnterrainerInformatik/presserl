package info.unterrainer.presserl.newspaper;

import java.util.Optional;

import info.unterrainer.presserl.newspaper.SettingValueConverter.CorrectionsConverter;
import info.unterrainer.presserl.newspaper.SettingValueConverter.EditorLevelConverter;
import info.unterrainer.presserl.newspaper.SettingValueConverter.SpellCheckHelpConverter;
import info.unterrainer.presserl.newspaper.SettingValueConverter.TextSizeConverter;
import info.unterrainer.presserl.newspaper.SettingValueConverter.VisibilityConverter;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithConverter;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Newspaper settings, configuration layers 1 (code default) and 2 (deployment environment).
 * {@code PRESSERL_NEWSPAPER_NAME} maps to {@code presserl.newspaper.name} and so on. The
 * deployment-only setting {@code media.max-size} lives in {@code MediaConfig}, the deployment-only
 * spell-check settings ({@code enabled}, {@code url}, {@code language}) in {@code SpellCheckConfig}.
 */
@ConfigMapping(prefix = "presserl")
public interface NewspaperConfig {

    Newspaper newspaper();

    Retract retract();

    Section section();

    Editor editor();

    Reader reader();

    @WithName("spell-check")
    SpellCheck spellCheck();

    Article article();

    interface Newspaper {

        @WithDefault("My Newspaper")
        String name();

        Optional<String> subtitle();

        @WithDefault("public")
        @WithConverter(VisibilityConverter.class)
        Visibility visibility();
    }

    interface Retract {

        @WithName("author-can-retract")
        @WithDefault("true")
        boolean authorCanRetract();
    }

    interface Section {

        @WithName("default")
        @WithDefault("General")
        String defaultName();
    }

    interface Editor {

        @WithDefault("standard")
        @WithConverter(EditorLevelConverter.class)
        EditorLevel level();
    }

    interface Reader {

        @WithName("text-size")
        @WithDefault("m")
        @WithConverter(TextSizeConverter.class)
        TextSize textSize();
    }

    interface SpellCheck {

        @WithDefault("suggestions")
        @WithConverter(SpellCheckHelpConverter.class)
        SpellCheckHelp help();
    }

    interface Article {

        /**
         * Whether higher levels may correct the articles of those below them; {@code true} or
         * {@code false} only.
         */
        @WithDefault("true")
        @WithConverter(CorrectionsConverter.class)
        Boolean corrections();
    }
}
