package info.unterrainer.presserl.media;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import info.unterrainer.presserl.TestSupport;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * {@code PRESSERL_MEDIA_MAX_SIZE=1M}: a 2 MiB upload is too large, and the setting shows as the
 * effective {@code media.max-size}.
 */
@QuarkusTest
@TestProfile(MediaSizeLimitProfile.class)
class MediaSizeLimitTest {

    @Test
    void uploadAboveTheConfiguredLimitIsTooLarge() {
        TestSupport.awaitReady();
        byte[] content = Arrays.copyOf(MediaFixtures.bytes("opaque.png"), 2 * 1024 * 1024);

        given().auth().oauth2(TestSupport.token("publisher", "publisher"))
                .multiPart("file", "big.png", content, "image/png")
                .post("/api/media").then()
                .statusCode(413)
                .body("errors[0].field", equalTo("file"));
    }

    @Test
    void effectiveSettingShowsTheConfiguredLimit() {
        TestSupport.awaitReady();
        given().auth().oauth2(TestSupport.token("publisher", "publisher")).get("/api/newspaper").then()
                .statusCode(200)
                .body("settings.'media.max-size'", equalTo("1M"));
    }
}
