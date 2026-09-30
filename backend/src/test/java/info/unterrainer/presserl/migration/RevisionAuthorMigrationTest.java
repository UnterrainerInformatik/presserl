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
 * V14 (revision author) on a separate schema: migrated to V13, prepared by SQL, then migrated to V14,
 * which attributes every existing revision to its article's author.
 */
@QuarkusTest
class RevisionAuthorMigrationTest {

    private static final String SCHEMA = "migration_v14_test";

    @Inject
    DataSource dataSource;

    @BeforeEach
    @AfterEach
    void dropSchema() {
        execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void attributesExistingRevisionsToTheArticleAuthor() {
        migrate("13");
        execute("INSERT INTO " + SCHEMA + ".section (id, name, slug, color, position, created_at) OVERRIDING SYSTEM VALUE "
                + "VALUES (1, 'Sport', 'sport', 'red', 0, now())");
        execute("INSERT INTO " + SCHEMA + ".article (id, status, author_sub, author_username, author_display_name, "
                + "section_id, created_at, updated_at) OVERRIDING SYSTEM VALUE VALUES "
                + "(1, 'DRAFT', 's1', 'reader', 'Reader', 1, now(), now()), "
                + "(2, 'DRAFT', 's2', 'chief', 'Chief', 1, now(), now())");
        execute("INSERT INTO " + SCHEMA + ".article_revision (article_id, number, body, created_at, updated_at) VALUES "
                + "(1, 1, '{\"version\": 1, \"blocks\": []}', now(), now()), "
                + "(1, 2, '{\"version\": 1, \"blocks\": []}', now(), now()), "
                + "(2, 1, '{\"version\": 1, \"blocks\": []}', now(), now())");

        migrate("14");

        assertThat(strings("SELECT article_id || '/' || number || ':' || author_sub || ',' || author_username || ','"
                + " || author_display_name FROM " + SCHEMA + ".article_revision ORDER BY article_id, number"))
                .containsExactly("1/1:s1,reader,Reader", "1/2:s1,reader,Reader", "2/1:s2,chief,Chief");
        assertThat(strings("SELECT column_name FROM information_schema.columns WHERE table_schema = '" + SCHEMA
                + "' AND table_name = 'article_revision' AND column_name LIKE 'author_%' AND is_nullable = 'NO' "
                + "ORDER BY column_name"))
                .containsExactly("author_display_name", "author_sub", "author_username");
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
