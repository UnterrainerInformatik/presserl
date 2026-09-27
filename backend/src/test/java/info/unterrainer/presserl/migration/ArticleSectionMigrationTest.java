package info.unterrainer.presserl.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * V5 (article section {@code NOT NULL}) on a separate schema: migrated to V4, prepared by SQL, then
 * migrated to V5.
 */
@QuarkusTest
class ArticleSectionMigrationTest {

    private static final String SCHEMA = "migration_v5_test";

    @Inject
    DataSource dataSource;

    @BeforeEach
    @AfterEach
    void dropSchema() {
        execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void filesUnfiledArticlesUnderTheFirstSectionByPosition() {
        migrate("4");
        execute("INSERT INTO " + SCHEMA + ".section (name, slug, color, position, created_at) VALUES "
                + "('Kultur', 'kultur', 'blue', 1, now()), ('Sport', 'sport', 'red', 0, now())");
        insertArticle();

        migrate("5");

        assertThat(strings("SELECT s.name FROM " + SCHEMA + ".article a JOIN " + SCHEMA
                + ".section s ON s.id = a.section_id")).containsExactly("Sport");
        assertThat(strings("SELECT name FROM " + SCHEMA + ".section ORDER BY position")).containsExactly("Sport",
                "Kultur");
    }

    @Test
    void createsGeneralWhenUnfiledArticlesExistButNoSection() {
        migrate("4");
        insertArticle();

        migrate("5");

        assertThat(strings("SELECT s.name || '/' || s.slug || '/' || s.color || '/' || s.position FROM " + SCHEMA
                + ".article a JOIN " + SCHEMA + ".section s ON s.id = a.section_id"))
                .containsExactly("General/general/red/0");
    }

    @Test
    void createsNoSectionWithoutArticles() {
        migrate("5");

        assertThat(strings("SELECT name FROM " + SCHEMA + ".section")).isEmpty();
    }

    @Test
    void rejectsArticlesWithoutSectionAfterwards() {
        migrate("5");

        assertThatThrownBy(this::insertArticle).hasRootCauseInstanceOf(SQLException.class)
                .rootCause().hasMessageContaining("section_id");
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

    private void insertArticle() {
        execute("INSERT INTO " + SCHEMA + ".article (status, author_sub, author_username, author_display_name, "
                + "created_at, updated_at) VALUES ('DRAFT', 'sub-old', 'old', 'Old', now(), now())");
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
