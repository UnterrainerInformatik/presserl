package info.unterrainer.presserl.media;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import info.unterrainer.presserl.TestSupport;
import info.unterrainer.presserl.bootstrap.KeycloakAdminProducer.KeycloakRealm;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Media list, usage and editing ({@code GET /api/media}, {@code GET /api/media/{id}/usage},
 * {@code POST /api/media/{id}/edit}) and the revalidated caching of the media bytes. {@code nogroups}
 * is a reporter in "Pets", {@code reader} a reporter in "Sport"; media rows, objects, articles and
 * sections are removed before and after every test.
 */
@QuarkusTest
class MediaEditResourceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    DataSource dataSource;

    @Inject
    S3Client s3;

    @Inject
    MediaStore store;

    @Inject
    MediaTrashSweeper sweeper;

    @Inject
    Keycloak keycloak;

    @Inject
    KeycloakRealm keycloakRealm;

    private String publisher;
    private String chief;
    private String anna;
    private String ben;
    private String readerOnly;
    private long pets;

    @BeforeEach
    void ready() {
        TestSupport.awaitReady();
        cleanUp();
        publisher = TestSupport.token("publisher", "publisher");
        chief = TestSupport.token("chief", "chief");
        readerOnly = TestSupport.token("reader", "reader");
        pets = section("Pets");
        assign(pets, "nogroups");
        assign(section("Sport"), "reader");
        anna = TestSupport.token("nogroups", "nogroups");
        ben = TestSupport.token("reader", "reader");
    }

    @AfterEach
    void cleanUp() {
        TestSupport.deleteSections(dataSource);
        sql("DELETE FROM media");
        sql("DELETE FROM media_object_trash");
        objects().forEach(key -> s3.deleteObject(b -> b.bucket(store.bucket()).key(key)));
    }

    // --- helpers

    private long section(String name) {
        return as(publisher).contentType(ContentType.JSON).body("{\"name\": \"%s\"}".formatted(name))
                .post("/api/sections").then().statusCode(201).extract().jsonPath().getLong("id");
    }

    private void assign(long section, String username) {
        String id = keycloak.realm(keycloakRealm.name()).users().searchByUsername(username, true).getFirst().getId();
        as(publisher).contentType(ContentType.JSON).body("{\"role\": \"REPORTER\"}")
                .put("/api/sections/%d/members/%s".formatted(section, id)).then().statusCode(200);
    }

    private static RequestSpecification as(String token) {
        return given().auth().oauth2(token);
    }

    private static long upload(String token, byte[] content) {
        return as(token).multiPart("file", "photo.jpg", content, "image/jpeg").post("/api/media").then()
                .statusCode(201).extract().jsonPath().getLong("id");
    }

    private static long upload(String token) {
        return upload(token, MediaFixtures.jpeg(1600, 1067));
    }

    private static long article(String token, long section, String headline, long mediaId) {
        ObjectNode content = MAPPER.createObjectNode().put("headline", headline).put("sectionId", section);
        content.putObject("leadImage").put("mediaId", mediaId);
        return as(token).contentType(ContentType.JSON).body(content.toString()).post("/api/articles").then()
                .statusCode(201).extract().jsonPath().getLong("id");
    }

    private static void save(String token, long article, long section, String headline, Long mediaId) {
        long version = as(token).get("/api/articles/" + article).then().statusCode(200).extract().jsonPath()
                .getLong("version");
        ObjectNode content = MAPPER.createObjectNode().put("headline", headline).put("sectionId", section)
                .put("version", version);
        if (mediaId != null) {
            content.putObject("leadImage").put("mediaId", mediaId);
        }
        as(token).contentType(ContentType.JSON).body(content.toString()).put("/api/articles/" + article).then()
                .statusCode(200);
    }

    /**
     * Makes revision 1 live, as if the approval chain had published it.
     */
    private void makeLive(long article) {
        sql("UPDATE article SET status = 'PUBLISHED', live_revision = 1, published_at = now() WHERE id = "
                + article);
        sql("UPDATE article_revision SET published_at = now() WHERE article_id = %d AND number = 1"
                .formatted(article));
    }

    private static ValidatableResponse edit(String token, long id, String body) {
        return as(token).contentType(ContentType.JSON).body(body).post("/api/media/%d/edit".formatted(id)).then();
    }

    private static String pixelate(long version) {
        return "{\"version\": %d, \"pixelate\": [{\"cx\": 400, \"cy\": 300, \"rx\": 100, \"ry\": 60}]}"
                .formatted(version);
    }

    private List<String> objects() {
        return s3.listObjectsV2(b -> b.bucket(store.bucket()).prefix("media/")).contents().stream()
                .map(S3Object::key).toList();
    }

    private List<String> keys(long media) {
        return strings(("SELECT object_key FROM media WHERE id = %d UNION ALL SELECT object_key FROM media_rendition "
                + "WHERE media_id = %d ORDER BY 1").formatted(media, media));
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

    // --- version

    @Test
    void newMediaHasVersionZero() {
        long id = as(publisher).multiPart("file", "photo.jpg", MediaFixtures.jpeg(800, 600), "image/jpeg")
                .post("/api/media").then().statusCode(201).body("version", equalTo(0)).extract().jsonPath()
                .getLong("id");

        as(publisher).get("/api/media/" + id).then().statusCode(200).body("version", equalTo(0));
    }

    // --- list

    @Test
    void listPagesNewestFirstWithUsageCount() {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ids.add(upload(publisher, MediaFixtures.jpeg(64, 48)));
        }
        long first = article(publisher, pets, "One", ids.get(4));
        long second = article(publisher, pets, "Two", ids.get(4));
        save(publisher, second, pets, "Two", ids.get(3));

        Response page = as(anna).get("/api/media?limit=3");
        page.then().statusCode(200)
                .body("items.id", contains(ids.get(4).intValue(), ids.get(3).intValue(), ids.get(2).intValue()))
                .body("items[0].usageCount", equalTo(1))
                .body("items[1].usageCount", equalTo(1))
                .body("items[2].usageCount", equalTo(0))
                .body("items[0].version", equalTo(0))
                .body("items[0].uploadedBy.username", equalTo("publisher"))
                .body("items[0].renditions.thumbnail.width", equalTo(64))
                .body("next", equalTo(ids.get(2).intValue()));
        assertThat(first).isPositive();

        as(anna).get("/api/media?limit=3&before=" + ids.get(2)).then().statusCode(200)
                .body("items.id", contains(ids.get(1).intValue(), ids.get(0).intValue()))
                .body("next", nullValue());
        as(anna).get("/api/media").then().statusCode(200).body("items.size()", equalTo(5))
                .body("next", nullValue());
    }

    @Test
    void usageCountCountsArticlesNotRevisions() {
        long media = upload(publisher);
        long id = article(publisher, pets, "One", media);
        makeLive(id);
        save(publisher, id, pets, "One changed", media);

        as(publisher).get("/api/media").then().statusCode(200).body("items[0].usageCount", equalTo(1));
    }

    @Test
    void listRefusesBadParameters() {
        as(publisher).get("/api/media?limit=500").then().statusCode(400).body("errors.field", contains("limit"));
        as(publisher).get("/api/media?limit=0").then().statusCode(400).body("errors.field", contains("limit"));
        as(publisher).get("/api/media?limit=x").then().statusCode(400).body("errors.field", contains("limit"));
        as(publisher).get("/api/media?before=-3").then().statusCode(400).body("errors.field", contains("before"));
        as(publisher).get("/api/media?before=abc").then().statusCode(400).body("errors.field", contains("before"));
    }

    @Test
    void listIsForWritersOnly() {
        sql("DELETE FROM section");
        as(readerOnly).get("/api/media").then().statusCode(403);
        given().get("/api/media").then().statusCode(401).body(emptyString());
        given().get("/api/media/1/usage").then().statusCode(401).body(emptyString());
        given().contentType(ContentType.JSON).body(pixelate(0)).post("/api/media/1/edit").then().statusCode(401)
                .body(emptyString());
        as(readerOnly).get("/api/media/1/usage").then().statusCode(403);
        edit(readerOnly, 1, pixelate(0)).statusCode(403);
    }

    // --- usage

    @Test
    void usageShowsLiveAndDraftUse() {
        long media = upload(publisher);
        long published = article(publisher, pets, "Our cat Minka", media);
        makeLive(published);
        long draft = article(anna, pets, "Minka again", media);

        as(anna).get("/api/media/%d/usage".formatted(media)).then().statusCode(200)
                .body("mayEdit", equalTo(false))
                .body("articles.id", contains((int) draft, (int) published))
                .body("articles[0].status", equalTo("DRAFT"))
                .body("articles[0].publishedAt", nullValue())
                .body("articles[0].latest", equalTo(true))
                .body("articles[0].live", equalTo(false))
                .body("articles[0].author.username", equalTo("nogroups"))
                .body("articles[1].headline", equalTo("Our cat Minka"))
                .body("articles[1].status", equalTo("PUBLISHED"))
                .body("articles[1].section.name", equalTo("Pets"))
                .body("articles[1].section.color", equalTo("red"))
                .body("articles[1].live", equalTo(true))
                .body("articles[1].latest", equalTo(true))
                .body("articles[1].older", equalTo(false))
                .body("articles[1].pendingLevel", nullValue());
        as(publisher).get("/api/media/%d/usage".formatted(media)).then().body("mayEdit", equalTo(true));
    }

    @Test
    void imageReplacedInANewerRevision() {
        long cat = upload(publisher);
        long dog = upload(publisher);
        long id = article(publisher, pets, "Minka", cat);
        makeLive(id);
        save(publisher, id, pets, "Minka", dog);

        as(publisher).get("/api/media/%d/usage".formatted(cat)).then().statusCode(200)
                .body("articles[0].live", equalTo(true))
                .body("articles[0].latest", equalTo(false))
                .body("articles[0].older", equalTo(false));

        sql("UPDATE article SET live_revision = 2 WHERE id = " + id);
        as(publisher).get("/api/media/%d/usage".formatted(cat)).then().statusCode(200)
                .body("articles[0].live", equalTo(false))
                .body("articles[0].latest", equalTo(false))
                .body("articles[0].older", equalTo(true));
    }

    @Test
    void unusedAndUnknownMedia() {
        long media = upload(anna);

        as(anna).get("/api/media/%d/usage".formatted(media)).then().statusCode(200)
                .body("mayEdit", equalTo(true))
                .body("articles.size()", equalTo(0));
        as(anna).get("/api/media/999999/usage").then().statusCode(404);
        edit(publisher, 999999, pixelate(0)).statusCode(404);
    }

    // --- who may edit

    @Test
    void reporterEditsTheirDraftImage() {
        long media = upload(anna);
        article(anna, pets, "Minka", media);

        edit(anna, media, pixelate(0)).statusCode(200).body("version", equalTo(1));
    }

    @Test
    void reporterCannotEditTheirLiveImage() {
        long media = upload(anna);
        makeLive(article(anna, pets, "Minka", media));
        List<String> before = keys(media);

        edit(anna, media, pixelate(0)).statusCode(403);

        assertThat(keys(media)).isEqualTo(before);
        assertThat(objects()).hasSize(4);
        as(anna).get("/api/media/" + media).then().body("version", equalTo(0));
    }

    @Test
    void reporterCannotEditAnImageUnderReview() {
        long media = upload(anna);
        long id = article(anna, pets, "Minka", media);
        as(anna).post("/api/articles/%d/submit".formatted(id)).then().statusCode(200);

        edit(anna, media, pixelate(0)).statusCode(403);
        as(anna).get("/api/media/%d/usage".formatted(media)).then()
                .body("mayEdit", equalTo(false))
                .body("articles[0].pendingLevel", notNullValue());
    }

    @Test
    void reporterCannotEditSomeoneElsesImage() {
        long media = upload(anna);

        edit(ben, media, pixelate(0)).statusCode(403);
        as(ben).get("/api/media/%d/usage".formatted(media)).then().body("mayEdit", equalTo(false));
    }

    @Test
    void publisherAndChiefEditALiveImage() {
        long media = upload(anna);
        makeLive(article(anna, pets, "Minka", media));

        edit(publisher, media, pixelate(0)).statusCode(200).body("version", equalTo(1));
        edit(chief, media, pixelate(1)).statusCode(200).body("version", equalTo(2));
    }

    // --- editing

    @Test
    void cropAndPixelateTogether() {
        long media = upload(chief);
        List<String> before = keys(media);

        edit(chief, media, """
                {"version": 0, "crop": {"x": 100, "y": 50, "width": 1200, "height": 800},
                 "pixelate": [{"cx": 600, "cy": 400, "rx": 80, "ry": 110}]}""").statusCode(200)
                .body("id", equalTo((int) media))
                .body("version", equalTo(1))
                .body("width", equalTo(1200))
                .body("height", equalTo(800))
                .body("uploadedBy.username", equalTo("chief"))
                .body("renditions.web.width", equalTo(1200))
                .body("renditions.thumbnail.width", equalTo(480))
                .body("renditions.thumbnail.height", equalTo(320));

        List<String> after = keys(media);
        assertThat(after).hasSize(4).doesNotContainAnyElementsOf(before);
        assertThat(objects()).containsExactlyInAnyOrderElementsOf(after);
        assertThat(strings("SELECT object_key FROM media_object_trash")).isEmpty();
        byte[] content = as(chief).get("/api/media/%d/content".formatted(media)).then().statusCode(200).extract()
                .asByteArray();
        assertThat(MediaFixtures.read(content).getWidth()).isEqualTo(1200);
        as(chief).get("/api/media/" + media).then().body("size", equalTo(content.length));
    }

    @Test
    void pixelatedAreaConsistsOfBlocks() {
        long media = upload(publisher, png());

        edit(publisher, media, pixelate(0)).statusCode(200);

        java.awt.image.BufferedImage image = MediaFixtures.read(as(publisher)
                .get("/api/media/%d/content".formatted(media)).asByteArray());
        // block (390..404, 300..314) lies inside the ellipse; JPEG may shift colours slightly
        int reference = image.getRGB(397, 307);
        for (int y = 302; y < 312; y++) {
            for (int x = 392; x < 402; x++) {
                assertThat(distance(image.getRGB(x, y), reference)).as("%d,%d", x, y).isLessThan(24);
            }
        }
    }

    private static byte[] png() {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(800, 600,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 600; y++) {
            for (int x = 0; x < 800; x++) {
                image.setRGB(x, y, (x * 7 % 256) << 16 | (y * 13 % 256) << 8 | ((x + y) * 5 % 256));
            }
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try {
            javax.imageio.ImageIO.write(image, "png", out);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static int distance(int a, int b) {
        return Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF)) + Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF))
                + Math.abs((a & 0xFF) - (b & 0xFF));
    }

    @Test
    void invalidEditsChangeNothing() {
        long media = upload(publisher);
        List<String> before = keys(media);

        edit(publisher, media, "{\"version\": 0, \"crop\": {\"x\": 1000, \"y\": 0, \"width\": 800, \"height\": 500}}")
                .statusCode(400).body("errors.field", contains("crop"));
        edit(publisher, media, "{\"version\": 0, \"pixelate\": []}").statusCode(400)
                .body("errors.field", contains("crop"));
        edit(publisher, media, "{\"pixelate\": [{\"cx\": 1, \"cy\": 1, \"rx\": 5, \"ry\": 5}]}").statusCode(400)
                .body("errors.field", contains("version"));
        edit(publisher, media, "{\"version\": 0, \"pixelate\": [{\"cx\": 1, \"cy\": 1, \"rx\": 2, \"ry\": 5}]}")
                .statusCode(400).body("errors.field", contains("pixelate[0]"));

        assertThat(keys(media)).isEqualTo(before);
        assertThat(objects()).hasSize(4);
    }

    @Test
    void staleVersionIsAConflict() {
        long media = upload(publisher);
        edit(publisher, media, pixelate(0)).statusCode(200);
        List<String> before = keys(media);

        edit(chief, media, pixelate(0)).statusCode(409)
                .body("errors[0].field", equalTo("version"))
                .body("errors[0].message", equalTo("media %d was changed meanwhile".formatted(media)));

        assertThat(keys(media)).isEqualTo(before);
        assertThat(objects()).containsExactlyInAnyOrderElementsOf(before);
        as(publisher).get("/api/media/" + media).then().body("version", equalTo(1));
    }

    @Test
    void storeDownDuringAnEditLeavesTheMediaUnchanged() {
        long media = upload(publisher);
        List<String> before = keys(media);
        QuarkusMock.installMockForType(new FailingMediaStore(s3, store.bucket(), 2), MediaStore.class);

        edit(publisher, media, pixelate(0)).statusCode(503);

        assertThat(keys(media)).isEqualTo(before);
        assertThat(objects()).containsExactlyInAnyOrderElementsOf(before);
        as(publisher).get("/api/media/" + media).then().body("version", equalTo(0));
    }

    @Test
    void sweeperDeletesTrashedObjects() throws Throwable {
        String key = store.put(MediaFixtures.jpeg(20, 20), "image/jpeg", "jpg");
        sql("INSERT INTO media_object_trash (object_key, created_at) VALUES ('%s', now())".formatted(key));
        sql("INSERT INTO media_object_trash (object_key, created_at) VALUES ('media/already-gone.jpg', now())");

        assertThat(sweeper.run()).isEqualTo(2);

        assertThat(strings("SELECT object_key FROM media_object_trash")).isEmpty();
        assertThat(objects()).doesNotContain(key);
        assertThat(sweeper.run()).isZero();
    }

    // --- caching

    @Test
    void contentAndRenditionsAreRevalidated() {
        long media = upload(publisher);
        String etag = "\"%d-0\"".formatted(media);

        for (String path : List.of("content", "renditions/web", "renditions/thumbnail")) {
            String url = "/api/media/%d/%s".formatted(media, path);
            as(publisher).get(url).then().statusCode(200)
                    .header("Cache-Control", equalTo("private, no-cache"))
                    .header("ETag", equalTo(etag));
            as(publisher).header("If-None-Match", etag).get(url).then().statusCode(304)
                    .header("ETag", equalTo(etag))
                    .body(emptyString());
        }

        edit(publisher, media, pixelate(0)).statusCode(200);

        as(publisher).header("If-None-Match", etag).get("/api/media/%d/content".formatted(media)).then()
                .statusCode(200)
                .header("ETag", equalTo("\"%d-1\"".formatted(media)));
        as(publisher).header("If-None-Match", etag).get("/api/media/%d/renditions/web".formatted(media)).then()
                .statusCode(200)
                .header("ETag", equalTo("\"%d-1\"".formatted(media)));
    }

    @Test
    void notModifiedDoesNotReadTheObjectStore() {
        long media = upload(publisher);
        keys(media).forEach(key -> s3.deleteObject(b -> b.bucket(store.bucket()).key(key)));

        as(publisher).header("If-None-Match", "\"%d-0\"".formatted(media))
                .get("/api/media/%d/content".formatted(media)).then().statusCode(304);
        as(publisher).header("If-None-Match", "W/\"%d-0\"".formatted(media))
                .get("/api/media/%d/renditions/print".formatted(media)).then().statusCode(304);
    }
}
