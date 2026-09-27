package info.unterrainer.presserl.newspaper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.smallrye.config.EnvConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

class NewspaperConfigTest {

    private static NewspaperConfig config(Map<String, String> env) {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new EnvConfigSource(env, 300))
                .withMapping(NewspaperConfig.class)
                .build();
        return config.getConfigMapping(NewspaperConfig.class);
    }

    @Test
    void freshInstallationUsesCodeDefaults() {
        EffectiveSettings settings = EffectiveSettings.resolve(config(Map.of()), "10M", null);

        assertThat(settings).isEqualTo(new EffectiveSettings("My Newspaper", "", Visibility.PUBLIC, true, "General",
                EditorLevel.STANDARD, TextSize.M, "10M", Set.of()));
        assertThat(settings.overridesMap()).isEmpty();
    }

    @Test
    void deploymentVariableOverridesCodeDefault() {
        NewspaperConfig config = config(Map.of(
                "PRESSERL_NEWSPAPER_NAME", "Die Zwergenpost",
                "PRESSERL_NEWSPAPER_VISIBILITY", "private",
                "PRESSERL_RETRACT_AUTHOR_CAN_RETRACT", "false",
                "PRESSERL_EDITOR_LEVEL", "profi",
                "PRESSERL_READER_TEXT_SIZE", "xl"));

        EffectiveSettings settings = EffectiveSettings.resolve(config, "10M", new NewspaperEntity());

        assertThat(settings.name()).isEqualTo("Die Zwergenpost");
        assertThat(settings.visibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(settings.authorCanRetract()).isFalse();
        assertThat(settings.editorLevel()).isEqualTo(EditorLevel.PROFI);
        assertThat(settings.readerTextSize()).isEqualTo(TextSize.XL);
    }

    @Test
    void databaseOverrideWinsOverDeploymentVariable() {
        NewspaperEntity row = new NewspaperEntity();
        row.name = "Zwergenpost Extra";
        row.settings = new HashMap<>(Map.of("visibility", "private", "editor.level", "starter",
                "retract.author-can-retract", false));

        EffectiveSettings settings = EffectiveSettings.resolve(
                config(Map.of("PRESSERL_NEWSPAPER_NAME", "Die Zwergenpost")), "10M", row);

        assertThat(settings.name()).isEqualTo("Zwergenpost Extra");
        assertThat(settings.visibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(settings.editorLevel()).isEqualTo(EditorLevel.STARTER);
        assertThat(settings.authorCanRetract()).isFalse();
    }

    @Test
    void invalidDatabaseOverrideFallsBackToConfiguredValue() {
        NewspaperEntity row = new NewspaperEntity();
        row.settings = new HashMap<>(Map.of("visibility", "secret", "retract.author-can-retract", "maybe"));

        EffectiveSettings settings = EffectiveSettings.resolve(config(Map.of()), "10M", row);

        assertThat(settings.visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(settings.authorCanRetract()).isTrue();
    }

    @Test
    void deploymentOnlyKeysIgnoreDatabaseOverrides() {
        NewspaperEntity row = new NewspaperEntity();
        row.settings = new HashMap<>(Map.of("section.default", "News", "media.max-size", "1G"));

        EffectiveSettings settings = EffectiveSettings.resolve(config(Map.of()), "10M", row);

        assertThat(settings.sectionDefault()).isEqualTo("General");
        assertThat(settings.mediaMaxSize()).isEqualTo("10M");
    }

    @Test
    void validOverridesAreRecordedAndListedWithTheirEffectiveValue() {
        NewspaperEntity row = new NewspaperEntity();
        row.settings = new HashMap<>(Map.of("visibility", "private", "reader.text-size", "XL",
                "retract.author-can-retract", false));

        EffectiveSettings settings = EffectiveSettings.resolve(
                config(Map.of("PRESSERL_READER_TEXT_SIZE", "l")), "10M", row);

        assertThat(settings.overridden()).containsExactlyInAnyOrder("visibility", "reader.text-size",
                "retract.author-can-retract");
        // visibility is not part of settings, so it is no entry of overrides
        assertThat(settings.overridesMap()).containsExactly(
                Map.entry("retract.author-can-retract", false),
                Map.entry("reader.text-size", "xl"));
    }

    @Test
    void invalidAndDeploymentOnlyOverridesAreNotListed() {
        NewspaperEntity row = new NewspaperEntity();
        row.settings = new HashMap<>(Map.of("reader.text-size", "huge", "editor.level", "profi",
                "retract.author-can-retract", "maybe", "media.max-size", "1G"));

        EffectiveSettings settings = EffectiveSettings.resolve(config(Map.of()), "10M", row);

        assertThat(settings.overridesMap()).containsExactly(Map.entry("editor.level", "profi"));
    }

    @Test
    void unknownVisibilityFailsNamingVariableAndAllowedValues() {
        assertThatThrownBy(() -> config(Map.of("PRESSERL_NEWSPAPER_VISIBILITY", "secret")))
                .hasStackTraceContaining("PRESSERL_NEWSPAPER_VISIBILITY")
                .hasStackTraceContaining("Allowed values: public, private");
    }

    @Test
    void unknownEditorLevelAndTextSizeFailNamingVariable() {
        assertThatThrownBy(() -> config(Map.of("PRESSERL_EDITOR_LEVEL", "expert")))
                .hasStackTraceContaining("PRESSERL_EDITOR_LEVEL")
                .hasStackTraceContaining("starter, standard, profi");
        assertThatThrownBy(() -> config(Map.of("PRESSERL_READER_TEXT_SIZE", "xxl")))
                .hasStackTraceContaining("PRESSERL_READER_TEXT_SIZE")
                .hasStackTraceContaining("s, m, l, xl");
    }

    @Test
    void settingsMapUsesSettingNamesInDocumentedOrder() {
        EffectiveSettings settings = EffectiveSettings.resolve(config(Map.of()), "10M", null);

        assertThat(settings.settingsMap()).containsExactly(
                Map.entry("retract.author-can-retract", true),
                Map.entry("section.default", "General"),
                Map.entry("editor.level", "standard"),
                Map.entry("reader.text-size", "m"),
                Map.entry("media.max-size", "10M"));
    }
}
