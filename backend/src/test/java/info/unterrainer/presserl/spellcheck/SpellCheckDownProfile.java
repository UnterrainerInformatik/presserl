package info.unterrainer.presserl.spellcheck;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Points the LanguageTool client at a port where nothing answers.
 */
public class SpellCheckDownProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("quarkus.rest-client.languagetool.url", "http://127.0.0.1:1");
    }
}
