package info.unterrainer.presserl.reader;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;

/**
 * The legal notice of a private newspaper is public: anonymous visitors get it without login redirect.
 */
@QuarkusTest
@TestProfile(ReaderLegalNoticePrivateTest.PrivateTheme.class)
class ReaderLegalNoticePrivateTest {

    public static class PrivateTheme extends ThemeProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> overrides = new HashMap<>(super.getConfigOverrides());
            overrides.put("presserl.newspaper.visibility", "private");
            return overrides;
        }
    }

    @BeforeEach
    void theme() {
        ThemeProfile.clear();
        ThemeProfile.write("legal-notice.txt", ReaderLegalNoticeTest.NOTICE);
    }

    @AfterEach
    void cleanUp() {
        ThemeProfile.clear();
    }

    @Test
    void anonymousVisitorGetsTheNotice() {
        Response response = given().redirects().follow(false).get("/legal-notice");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.header("Cache-Control")).isNull();
        assertThat(response.asString()).contains("<main data-view=\"legal-notice\">",
                "<p>Offenlegung gemäß § 25 Mediengesetz</p>").doesNotContain("section-bar");
    }

    @Test
    void privateFrontPageLinksTheNotice() {
        assertThat(given().get("/").asString()).contains("Diese Zeitung ist privat.",
                "<a href=\"/legal-notice\">Impressum</a>");
    }
}
