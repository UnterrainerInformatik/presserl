package info.unterrainer.presserl.media;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import info.unterrainer.presserl.TestSupport;
import info.unterrainer.presserl.bootstrap.KeycloakAdminProducer.KeycloakRealm;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Description and tags of media: the upload parts, {@code PUT /api/media/{id}/details},
 * {@code GET /api/media/tags} and the filters of {@code GET /api/media}. {@code nogroups} ("anna") is a
 * reporter in "Pets", {@code reader} only a reader; media rows, objects and sections are removed before
 * and after every test.
 */
@QuarkusTest
class MediaDetailsResourceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    DataSource dataSource;

    @Inject
    S3Client s3;

    @Inject
    MediaStore store;

    @Inject
    Keycloak keycloak;

    @Inject
    KeycloakRealm keycloakRealm;

    private String publisher;
    private String anna;
    private String readerOnly;
    private long pets;

    @BeforeEach
    void ready() {
        TestSupport.awaitReady();
        cleanUp();
        publisher = TestSupport.token("publisher", "publisher");
        readerOnly = TestSupport.token("reader", "reader");
        pets = as(publisher).contentType(ContentType.JSON).body("{\"name\": \"Pets\"}").post("/api/sections").then()
                .statusCode(201).extract().jsonPath().getLong("id");
        String id = keycloak.realm(keycloakRealm.name()).users().searchByUsername("nogroups", true).getFirst().getId();
        as(publisher).contentType(ContentType.JSON).body("{\"role\": \"REPORTER\"}")
                .put("/api/sections/%d/members/%s".formatted(pets, id)).then().statusCode(200);
        anna = TestSupport.token("nogroups", "nogroups");
    }

    @AfterEach
    void cleanUp() {
        TestSupport.deleteSections(dataSource);
        sql("DELETE FROM media");
        objects().forEach(key -> s3.deleteObject(b -> b.bucket(store.bucket()).key(key)));
    }

    // --- helpers

    private static RequestSpecification as(String token) {
        return given().auth().oauth2(token);
    }

    private static long upload(String token) {
        return as(token).multiPart("file", "photo.jpg", MediaFixtures.jpeg(64, 48), "image/jpeg").post("/api/media")
                .then().statusCode(201).extract().jsonPath().getLong("id");
    }

    private static ValidatableResponse details(String token, long id, String body) {
        return as(token).contentType(ContentType.JSON).body(body).put("/api/media/%d/details".formatted(id)).then();
    }

    private static ValidatableResponse tags(String token, long id, String... tags) {
        ObjectNode body = MAPPER.createObjectNode().putNull("description");
        for (String tag : tags) {
            body.withArray("tags").add(tag);
        }
        if (tags.length == 0) {
            body.putArray("tags");
        }
        return details(token, id, body.toString());
    }

    private static void describe(String token, long id, String description) {
        ObjectNode body = MAPPER.createObjectNode().put("description", description);
        body.putArray("tags");
        details(token, id, body.toString()).statusCode(200);
    }

    private long article(String token, long mediaId) {
        ObjectNode content = MAPPER.createObjectNode().put("headline", "Using it").put("sectionId", pets);
        content.putObject("leadImage").put("mediaId", mediaId);
        return as(token).contentType(ContentType.JSON).body(content.toString()).post("/api/articles").then()
                .statusCode(201).extract().jsonPath().getLong("id");
    }

    /**
     * {@code count} media rows (no objects) uploaded by {@code sub}, oldest first.
     */
    private List<Long> rows(int count, String sub) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(Long.parseLong(strings(("INSERT INTO media (object_key, content_type, width, height, byte_size, "
                    + "uploader_sub, uploader_username, uploader_display_name, created_at) VALUES ('media/row-%d-%d.jpg', "
                    + "'image/jpeg', 10, 10, 100, '%s', 'x', 'X', now()) RETURNING id")
                    .formatted(System.nanoTime(), i, sub)).getFirst()));
        }
        return ids;
    }

    private List<String> objects() {
        return s3.listObjectsV2(b -> b.bucket(store.bucket()).prefix("media/")).contents().stream()
                .map(S3Object::key).toList();
    }

    private List<String> strings(String query) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(query)) {
            List<String> values = new ArrayList<>();
            while (rs.next()) {
                values.add(rs.getString(1));
            }
            return values;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void sql(String statementText) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(statementText);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Integer[] ints(List<Long> ids) {
        return ids.stream().map(Long::intValue).toArray(Integer[]::new);
    }

    // --- upload

    @Test
    void uploadWithTagsAndDescription() {
        long id = as(anna).multiPart("file", "photo.jpg", MediaFixtures.jpeg(64, 48), "image/jpeg")
                .multiPart("description", "Einsatz am Dorfplatz, Foto: Anna")
                .multiPart("tag", "Feuerwehr")
                .multiPart("tag", "Einsatz")
                .post("/api/media").then().statusCode(201)
                .body("description", equalTo("Einsatz am Dorfplatz, Foto: Anna"))
                .body("tags", contains("Einsatz", "Feuerwehr"))
                .extract().jsonPath().getLong("id");

        as(anna).get("/api/media/" + id).then().statusCode(200)
                .body("description", equalTo("Einsatz am Dorfplatz, Foto: Anna"))
                .body("tags", contains("Einsatz", "Feuerwehr"));
    }

    @Test
    void invalidTagOnUploadStoresNothing() {
        as(anna).multiPart("file", "photo.jpg", MediaFixtures.jpeg(64, 48), "image/jpeg")
                .multiPart("tag", "x".repeat(41))
                .post("/api/media").then().statusCode(400).body("errors.field", contains("tags[0]"));

        assertThat(strings("SELECT count(*) FROM media")).containsExactly("0");
        assertThat(objects()).isEmpty();
    }

    @Test
    void secondDescriptionPartIsInvalid() {
        as(anna).multiPart("file", "photo.jpg", MediaFixtures.jpeg(64, 48), "image/jpeg")
                .multiPart("description", "one").multiPart("description", "two")
                .post("/api/media").then().statusCode(400).body("errors.field", contains("description"));
    }

    @Test
    void mediaWithoutDetailsHasNullDescriptionAndNoTags() {
        long id = upload(anna);

        as(anna).get("/api/media/" + id).then().statusCode(200)
                .body("description", nullValue())
                .body("tags", empty());
        as(anna).get("/api/media").then().statusCode(200)
                .body("items[0].description", nullValue())
                .body("items[0].tags", empty());
    }

    // --- details

    @Test
    void tagSpellingOfTheNewspaperIsKept() {
        long first = upload(publisher);
        long second = upload(anna);
        tags(publisher, first, "Feuerwehr").statusCode(200);

        tags(anna, second, "feuerwehr ", "Hochwasser  2026").statusCode(200)
                .body("tags", contains("Feuerwehr", "Hochwasser 2026"));
        as(anna).get("/api/media/" + second).then().body("tags", contains("Feuerwehr", "Hochwasser 2026"));
    }

    @Test
    void ownSpellingCanBeChangedWhileNoOtherMediaCarriesTheTag() {
        long id = upload(anna);
        tags(anna, id, "feuerwehr").statusCode(200).body("tags", contains("feuerwehr"));

        tags(anna, id, "Feuerwehr").statusCode(200).body("tags", contains("Feuerwehr"));
    }

    @Test
    void duplicatesInOneRequest() {
        long id = upload(anna);

        tags(anna, id, "Schule", "schule", "SCHULE").statusCode(200).body("tags", contains("Schule"));
        assertThat(strings("SELECT name FROM media_tag WHERE media_id = " + id)).containsExactly("Schule");
    }

    @Test
    void reporterTagsSomeoneElsesLiveImage() {
        long media = upload(publisher);
        long article = article(publisher, media);
        sql("UPDATE article SET status = 'PUBLISHED', live_revision = 1, published_at = now() WHERE id = " + article);

        ObjectNode body = MAPPER.createObjectNode().put("description", "  Foto: Papa ");
        body.putArray("tags").add("Feuerwehr").add("Einsatz");
        details(anna, media, body.toString()).statusCode(200)
                .body("version", equalTo(0))
                .body("description", equalTo("Foto: Papa"))
                .body("tags", contains("Einsatz", "Feuerwehr"));
        as(anna).get("/api/media/%d/content".formatted(media)).then().statusCode(200)
                .header("ETag", equalTo("\"%d-0\"".formatted(media)));
    }

    @Test
    void detailsReplaceTheWholeState() {
        long id = upload(anna);
        ObjectNode body = MAPPER.createObjectNode().put("description", "Foto: Anna");
        body.putArray("tags").add("Sport");
        details(anna, id, body.toString()).statusCode(200);

        details(anna, id, "{\"description\": \"   \", \"tags\": []}").statusCode(200)
                .body("description", nullValue())
                .body("tags", empty());
        assertThat(strings("SELECT count(*) FROM media_tag")).containsExactly("0");
    }

    @Test
    void invalidDetailsChangeNothing() {
        long id = upload(anna);
        tags(anna, id, "Sport").statusCode(200);

        tags(anna, id, "Feuer, Wasser").statusCode(400).body("errors.field", contains("tags[0]"));
        tags(anna, id, IntStream.range(0, 21).mapToObj(i -> "Tag " + i).toArray(String[]::new)).statusCode(400)
                .body("errors.field", contains("tags"));
        details(anna, id, "{\"tags\": []}").statusCode(400).body("errors.field", contains("description"));
        details(anna, id, "{\"description\": null}").statusCode(400).body("errors.field", contains("tags"));
        details(anna, id, "{\"description\": null, \"tags\": [], \"version\": 0}").statusCode(400)
                .body("errors.field", contains("version"));
        details(anna, id, "{\"description\": 3, \"tags\": []}").statusCode(400)
                .body("errors.field", contains("description"));

        as(anna).get("/api/media/" + id).then().body("tags", contains("Sport"));
    }

    @Test
    void detailsAreForMediaUsersOnly() {
        long id = upload(anna);

        tags(readerOnly, id, "Sport").statusCode(403);
        tags(anna, 999999, "Sport").statusCode(404);
        given().contentType(ContentType.JSON).body("{\"description\": null, \"tags\": []}")
                .put("/api/media/%d/details".formatted(id)).then().statusCode(401).body(emptyString());
    }

    // --- tag suggestions

    @Test
    void suggestWhileTyping() {
        List<Long> ids = rows(20, "someone");
        for (int i = 0; i < 12; i++) {
            tags(anna, ids.get(i), "Feuerwehr").statusCode(200);
        }
        for (int i = 12; i < 15; i++) {
            tags(anna, ids.get(i), "Freiwillige Feuerwehr").statusCode(200);
        }
        for (int i = 15; i < 20; i++) {
            tags(anna, ids.get(i), "Fest").statusCode(200);
        }

        as(anna).get("/api/media/tags?prefix=feu").then().statusCode(200)
                .body("items.name", contains("Feuerwehr", "Freiwillige Feuerwehr"))
                .body("items.count", contains(12, 3));
        as(anna).queryParam("prefix", " FE").get("/api/media/tags").then().statusCode(200)
                .body("items.name", contains("Feuerwehr", "Fest", "Freiwillige Feuerwehr"));
        as(anna).get("/api/media/tags?prefix=wehr").then().statusCode(200).body("items", empty());
    }

    @Test
    void mostUsedTags() {
        List<Long> ids = rows(3, "someone");
        tags(anna, ids.get(0), "Sport", "Anna").statusCode(200);
        tags(anna, ids.get(1), "Sport", "Schule").statusCode(200);
        tags(anna, ids.get(2), "Sport").statusCode(200);

        as(anna).get("/api/media/tags").then().statusCode(200)
                .body("items.name", contains("Sport", "Anna", "Schule"))
                .body("items.count", contains(3, 1, 1));
        as(anna).get("/api/media/tags?limit=1").then().statusCode(200).body("items.name", contains("Sport"));
    }

    @Test
    void mostUsedTagsAreLimitedTo20ByDefault() {
        List<Long> ids = rows(1, "someone");
        tags(anna, ids.getFirst(), IntStream.range(0, 20).mapToObj(i -> "Tag " + i).toArray(String[]::new))
                .statusCode(200);
        List<Long> more = rows(1, "someone");
        tags(anna, more.getFirst(), "Extra").statusCode(200);

        as(anna).get("/api/media/tags").then().statusCode(200).body("items", hasSize(20));
    }

    @Test
    void likeWildcardsInThePrefixAreLiteral() {
        List<Long> ids = rows(2, "someone");
        tags(anna, ids.get(0), "100%").statusCode(200);
        tags(anna, ids.get(1), "1000").statusCode(200);

        as(anna).queryParam("prefix", "100%").get("/api/media/tags").then().statusCode(200).body("items.name", contains("100%"));
        as(anna).get("/api/media/tags?prefix=_").then().statusCode(200).body("items", empty());
    }

    @Test
    void invalidSuggestionLimit() {
        as(anna).get("/api/media/tags?limit=0").then().statusCode(400).body("errors.field", contains("limit"));
        as(anna).get("/api/media/tags?limit=101").then().statusCode(400).body("errors.field", contains("limit"));
        as(anna).get("/api/media/tags?limit=x").then().statusCode(400).body("errors.field", contains("limit"));
    }

    @Test
    void suggestionsAreForMediaUsersOnly() {
        as(readerOnly).get("/api/media/tags").then().statusCode(403);
        given().get("/api/media/tags").then().statusCode(401).body(emptyString());
    }

    // --- list filters

    @Test
    void twoTags() {
        List<Long> ids = rows(2, "someone");
        tags(anna, ids.get(0), "Feuerwehr", "Einsatz").statusCode(200);
        tags(anna, ids.get(1), "Feuerwehr").statusCode(200);

        as(anna).get("/api/media?tag=feuerwehr&tag=Einsatz").then().statusCode(200)
                .body("items.id", contains(ids.get(0).intValue()))
                .body("items[0].tags", contains("Einsatz", "Feuerwehr"));
        as(anna).get("/api/media?tag=Feuerwehr").then().statusCode(200)
                .body("items.id", contains(ints(ids.reversed())));
        as(anna).get("/api/media?tag=Unbekannt").then().statusCode(200).body("items", empty());
    }

    @Test
    void wordsInTheDescription() {
        List<Long> ids = rows(3, "someone");
        describe(anna, ids.get(0), "Einsatz am Dorfplatz, Foto: Anna");
        describe(anna, ids.get(1), "Dorfplatz im Winter");
        tags(anna, ids.get(2), "Anna", "Dorfplatz").statusCode(200);

        as(anna).queryParam("q", "dorfplatz anna").get("/api/media").then().statusCode(200)
                .body("items.id", contains(ids.get(2).intValue(), ids.get(0).intValue()));
        as(anna).queryParam("q", "  ").get("/api/media").then().statusCode(200).body("items", hasSize(3));
        as(anna).queryParam("q", "%").get("/api/media").then().statusCode(200).body("items", empty());
    }

    @Test
    void ownUnusedImages() {
        long used = upload(anna);
        long unused = upload(anna);
        long others = upload(publisher);
        article(anna, used);

        as(anna).get("/api/media?mine=true&unused=true").then().statusCode(200)
                .body("items.id", contains((int) unused));
        as(anna).get("/api/media?mine=true").then().statusCode(200)
                .body("items.id", contains((int) unused, (int) used));
        as(anna).get("/api/media?unused=true&mine=false").then().statusCode(200)
                .body("items.id", contains((int) others, (int) unused));
    }

    @Test
    void filteredPaging() {
        List<Long> ids = rows(75, "someone");
        List<Long> sport = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            if (i % 15 != 0) {
                sql("INSERT INTO media_tag (media_id, name, name_key) VALUES (%d, 'Sport', 'sport')"
                        .formatted(ids.get(i)));
                sport.add(ids.get(i));
            }
        }
        assertThat(sport).hasSize(70);
        List<Long> newest = sport.reversed();

        int next = as(anna).get("/api/media?tag=Sport").then().statusCode(200)
                .body("items.id", contains(ints(newest.subList(0, 60))))
                .body("next", equalTo(newest.get(59).intValue()))
                .extract().jsonPath().getInt("next");
        as(anna).get("/api/media?tag=Sport&before=" + next).then().statusCode(200)
                .body("items.id", contains(ints(newest.subList(60, 70))))
                .body("next", nullValue());
    }

    @Test
    void invalidFilters() {
        as(anna).get("/api/media?unused=yes").then().statusCode(400).body("errors.field", contains("unused"));
        as(anna).get("/api/media?mine=1").then().statusCode(400).body("errors.field", contains("mine"));
        as(anna).queryParam("tag", " ").get("/api/media").then().statusCode(400).body("errors.field", contains("tag"));
        as(anna).get("/api/media?" + String.join("&", IntStream.range(0, 11).mapToObj(i -> "tag=t" + i).toList()))
                .then().statusCode(400).body("errors.field", contains("tag"));
        as(anna).get("/api/media?q=" + "x".repeat(201)).then().statusCode(400).body("errors.field", contains("q"));
        as(anna).get("/api/media?" + String.join("&", IntStream.range(0, 10).mapToObj(i -> "tag=t" + i).toList()))
                .then().statusCode(200);
    }
}
