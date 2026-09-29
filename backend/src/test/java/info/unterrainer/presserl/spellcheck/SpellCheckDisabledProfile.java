package info.unterrainer.presserl.spellcheck;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * The deployment switched the spell check off.
 */
public class SpellCheckDisabledProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("presserl.spell-check.enabled", "false");
    }
}
