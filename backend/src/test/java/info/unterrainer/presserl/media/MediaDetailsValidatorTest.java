package info.unterrainer.presserl.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import info.unterrainer.presserl.media.MediaDetailsValidator.Details;
import jakarta.ws.rs.core.Response.Status;

class MediaDetailsValidatorTest {

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

    private static void assertBody(String body, String... fields) {
        assertFields(() -> MediaDetailsValidator.parse(json(body)), fields);
    }

    private static Details tags(String... tags) {
        return MediaDetailsValidator.of(List.of(), List.of(tags));
    }

    @Test
    void descriptionAndTagsAreRead() {
        Details details = MediaDetailsValidator.parse(json("""
                {"description": "  Einsatz am Dorfplatz, Foto: Anna ", "tags": ["Feuerwehr", "Einsatz"]}"""));

        assertThat(details.description()).isEqualTo("Einsatz am Dorfplatz, Foto: Anna");
        assertThat(details.tags()).containsExactly("Feuerwehr", "Einsatz");
    }

    @Test
    void tagsAreNormalised() {
        assertThat(tags("feuerwehr ", "Hochwasser  2026", "\tFreiwillige  \n Feuerwehr").tags())
                .containsExactly("feuerwehr", "Hochwasser 2026", "Freiwillige Feuerwehr");
    }

    @Test
    void duplicatesDifferingInCaseCollapseToTheFirstSpelling() {
        assertThat(tags("Schule", "schule", "SCHULE ", "Sport").tags()).containsExactly("Schule", "Sport");
    }

    @Test
    void blankDescriptionIsNull() {
        assertThat(MediaDetailsValidator.parse(json("{\"description\": \"   \", \"tags\": []}")).description())
                .isNull();
        assertThat(MediaDetailsValidator.parse(json("{\"description\": null, \"tags\": []}")).description())
                .isNull();
        assertThat(MediaDetailsValidator.of(List.of(""), List.of()).description()).isNull();
    }

    @Test
    void descriptionIsLimitedTo1000CodePoints() {
        String emoji = "📷";
        assertThat(MediaDetailsValidator.of(List.of(" " + emoji.repeat(1000) + " "), List.of()).description())
                .isEqualTo(emoji.repeat(1000));
        assertFields(() -> MediaDetailsValidator.of(List.of("x".repeat(1001)), List.of()), "description");
    }

    @Test
    void tagIsLimitedTo40CodePoints() {
        assertThat(tags("ä".repeat(40)).tags()).containsExactly("ä".repeat(40));
        assertFields(() -> tags("ok", "x".repeat(41)), "tags[1]");
    }

    @Test
    void tagWithCommaIsInvalid() {
        assertFields(() -> tags("Feuer, Wasser"), "tags[0]");
    }

    @Test
    void tagWithControlCharacterIsInvalid() {
        assertFields(() -> tags("Feuer\u0000wehr"), "tags[0]");
        assertFields(() -> tags("Feuer\u007fwehr"), "tags[0]");
    }

    @Test
    void emptyTagIsInvalid() {
        assertFields(() -> tags("  "), "tags[0]");
        assertFields(() -> tags(""), "tags[0]");
    }

    @Test
    void atMost20DistinctTags() {
        List<String> twenty = IntStream.range(0, 20).mapToObj(i -> "Tag " + i).toList();
        List<String> withDuplicates = new ArrayList<>(twenty);
        withDuplicates.add("TAG 0");
        assertThat(MediaDetailsValidator.of(List.of(), withDuplicates).tags()).hasSize(20);

        List<String> twentyOne = new ArrayList<>(twenty);
        twentyOne.add("Tag 20");
        assertFields(() -> MediaDetailsValidator.of(List.of(), twentyOne), "tags");
    }

    @Test
    void uploadAcceptsAtMostOneDescription() {
        assertFields(() -> MediaDetailsValidator.of(List.of("a", "b"), List.of()), "description");
    }

    @Test
    void uploadWithoutPartsHasNoDetails() {
        Details details = MediaDetailsValidator.of(null, null);

        assertThat(details.description()).isNull();
        assertThat(details.tags()).isEmpty();
    }

    @Test
    void bothFieldsAreRequired() {
        assertBody("{}", "description", "tags");
        assertBody("{\"description\": null}", "tags");
        assertBody("{\"tags\": []}", "description");
        assertBody("{\"description\": null, \"tags\": null}", "tags");
    }

    @Test
    void wrongTypesAreInvalid() {
        assertBody("{\"description\": 5, \"tags\": \"Feuerwehr\"}", "description", "tags");
        assertBody("{\"description\": null, \"tags\": [\"ok\", 3, null]}", "tags[1]", "tags[2]");
        assertBody("[]", (String) null);
    }

    @Test
    void unknownFieldsAreInvalid() {
        assertBody("{\"description\": null, \"tags\": [], \"version\": 0}", "version");
    }

    @Test
    void everyViolationIsReported() {
        assertBody("{\"description\": \"%s\", \"tags\": [\"a,b\", \"ok\", \"\"]}".formatted("x".repeat(1001)),
                "description", "tags[0]", "tags[2]");
    }

    @Test
    void keyIsLowerCase() {
        assertThat(MediaDetailsValidator.key("Freiwillige Feuerwehr")).isEqualTo("freiwillige feuerwehr");
    }

    @Test
    void tagOrderIsCaseInsensitive() {
        assertThat(List.of("feuerwehr", "Einsatz", "Anna").stream().sorted(MediaDetailsValidator.TAG_ORDER))
                .containsExactly("Anna", "Einsatz", "feuerwehr");
    }
}
