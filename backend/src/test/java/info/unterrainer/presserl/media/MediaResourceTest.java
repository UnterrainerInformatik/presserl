package info.unterrainer.presserl.media;

import static info.unterrainer.presserl.media.MediaFixtures.bytes;
import static info.unterrainer.presserl.media.MediaFixtures.directories;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;

import info.unterrainer.presserl.TestSupport;
import info.unterrainer.presserl.bootstrap.KeycloakAdminProducer.KeycloakRealm;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * {@code /api/media} against the Dev Services RustFS. The dev realm has {@code publisher},
 * {@code chief}, {@code reader} ({@code READER} only) and {@code nogroups} (no role); media rows,
 * objects, articles and sections are removed before every test.
 */
@QuarkusTest
class MediaResourceTest {

    @Inject
    DataSource dataSource;

    @Inject
    S3Client s3;

    @Inject
    MediaStore store;

    @Inject
    MediaRenditionBackfill backfill;

    @Inject
    Keycloak keycloak;

    @Inject
    KeycloakRealm keycloakRealm;

    private String publisher;
    private String reader;

    @BeforeEach
    void ready() {
        TestSupport.awaitReady();
        TestSupport.deleteSections(dataSource);
        sql("DELETE FROM media");
        objects().forEach(key -> s3.deleteObject(b -> b.bucket(store.bucket()).key(key)));
        publisher = TestSupport.token("publisher", "publisher");
        reader = TestSupport.token("reader", "reader");
    }

    private static RequestSpecification as(String token) {
        return given().auth().oauth2(token);
    }

    private static ExtractableResponse<Response> upload(String token, String name, byte[] content, String type,
            int status) {
        return as(token).multiPart("file", name, content, type).post("/api/media").then().statusCode(status)
                .extract();
    }

