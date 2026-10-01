package info.unterrainer.presserl.reader;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;

/**
 * The account deletion page of a private newspaper is public: anonymous visitors get it without login
 * redirect.
 */
@QuarkusTest
@TestProfile(ReaderLegalNoticePrivateTest.PrivateTheme.class)
class ReaderAccountDeletionPrivateTest {

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
    void anonymousVisitorGetsThePage() {
        Response response = given().redirects().follow(false).get("/account-deletion");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.header("Cache-Control")).isNull();
        assertThat(response.asString()).contains("<main data-view=\"account-deletion\">",
                "<a href=\"/legal-notice\">Impressum</a>").doesNotContain("section-bar");
    }
}
