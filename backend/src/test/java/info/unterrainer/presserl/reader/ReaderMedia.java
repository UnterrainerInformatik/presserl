package info.unterrainer.presserl.reader;

import static io.restassured.RestAssured.given;

import info.unterrainer.presserl.TestSupport;
import info.unterrainer.presserl.media.MediaFixtures;

/**
 * Real media for reader tests, uploaded through {@code /api/media} so renditions exist.
 */
final class ReaderMedia {

    private ReaderMedia() {
    }

    /**
     * Uploads a {@code width} x {@code height} JPEG as the publisher and returns the media id.
     */
    static long upload(int width, int height) {
        return given().auth().oauth2(TestSupport.token("publisher", "publisher"))
                .multiPart("file", "photo.jpg", MediaFixtures.jpeg(width, height), "image/jpeg")
                .post("/api/media").then().statusCode(201).extract().jsonPath().getLong("id");
    }

    /**
     * A rendition as writers download it.
     */
    static byte[] rendition(long mediaId, String kind) {
        return given().auth().oauth2(TestSupport.token("publisher", "publisher"))
                .get("/api/media/%d/renditions/%s".formatted(mediaId, kind)).then().statusCode(200).extract()
                .asByteArray();
    }

    /**
     * The stored image as writers download it.
     */
    static byte[] content(long mediaId) {
        return given().auth().oauth2(TestSupport.token("publisher", "publisher"))
                .get("/api/media/%d/content".formatted(mediaId)).then().statusCode(200).extract().asByteArray();
    }
}
