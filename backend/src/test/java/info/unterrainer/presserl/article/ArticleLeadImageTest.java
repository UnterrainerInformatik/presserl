package info.unterrainer.presserl.article;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import info.unterrainer.presserl.TestSupport;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;

/**
 * The lead image of article revisions. Media rows are inserted directly (the article endpoints never
 * read the bytes); articles, sections and media are deleted before and after every test.
 */
@QuarkusTest
class ArticleLeadImageTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    DataSource dataSource;

    private String publisher;
    private long cat;
    private long dog;

    @BeforeEach
    void ready() {
        TestSupport.awaitReady();
        cleanUp();
        publisher = TestSupport.token("publisher", "publisher");
        cat = media(1600, 1067);
        dog = media(800, 1200);
    }

    @AfterEach
    void cleanUp() {
        TestSupport.deleteSections(dataSource);
        sql("DELETE FROM media");
    }

    private long media(int width, int height) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("""
                        INSERT INTO media (object_key, content_type, width, height, byte_size, uploader_sub,
                            uploader_username, uploader_display_name, created_at)
                        VALUES ('media/' || gen_random_uuid() || '.jpg', 'image/jpeg', %d, %d, 1000, 'sub',
                            'publisher', 'Publisher', now()) RETURNING id""".formatted(width, height))) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private long revisions() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT count(*) FROM article_revision")) {
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

    private static RequestSpecification as(String token) {
        return given().auth().oauth2(token).contentType(ContentType.JSON);
    }

    private static ObjectNode content(String headline) {
        return MAPPER.createObjectNode().put("headline", headline);
    }

    private static ObjectNode withImage(String headline, long mediaId, String caption) {
        ObjectNode content = content(headline);
        ObjectNode leadImage = content.putObject("leadImage").put("mediaId", mediaId);
        if (caption != null) {
            leadImage.put("caption", caption);
        }
        return content;
    }

    private long create(ObjectNode content) {
        return as(publisher).body(content.toString()).post("/api/articles").then().statusCode(201).extract()
                .jsonPath().getLong("id");
    }

    private ValidatableResponse get(long id) {
        return as(publisher).get("/api/articles/" + id).then();
    }

    private ValidatableResponse save(long id, ObjectNode content) {
        long version = get(id).statusCode(200).extract().jsonPath().getLong("version");
        return as(publisher).body(content.deepCopy().put("version", version).toString()).put("/api/articles/" + id)
                .then();
    }

    private ValidatableResponse publish(long id) {
        return as(publisher).post("/api/articles/" + id + "/publish").then();
    }

    @Test
    void createWithoutLeadImage() {
        as(publisher).body("{}").post("/api/articles").then().statusCode(201).body("leadImage", nullValue());
    }

    @Test
    void createWithLeadImage() {
        as(publisher).body(withImage("Minka", cat, "Our cat Minka").toString()).post("/api/articles").then()
                .statusCode(201)
                .body("leadImage.mediaId", equalTo((int) cat))
                .body("leadImage.caption", equalTo("Our cat Minka"))
                .body("leadImage.width", equalTo(1600))
                .body("leadImage.height", equalTo(1067));
    }

    @Test
    void setReplaceAndRemoveOnADraft() {
        long id = create(content("Minka"));

        save(id, withImage("Minka", cat, "Our cat Minka")).statusCode(200)
                .body("leadImage.mediaId", equalTo((int) cat))
                .body("leadImage.caption", equalTo("Our cat Minka"))
                .body("leadImage.width", equalTo(1600))
                .body("leadImage.height", equalTo(1067));
        save(id, withImage("Minka", dog, "Our cat Minka")).statusCode(200)
                .body("leadImage.mediaId", equalTo((int) dog))
                .body("leadImage.width", equalTo(800))
                .body("leadImage.height", equalTo(1200));
        get(id).body("leadImage.mediaId", equalTo((int) dog)).body("revision", equalTo(1));

        save(id, content("Minka").putNull("leadImage")).statusCode(200).body("leadImage", nullValue());
        save(id, withImage("Minka", cat, null)).statusCode(200).body("leadImage.caption", equalTo(""));
        // a missing field removes the image as well (full replace)
        save(id, content("Minka")).statusCode(200).body("leadImage", nullValue());
        assertThat(revisions()).isEqualTo(1);
    }

    @Test
    void unknownMediaIsRefusedAndNothingSaved() {
        long id = create(withImage("Minka", cat, "Our cat Minka"));

        save(id, withImage("Changed", 999_999, "x")).statusCode(400)
                .body("errors.field", contains("leadImage.mediaId"));

        get(id).body("headline", equalTo("Minka")).body("leadImage.mediaId", equalTo((int) cat));
        as(publisher).body(withImage("New", 999_999, null).toString()).post("/api/articles").then().statusCode(400)
                .body("errors.field", contains("leadImage.mediaId"));
    }

    @Test
    void invalidLeadImageIsReportedWithOtherErrors() {
        long id = create(content("Minka"));
        ObjectNode content = content("a\nb");
        content.putObject("leadImage").put("mediaId", "x").put("caption", "c".repeat(301));

        save(id, content).statusCode(400)
                .body("errors.field", contains("headline", "leadImage.mediaId", "leadImage.caption"));
    }

    @Test
    void captionOnlyChangeOnAPublishedArticleCreatesARevision() {
        long id = create(withImage("Minka", cat, "Our cat Minka"));
        publish(id).statusCode(200);

        save(id, withImage("Minka", cat, "Minka sleeping")).statusCode(200)
                .body("revision", equalTo(2))
                .body("liveRevision", equalTo(1))
                .body("hasUnpublishedChanges", equalTo(true))
                .body("leadImage.caption", equalTo("Minka sleeping"));
    }

    @Test
    void unchangedSaveWithLeadImageCreatesNoRevision() {
        long id = create(withImage("Minka", cat, "Our cat Minka"));
        publish(id).statusCode(200);

        save(id, withImage("Minka", cat, " Our cat Minka ")).statusCode(200)
                .body("revision", equalTo(1))
                .body("hasUnpublishedChanges", equalTo(false));
        assertThat(revisions()).isEqualTo(1);
    }

    @Test
    void publishingMakesTheLeadImageLive() {
        long id = create(content("Minka"));
        publish(id).statusCode(200);
        save(id, withImage("Minka", cat, "Our cat Minka")).statusCode(200).body("revision", equalTo(2));

        as(publisher).get("/api/articles/" + id + "/revisions/1").then().statusCode(200)
                .body("live", equalTo(true)).body("leadImage", nullValue());

        publish(id).statusCode(200).body("liveRevision", equalTo(2)).body("leadImage.mediaId", equalTo((int) cat));
        as(publisher).get("/api/articles/" + id + "/revisions/2").then().statusCode(200)
                .body("live", equalTo(true)).body("leadImage.mediaId", equalTo((int) cat));
    }

    @Test
    void olderRevisionShowsItsOwnLeadImage() {
        long id = create(withImage("Minka", cat, "Our cat Minka"));
        publish(id).statusCode(200);
        save(id, withImage("Minka", dog, "Not a cat")).statusCode(200);

        as(publisher).get("/api/articles/" + id + "/revisions/1").then().statusCode(200)
                .body("leadImage.mediaId", equalTo((int) cat))
                .body("leadImage.caption", equalTo("Our cat Minka"))
                .body("leadImage.width", equalTo(1600))
                .body("leadImage.height", equalTo(1067));
        as(publisher).get("/api/articles/" + id + "/revisions/2").then().statusCode(200)
                .body("leadImage.mediaId", equalTo((int) dog))
                .body("leadImage.caption", equalTo("Not a cat"));
    }

    @Test
    void referencedMediaCannotBeDeleted() {
        create(withImage("Minka", cat, null));

        assertThatThrownBy(() -> sql("DELETE FROM media WHERE id = " + cat))
                .hasMessageContaining("article_revision");
    }
}
