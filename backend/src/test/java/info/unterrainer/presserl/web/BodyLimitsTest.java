package info.unterrainer.presserl.web;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.Arrays;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import info.unterrainer.presserl.TestSupport;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

/**
 * The HTTP body ceiling is 64M for media uploads; everything else keeps 10M.
 */
@QuarkusTest
class BodyLimitsTest {

    private static byte[] json(int size) {
        byte[] body = new byte[size];
        Arrays.fill(body, (byte) ' ');
        body[0] = '{';
        body[size - 1] = '}';
        return body;
    }

    @Inject
    DataSource dataSource;

    @AfterEach
    void cleanUp() {
        TestSupport.deleteSections(dataSource);
    }

    @Test
    void nonMediaEndpointRefusesBodiesAboveTenMegabytes() {
        TestSupport.awaitReady();
        given().auth().oauth2(TestSupport.token("publisher", "publisher"))
                .contentType(ContentType.JSON)
                .body(json(10 * 1024 * 1024 + 1))
                .post("/api/articles").then()
                .statusCode(413)
                .body("errors[0].message", equalTo("request body larger than 10M"));
    }

    @Test
    void nonMediaEndpointAcceptsBodiesUpToTenMegabytes() {
        TestSupport.awaitReady();
        // an empty article: whitespace is valid JSON padding
        given().auth().oauth2(TestSupport.token("publisher", "publisher"))
                .contentType(ContentType.JSON)
                .body(json(10 * 1024 * 1024))
                .post("/api/articles").then()
                .statusCode(201);
    }

    @Test
    void contentLengthParsing() {
        assertThat(BodyLimits.exceeds(null, 10)).isFalse();
        assertThat(BodyLimits.exceeds("10", 10)).isFalse();
        assertThat(BodyLimits.exceeds("11", 10)).isTrue();
        assertThat(BodyLimits.exceeds("junk", 10)).isFalse();
    }
}
