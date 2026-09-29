package info.unterrainer.presserl.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import info.unterrainer.presserl.media.MediaEditValidator.EditRequest;
import jakarta.ws.rs.core.Response.Status;

class MediaEditValidatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode json(String text) {
        try {
            return MAPPER.readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static void assertFields(Runnable call, String... fields) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(MediaException.class, e -> {
            assertThat(e.status()).isEqualTo(Status.BAD_REQUEST);
            assertThat(e.errors()).extracting(error -> error.field()).containsExactly(fields);
        });
    }

    private static void assertBounds(String body, int width, int height, String... fields) {
        EditRequest request = MediaEditValidator.parse(json(body));
        assertFields(() -> MediaEditValidator.checkBounds(request, width, height), fields);
    }

    @Test
    void cropAndEllipsesAreRead() {
        EditRequest request = MediaEditValidator.parse(json("""
                {"version": 3, "crop": {"x": 100, "y": 50, "width": 1200, "height": 800},
                 "pixelate": [{"cx": 600, "cy": 400, "rx": 80, "ry": 110}]}"""));

        assertThat(request.version()).isEqualTo(3);
        assertThat(request.crop()).isEqualTo(new MediaProcessor.Crop(100, 50, 1200, 800));
        assertThat(request.ellipses()).containsExactly(new MediaProcessor.Ellipse(600, 400, 80, 110));
        MediaEditValidator.checkBounds(request, 1600, 1067);
    }

    @Test
    void versionIsRequired() {
        assertFields(() -> MediaEditValidator.parse(json("{\"crop\": {\"x\": 0, \"y\": 0, \"width\": 20, "
                + "\"height\": 20}}")), "version");
        assertFields(() -> MediaEditValidator.parse(json("{\"version\": \"1\", \"crop\": {\"x\": 0, \"y\": 0, "
                + "\"width\": 20, \"height\": 20}}")), "version");
    }

    @Test
    void somethingToDoIsRequired() {
        assertFields(() -> MediaEditValidator.parse(json("{\"version\": 0, \"pixelate\": []}")), "crop");
        assertFields(() -> MediaEditValidator.parse(json("{\"version\": 0}")), "crop");
    }

    @Test
    void malformedCropAndEllipses() {
        assertFields(() -> MediaEditValidator.parse(json("{\"version\": 0, \"crop\": {\"x\": 0, \"y\": 0}}")),
                "crop");
        assertFields(() -> MediaEditValidator.parse(json("{\"version\": 0, \"pixelate\": [{\"cx\": 1, \"cy\": 1, "
                + "\"rx\": 5, \"ry\": 5}, {\"cx\": 1.5, \"cy\": 1, \"rx\": 5, \"ry\": 5}]}")), "pixelate[1]");
        assertFields(() -> MediaEditValidator.parse(json("{\"version\": 0, \"pixelate\": {}}")), "pixelate");
        assertFields(() -> MediaEditValidator.parse(json("{\"version\": 0, \"blur\": 3, \"crop\": {\"x\": 0, "
                + "\"y\": 0, \"width\": 20, \"height\": 20}}")), "blur");
    }

    @Test
    void atMostFiftyEllipses() {
        String ellipse = "{\"cx\": 10, \"cy\": 10, \"rx\": 5, \"ry\": 5}";
        String fifty = String.join(",", java.util.Collections.nCopies(50, ellipse));

        MediaEditValidator.parse(json("{\"version\": 0, \"pixelate\": [" + fifty + "]}"));
        assertFields(() -> MediaEditValidator.parse(json("{\"version\": 0, \"pixelate\": [" + fifty + ","
                + ellipse + "]}")), "pixelate");
    }

    @Test
    void cropMustLieInsideAndBeLargeEnough() {
        assertBounds("{\"version\": 0, \"crop\": {\"x\": 1000, \"y\": 0, \"width\": 800, \"height\": 500}}", 1600,
                1067, "crop");
        assertBounds("{\"version\": 0, \"crop\": {\"x\": -1, \"y\": 0, \"width\": 800, \"height\": 500}}", 1600,
                1067, "crop");
        assertBounds("{\"version\": 0, \"crop\": {\"x\": 0, \"y\": 0, \"width\": 15, \"height\": 500}}", 1600,
                1067, "crop");
        MediaEditValidator.checkBounds(MediaEditValidator.parse(json(
                "{\"version\": 0, \"crop\": {\"x\": 0, \"y\": 0, \"width\": 1600, \"height\": 1067}}")), 1600, 1067);
    }

    @Test
    void ellipsesNeedRadiusAndCentreInside() {
        assertBounds("{\"version\": 0, \"pixelate\": [{\"cx\": 10, \"cy\": 10, \"rx\": 3, \"ry\": 10}, "
                + "{\"cx\": 1600, \"cy\": 10, \"rx\": 10, \"ry\": 10}, {\"cx\": 5, \"cy\": 5, \"rx\": 40, \"ry\": 40}]}",
                1600, 1067, "pixelate[0]", "pixelate[1]");
    }
}
