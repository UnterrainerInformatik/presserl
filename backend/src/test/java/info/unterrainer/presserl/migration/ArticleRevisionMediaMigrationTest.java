package info.unterrainer.presserl.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * V13 (revision-to-media table) on a separate schema: migrated to V12, prepared by SQL, then migrated
 * to V13, which backfills the table from the lead images.
 */
@QuarkusTest
class ArticleRevisionMediaMigrationTest {

    private static final String SCHEMA = "migration_v13_test";

    @Inject
    DataSource dataSource;

    @BeforeEach
    @AfterEach
    void dropSchema() {
        execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void backfillsTheLeadImages() {
        migrate("12");
        execute("INSERT INTO " + SCHEMA + ".section (id, name, slug, color, position, created_at) OVERRIDING SYSTEM VALUE "
                + "VALUES (1, 'Sport', 'sport', 'red', 0, now())");
        execute("INSERT INTO " + SCHEMA + ".media (id, object_key, content_type, width, height, byte_size, uploader_sub, "
                + "uploader_username, uploader_display_name, created_at) OVERRIDING SYSTEM VALUE VALUES "
                + "(7, 'media/a.jpg', 'image/jpeg', 10, 10, 1, 's', 'u', 'U', now()), "
                + "(8, 'media/b.jpg', 'image/jpeg', 10, 10, 1, 's', 'u', 'U', now())");
        execute("INSERT INTO " + SCHEMA + ".article (id, status, author_sub, author_username, author_display_name, "
                + "section_id, created_at, updated_at) OVERRIDING SYSTEM VALUE VALUES "
                + "(1, 'DRAFT', 's', 'u', 'U', 1, now(), now()), (2, 'DRAFT', 's', 'u', 'U', 1, now(), now())");
        execute("INSERT INTO " + SCHEMA + ".article_revision (article_id, number, body, lead_image_media_id, "
                + "created_at, updated_at) VALUES "
                + "(1, 1, '{\"version\": 1, \"blocks\": []}', 7, now(), now()), "
                + "(1, 2, '{\"version\": 1, \"blocks\": []}', 8, now(), now()), "
                + "(2, 1, '{\"version\": 1, \"blocks\": []}', NULL, now(), now())");

        migrate("13");

        assertThat(strings("SELECT article_id || '/' || number || ':' || media_id FROM " + SCHEMA
                + ".article_revision_media ORDER BY article_id, number")).containsExactly("1/1:7", "1/2:8");
    }

    private void migrate(String target) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }

    private void execute(String sql) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> strings(String sql) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            List<String> values = new ArrayList<>();
            while (result.next()) {
                values.add(result.getString(1));
            }
            return values;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
