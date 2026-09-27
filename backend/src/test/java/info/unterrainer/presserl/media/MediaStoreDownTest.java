package info.unterrainer.presserl.media;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import info.unterrainer.presserl.TestSupport;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

/**
 * With the object store unreachable, readiness is DOWN naming the store and uploads answer
 * {@code 503} without leaving a media record.
 */
@QuarkusTest
@TestProfile(MediaStoreDownProfile.class)
class MediaStoreDownTest {

    @Inject
    DataSource dataSource;

    @Test
    void readinessIsDownNamingTheObjectStore() {
        given().get("/q/health/ready").then().statusCode(503)
                .body("checks.find { it.name == 'media-store' }.status", equalTo("DOWN"));
    }

    @Test
    void uploadIsUnavailableAndLeavesNoRecord() {
        awaitPublisher();
        long before = mediaRows();

        given().auth().oauth2(TestSupport.token("publisher", "publisher"))
                .multiPart("file", "photo.jpg", MediaFixtures.bytes("photo-gps.jpg"), "image/jpeg")
                .post("/api/media").then()
                .statusCode(503)
                .body("errors[0].field", equalTo(null));

        assertThat(mediaRows()).isEqualTo(before);
    }

    /**
     * Readiness stays DOWN here, so wait for the publisher bootstrap check alone.
     */
    private static void awaitPublisher() {
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (!"UP".equals(given().get("/q/health/ready").jsonPath()
                .getString("checks.find { it.name == 'publisher-bootstrap' }.status"))) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("publisher bootstrap did not complete");
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    private long mediaRows() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT count(*) FROM media")) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