    private List<String> objects() {
        return s3.listObjectsV2(b -> b.bucket(store.bucket()).prefix("media/")).contents().stream()
                .map(S3Object::key).toList();
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

    private String masterKey() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT object_key FROM media")) {
            rs.next();
            return rs.getString(1);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private long renditionRows() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT count(*) FROM media_rendition")) {
            rs.next();
            return rs.getLong(1);
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

    private void assertNothingStored() {
        assertThat(mediaRows()).isZero();
        assertThat(objects()).isEmpty();
    }

    private void assertFileError(ExtractableResponse<Response> response) {
        assertThat(response.jsonPath().getList("errors.field")).containsExactly("file");
    }

    @Test
    void reporterUploadsAnImage() {
        long section = as(publisher).contentType(ContentType.JSON).body("{\"name\": \"Sport\"}").post("/api/sections")
                .then().statusCode(201).extract().jsonPath().getLong("id");
        String nogroups = keycloak.realm(keycloakRealm.name()).users().searchByUsername("nogroups", true).getFirst()
                .getId();
        as(publisher).contentType(ContentType.JSON).body("{\"role\": \"REPORTER\"}")
                .put("/api/sections/%d/members/%s".formatted(section, nogroups)).then().statusCode(200);
        String reporter = TestSupport.token("nogroups", "nogroups");

        ExtractableResponse<Response> created = as(reporter).multiPart("file", "photo.jpg", bytes("photo-gps.jpg"),
                "image/jpeg").post("/api/media").then()
                .statusCode(201)
                .body("contentType", equalTo("image/jpeg"))
                .body("width", equalTo(640))
                .body("height", equalTo(480))
                .body("size", greaterThan(0))
                .body("uploadedBy.username", equalTo("nogroups"))
                .body("uploadedBy.displayName", equalTo("No Groups"))
                .body("uploadedAt", notNullValue())
                .extract();
        long id = created.jsonPath().getLong("id");

        assertThat(created.header("Location")).endsWith("/api/media/" + id);
        assertThat(mediaRows()).isEqualTo(1);
        assertThat(objects()).hasSize(4).allSatisfy(key -> assertThat(key).matches("media/[0-9a-f-]{36}\\.jpg"));
    }

    @Test
    void pngWithMisleadingNameIsStoredAsJpegAndReadBack() {
        long id = upload(publisher, "photo.jpg", bytes("opaque.png"), "image/jpeg", 201).jsonPath().getLong("id");

        as(publisher).get("/api/media/" + id).then()
                .statusCode(200)
                .body("id", equalTo((int) id))
                .body("version", equalTo(0))
                .body("contentType", equalTo("image/jpeg"))
                .body("width", equalTo(1200))
                .body("height", equalTo(800))
                .body("uploadedBy.username", equalTo("publisher"));

        Response content = as(publisher).get("/api/media/%d/content".formatted(id));
        byte[] downloaded = content.asByteArray();
        content.then()
                .statusCode(200)
                .header("Content-Type", equalTo("image/jpeg"))
                .header("Content-Disposition", equalTo("inline"))
                .header("X-Content-Type-Options", equalTo("nosniff"))
                .header("Cache-Control", equalTo("private, no-cache"))
                .header("ETag", equalTo("\"%d-0\"".formatted(id)));
        byte[] stored = s3.getObjectAsBytes(b -> b.bucket(store.bucket()).key(masterKey())).asByteArray();
        assertThat(downloaded).isEqualTo(stored);
        assertThat(content.header("Content-Length")).isEqualTo(String.valueOf(stored.length));
        as(publisher).get("/api/media/" + id).then().body("size", equalTo(stored.length));
    }

    @Test
    void storedObjectHasNoMetadata() {
        upload(publisher, "gps.jpg", bytes("photo-gps.jpg"), "image/jpeg", 201);

        byte[] stored = s3.getObjectAsBytes(b -> b.bucket(store.bucket()).key(masterKey())).asByteArray();
        assertThat(directories(stored)).doesNotContain("ExifIFD0Directory", "ExifSubIFDDirectory", "GpsDirectory",
                "XmpDirectory", "IptcDirectory", "JpegCommentDirectory");
    }

    @Test
    void transparentWebpIsStoredAsPng() {
        upload(publisher, "sticker.webp", bytes("transparent.webp"), "image/webp", 201).jsonPath();

        assertThat(objects()).hasSize(4).allSatisfy(key -> assertThat(key).endsWith(".png"));
    }

    @Test
    void readerOnlyUserIsRefused() {
        long id = upload(publisher, "photo.jpg", bytes("photo-gps.jpg"), "image/jpeg", 201).jsonPath().getLong("id");

        upload(reader, "photo.jpg", bytes("photo-gps.jpg"), "image/jpeg", 403);
        as(reader).get("/api/media/" + id).then().statusCode(403);
        as(reader).get("/api/media/%d/content".formatted(id)).then().statusCode(403);
        as(reader).get("/api/media/%d/renditions/web".formatted(id)).then().statusCode(403);
        assertThat(mediaRows()).isEqualTo(1);
        assertThat(objects()).hasSize(4);
    }

    @Test
    void sectionlessReporterUploadsListsAndSeesTheUsage() {
        String id = keycloak.realm(keycloakRealm.name()).users().searchByUsername("nogroups", true).getFirst().getId();
        sql("INSERT INTO sectionless_reporter (account_id, assigned_by, assigned_at) VALUES ('%s', 'test', now())"
                .formatted(id));
        String photographer = TestSupport.token("nogroups", "nogroups");

        long media = upload(photographer, "photo.jpg", bytes("photo-gps.jpg"), "image/jpeg", 201).jsonPath()
                .getLong("id");

        as(photographer).get("/api/media").then().statusCode(200).body("items.id", contains((int) media));
        as(photographer).get("/api/media/%d/usage".formatted(media)).then().statusCode(200)
                .body("mayEdit", equalTo(true))
                .body("articles", empty());
        as(photographer).get("/api/media/%d/renditions/web".formatted(media)).then().statusCode(200);
        // media only: the article endpoints stay closed
        as(photographer).get("/api/articles").then().statusCode(403);
    }

    @Test
    void readerOnlyUserWithoutFilePartIsRefusedToo() {
        as(reader).multiPart("other", "value").post("/api/media").then().statusCode(403);
    }

    @Test
    void noTokenIsUnauthorizedWithEmptyBody() {
        given().multiPart("file", "photo.jpg", bytes("photo-gps.jpg"), "image/jpeg").post("/api/media").then()
                .statusCode(401).body(emptyString());
        given().get("/api/media/1").then().statusCode(401).body(emptyString());
        given().get("/api/media/1/content").then().statusCode(401).body(emptyString());
        given().get("/api/media/1/renditions/web").then().statusCode(401).body(emptyString());
        assertNothingStored();
    }

    @Test
    void missingFilePartIsInvalid() {
        assertFileError(as(publisher).multiPart("other", "value").post("/api/media").then().statusCode(400).extract());
        assertFileError(as(publisher).multiPart("image", "photo.jpg", bytes("photo-gps.jpg"), "image/jpeg")
                .post("/api/media").then().statusCode(400).extract());
        assertNothingStored();
    }

    @Test
    void multipleFilesAreInvalid() {
        assertFileError(as(publisher)
                .multiPart("file", "one.jpg", bytes("photo-gps.jpg"), "image/jpeg")
                .multiPart("file", "two.png", bytes("opaque.png"), "image/png")
                .post("/api/media").then().statusCode(400).extract());
        assertNothingStored();
    }

    @Test
    void emptyFileIsInvalid() {
        assertFileError(upload(publisher, "empty.jpg", new byte[0], "image/jpeg", 400));
        assertNothingStored();
    }

    @Test
    void fileAboveTheLimitIsTooLarge() {
        byte[] content = Arrays.copyOf(bytes("photo-gps.jpg"), 11 * 1024 * 1024);

        assertFileError(upload(publisher, "huge.jpg", content, "image/jpeg", 413));
        assertNothingStored();
    }

    @Test
    void fileAtTheLimitPassesTheSizeCheck() {
        // exactly 10M: a JPEG followed by zeros, which decoders ignore after the end marker
        byte[] content = Arrays.copyOf(bytes("photo-gps.jpg"), 10 * 1024 * 1024);

        upload(publisher, "limit.jpg", content, "image/jpeg", 201);
    }

    @Test
    void htmlDisguisedAsJpegIsUnsupported() {
        assertFileError(upload(publisher, "cat.jpg", bytes("cat.jpg"), "image/jpeg", 415));
        assertNothingStored();
    }

    @Test
    void heicIsUnsupported() {
        assertFileError(upload(publisher, "photo.heic", bytes("image.heic"), "image/heic", 415));
    }

    @Test
    void decompressionBombIsInvalid() {
        assertFileError(upload(publisher, "bomb.png", bytes("bomb.png"), "image/png", 400));
        assertNothingStored();
    }

    @Test
    void truncatedJpegIsInvalid() {
        byte[] jpeg = bytes("photo-gps.jpg");
        assertFileError(upload(publisher, "half.jpg", Arrays.copyOf(jpeg, jpeg.length / 2), "image/jpeg", 400));
        assertNothingStored();
    }

    @Test
    void unknownMediaIsNotFound() {
        as(publisher).get("/api/media/999999").then().statusCode(404).body("errors[0].field", equalTo(null));
        as(publisher).get("/api/media/999999/content").then().statusCode(404)
                .contentType(ContentType.JSON);
    }

    @Test
    void uploadListsTheThreeRenditions() {
        upload(publisher, "big.jpg", MediaFixtures.jpeg(6000, 4000), "image/jpeg", 201).response().then()
                .body("renditions.thumbnail.width", equalTo(480))
                .body("renditions.thumbnail.height", equalTo(320))
                .body("renditions.thumbnail.size", greaterThan(0))
                .body("renditions.web.width", equalTo(1600))
                .body("renditions.web.height", equalTo(1067))
                .body("renditions.web.size", greaterThan(0))
                .body("renditions.print.width", equalTo(3000))
                .body("renditions.print.height", equalTo(2000))
                .body("renditions.print.size", greaterThan(0));

        assertThat(renditionRows()).isEqualTo(3);
        assertThat(objects()).hasSize(4);
    }

    @Test
    void mediumPngIsNotEnlargedForWebAndPrint() {
        long id = upload(publisher, "opaque.png", bytes("opaque.png"), "image/png", 201).jsonPath().getLong("id");

        as(publisher).get("/api/media/" + id).then().statusCode(200)
                .body("renditions.thumbnail.width", equalTo(480))
                .body("renditions.thumbnail.height", equalTo(320))
                .body("renditions.web.width", equalTo(1200))
                .body("renditions.web.height", equalTo(800))
                .body("renditions.print.width", equalTo(1200))
                .body("renditions.print.height", equalTo(800));
    }

    @Test
    void everyRenditionCanBeDownloaded() {
        ExtractableResponse<Response> created = upload(publisher, "big.jpg", MediaFixtures.jpeg(6000, 4000),
                "image/jpeg", 201);
        long id = created.jsonPath().getLong("id");

        for (String kind : List.of("thumbnail", "web", "print")) {
            Response response = as(publisher).get("/api/media/%d/renditions/%s".formatted(id, kind));
            response.then()
                    .statusCode(200)
                    .header("Content-Type", equalTo("image/jpeg"))
                    .header("Content-Disposition", equalTo("inline"))
                    .header("X-Content-Type-Options", equalTo("nosniff"))
                    .header("Cache-Control", equalTo("private, no-cache"))
                    .header("ETag", equalTo("\"%d-0\"".formatted(id)));
            byte[] bytes = response.asByteArray();
            assertThat(response.header("Content-Length")).isEqualTo(String.valueOf(bytes.length));
            assertThat(bytes.length).isEqualTo(created.jsonPath().getInt("renditions." + kind + ".size"));
            java.awt.image.BufferedImage image = MediaFixtures.read(bytes);
            assertThat(image.getWidth()).as(kind).isEqualTo(created.jsonPath().getInt("renditions." + kind + ".width"));
            assertThat(image.getHeight()).as(kind).isEqualTo(created.jsonPath().getInt("renditions." + kind + ".height"));
            assertThat(directories(bytes)).doesNotContain("ExifIFD0Directory", "GpsDirectory", "XmpDirectory");
        }
    }

    @Test
    void unknownRenditionIsNotFound() {
        long id = upload(publisher, "photo.jpg", bytes("photo-gps.jpg"), "image/jpeg", 201).jsonPath().getLong("id");

        as(publisher).get("/api/media/%d/renditions/huge".formatted(id)).then().statusCode(404)
                .contentType(ContentType.JSON).body("errors[0].field", equalTo(null));
        as(publisher).get("/api/media/%d/renditions/WEB".formatted(id)).then().statusCode(404);
        as(publisher).get("/api/media/999999/renditions/web").then().statusCode(404)
                .contentType(ContentType.JSON);
    }

    @Test
    void storeFailureDuringARenditionLeavesNothingBehind() {
        QuarkusMock.installMockForType(new FailingMediaStore(s3, store.bucket(), 3), MediaStore.class);

        upload(publisher, "photo.jpg", bytes("photo-gps.jpg"), "image/jpeg", 503);

        assertThat(mediaRows()).isZero();
        assertThat(renditionRows()).isZero();
        assertThat(objects()).isEmpty();
    }

    @Test
    void backfillProducesRenditionsForOldMedia() {
        long id = upload(publisher, "big.jpg", MediaFixtures.jpeg(2000, 1000), "image/jpeg", 201).jsonPath()
                .getLong("id");
        // as if uploaded before renditions existed
        List<String> renditionKeys = objects().stream().filter(key -> !key.equals(masterKey())).toList();
        sql("DELETE FROM media_rendition");
        renditionKeys.forEach(key -> s3.deleteObject(b -> b.bucket(store.bucket()).key(key)));
        as(publisher).get("/api/media/" + id).then().statusCode(200).body("renditions.size()", equalTo(0));
        as(publisher).get("/api/media/%d/renditions/web".formatted(id)).then().statusCode(404);
        given().get("/q/health/ready").then().statusCode(200);

        assertThat(backfill.run()).isEqualTo(1);

        as(publisher).get("/api/media/" + id).then().statusCode(200)
                .body("renditions.thumbnail.width", equalTo(480))
                .body("renditions.web.width", equalTo(1600))
                .body("renditions.print.width", equalTo(2000));
        as(publisher).get("/api/media/%d/renditions/thumbnail".formatted(id)).then().statusCode(200);
        assertThat(objects()).hasSize(4);
        assertThat(backfill.run()).isZero();
    }

    @Test
    void readinessReportsTheObjectStore() {
        given().get("/q/health/ready").then().statusCode(200)
                .body("checks.find { it.name == 'media-store' }.status", equalTo("UP"))
                .body("checks.find { it.name == 'media-store' }.data.bucket", endsWith("presserl-media"));
    }
}
