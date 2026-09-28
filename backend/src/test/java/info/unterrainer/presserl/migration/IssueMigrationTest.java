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
 * V11 (issues) on a separate schema: migrated to V10, prepared by SQL, then migrated to V11.
 */
@QuarkusTest
class IssueMigrationTest {

    private static final String SCHEMA = "migration_v11_test";

    @Inject
    DataSource dataSource;

    @BeforeEach
    @AfterEach
    void dropSchema() {
        execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void publishedArticlesMoveIntoIssueOneInOrderOfFirstPublication() {
        migrate("10");
        execute("INSERT INTO " + SCHEMA + ".section (name, slug, color, position, created_at) VALUES "
                + "('Sport', 'sport', 'red', 0, now())");
        // B was published before A; C never; D offline after publication; E and F tie (by id)
        insertArticle("A", "'PUBLISHED'", "'2026-05-02T10:00:00Z'");
        insertArticle("B", "'PUBLISHED'", "'2026-05-01T10:00:00Z'");
        insertArticle("C", "'DRAFT'", "NULL");
        insertArticle("D", "'OFFLINE'", "'2026-05-03T10:00:00Z'");
        insertArticle("E", "'PUBLISHED'", "'2026-05-04T10:00:00Z'");
        insertArticle("F", "'PUBLISHED'", "'2026-05-04T10:00:00Z'");

        migrate("11");

        assertThat(strings("SELECT number || '/' || published || '/' || coalesce(publication_date::text, '-') FROM "
                + SCHEMA + ".issue")).containsExactly("1/false/-");
        assertThat(strings("SELECT a.author_username || '@' || a.issue_position FROM " + SCHEMA + ".article a JOIN "
                + SCHEMA + ".issue i ON i.id = a.issue_id WHERE i.number = 1 ORDER BY a.issue_position"))
                .containsExactly("B@0", "A@1", "D@2", "E@3", "F@4");
        assertThat(strings("SELECT author_username FROM " + SCHEMA + ".article WHERE issue_id IS NULL"))
                .containsExactly("C");
    }

    @Test
    void freshInstallationGetsAnEmptyIssueOne() {
        migrate("11");

        assertThat(strings("SELECT number || '/' || published FROM " + SCHEMA + ".issue")).containsExactly("1/false");
        assertThat(strings("SELECT count(*) FROM " + SCHEMA + ".article")).containsExactly("0");
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

    /**
     * An article whose author username is {@code name}, so rows can be told apart.
     */
    private void insertArticle(String name, String status, String publishedAt) {
        execute("INSERT INTO " + SCHEMA + ".article (status, author_sub, author_username, author_display_name, "
                + "section_id, published_at, created_at, updated_at) SELECT " + status + ", 'sub-" + name + "', '" + name
                + "', '" + name + "', id, " + publishedAt + ", now(), now() FROM " + SCHEMA + ".section");
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
