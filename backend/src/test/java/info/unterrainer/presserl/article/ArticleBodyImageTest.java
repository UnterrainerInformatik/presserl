package info.unterrainer.presserl.article;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import info.unterrainer.presserl.TestSupport;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;

/**
 * Image blocks in article bodies: the existence check on save and the revision-to-media table the
 * database trigger maintains. Media rows are inserted directly; articles, sections and media are
 * deleted before and after every test.
 */
@QuarkusTest
class ArticleBodyImageTest {

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
        cat = media();
        dog = media();
    }

    @AfterEach
    void cleanUp() {
        TestSupport.deleteSections(dataSource);
        sql("DELETE FROM media");
    }

    private long media() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("""
                        INSERT INTO media (object_key, content_type, width, height, byte_size, uploader_sub,
                            uploader_username, uploader_display_name, created_at)
                        VALUES ('media/' || gen_random_uuid() || '.jpg', 'image/jpeg', 1600, 1067, 1000, 'sub',
                            'publisher', 'Publisher', now()) RETURNING id""")) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * The table's rows as {@code "number:mediaId"}, ordered.
     */
    private List<String> used(long articleId) {
        List<String> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT number, media_id FROM article_revision_media "
                        + "WHERE article_id = " + articleId + " ORDER BY number, media_id")) {
            while (rs.next()) {
                rows.add(rs.getInt(1) + ":" + rs.getLong(2));
            }
            return rows;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private long count(String table) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT count(*) FROM " + table)) {
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

    /**
     * Content with {@code headline} and a body of the given blocks: a {@code Long} is an image block
     * without caption, a {@code String} a paragraph.
     */
    private static ObjectNode content(String headline, Object... blocks) {
        ObjectNode content = MAPPER.createObjectNode().put("headline", headline);
        ObjectNode body = content.putObject("body").put("version", 1);
        ArrayNode array = body.putArray("blocks");
        for (Object block : blocks) {
            if (block instanceof Long mediaId) {
                array.addObject().put("type", "image").put("mediaId", mediaId);
            } else {
                array.addObject().put("type", "paragraph").putArray("content").addObject().put("text", (String) block);
            }
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
    void bodyWithImageBlockIsReturnedUnchanged() throws Exception {
        ObjectNode content = content("Race", "Start");
        ((ArrayNode) content.at("/body/blocks")).addObject().put("type", "image").put("mediaId", cat)
                .put("caption", "The finish line");
        ((ArrayNode) content.at("/body/blocks")).addObject().put("type", "image").put("mediaId", dog);
        ((ArrayNode) content.at("/body/blocks")).addObject().put("type", "paragraph").putArray("content")
                .addObject().put("text", "End");
        long id = create(content);

        String response = get(id).statusCode(200).extract().asString();
        // reparsed so that numbers compare as the same node type
        assertThat(MAPPER.readTree(response).get("body")).isEqualTo(MAPPER.readTree(content.get("body").toString()));
    }

    @Test
    void unknownMediaInTheBodyIsRefusedAndNothingSaved() {
        long id = create(content("Race", "Start"));

        save(id, content("Changed", "Start", 999_999L, cat, 999_998L)).statusCode(400)
                .body("errors.field", contains("body.blocks[1].mediaId", "body.blocks[3].mediaId"));

        get(id).body("headline", equalTo("Race")).body("body.blocks.size()", equalTo(1));
        assertThat(count("article_revision")).isEqualTo(1);
        as(publisher).body(content("New", 999_999L).toString()).post("/api/articles").then().statusCode(400)
                .body("errors.field", contains("body.blocks[0].mediaId"));
    }

    @Test
    void unknownLeadImageAndBodyMediaAreReportedTogether() {
        ObjectNode content = content("Race", "Start", 999_999L);
        content.putObject("leadImage").put("mediaId", 999_999L);

        as(publisher).body(content.toString()).post("/api/articles").then().statusCode(400)
                .body("errors.field", contains("leadImage.mediaId", "body.blocks[1].mediaId"));
    }

    @Test
    void sameImageAsLeadImageAndTwiceInTheBody() {
        ObjectNode content = content("Race", cat, "Start", cat);
        content.putObject("leadImage").put("mediaId", cat);

        long id = create(content);
        assertThat(used(id)).containsExactly("1:" + cat);
        save(id, content.deepCopy().put("headline", "Race 2")).statusCode(200);
    }

    @Test
    void newBodyImageStaysHiddenUntilPublication() {
        long id = create(content("Race", "Start"));
        publish(id).statusCode(200);

        save(id, content("Race", "Start", dog)).statusCode(200)
                .body("revision", equalTo(2))
                .body("liveRevision", equalTo(1))
                .body("hasUnpublishedChanges", equalTo(true))
                .body("body.blocks[1].mediaId", equalTo((int) dog));

        as(publisher).get("/api/articles/" + id + "/revisions/1").then().statusCode(200)
                .body("live", equalTo(true)).body("body.blocks.size()", equalTo(1));
        assertThat(used(id)).containsExactly("2:" + dog);
    }

    @Test
    void tableFollowsTheRevisions() {
        ObjectNode withLead = content("Race", "Start", dog);
        withLead.putObject("leadImage").put("mediaId", cat);
        long id = create(withLead);
        assertThat(used(id)).containsExactlyInAnyOrder("1:" + cat, "1:" + dog);

        // saved in place: the image block is removed
        save(id, content("Race", "Start")).statusCode(200).body("revision", equalTo(1));
        assertThat(used(id)).isEmpty();

        save(id, content("Race", "Start", cat)).statusCode(200).body("revision", equalTo(1));
        assertThat(used(id)).containsExactly("1:" + cat);

        // saved as a new revision: the older one keeps its rows
        publish(id).statusCode(200);
        save(id, content("Race", dog, "Start")).statusCode(200).body("revision", equalTo(2));
        assertThat(used(id)).containsExactly("1:" + cat, "2:" + dog);
    }

    @Test
    void rowsGoWithTheArticle() {
        long id = create(content("Race", cat, dog));
        assertThat(used(id)).hasSize(2);

        as(publisher).delete("/api/articles/" + id).then().statusCode(204);
        assertThat(count("article_revision_media")).isZero();
    }

    @Test
    void mediaUsedInABodyCannotBeDeleted() {
        create(content("Race", dog));

        assertThatThrownBy(() -> sql("DELETE FROM media WHERE id = " + dog))
                .hasMessageContaining("article_revision_media");
    }
}
