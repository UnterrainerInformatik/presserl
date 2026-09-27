package info.unterrainer.presserl.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

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

import info.unterrainer.presserl.TestSupport;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * The default-section bootstrap, run directly after the database was prepared by SQL.
 */
@QuarkusTest
class DefaultSectionBootstrapTest {

    @Inject
    DataSource dataSource;

    @Inject
    DefaultSectionBootstrap bootstrap;

    @BeforeEach
    @AfterEach
    void clean() {
        TestSupport.deleteSections(dataSource);
    }

    @Test
    void filesSectionlessArticlesUnderANewDefaultSection() throws Throwable {
        execute("INSERT INTO section (name, slug, color, position, created_at) VALUES ('Sport', 'sport', 'red', 0, now())");
        insertArticle();
        insertArticle();

        assertThat(bootstrap.run()).isEqualTo(2);

        assertThat(strings("SELECT name FROM section ORDER BY position")).containsExactly("Sport", "General");
        assertThat(strings("SELECT s.name FROM article a LEFT JOIN section s ON s.id = a.section_id"))
                .containsExactly("General", "General");
    }

    @Test
    void createsTheDefaultSectionWhenNoSectionExists() throws Throwable {
        assertThat(bootstrap.run()).isZero();
        assertThat(strings("SELECT name FROM section")).containsExactly("General");
    }

    @Test
    void doesNothingWhenSectionsExistAndEveryArticleHasOne() throws Throwable {
        execute("INSERT INTO section (name, slug, color, position, created_at) VALUES ('Sport', 'sport', 'red', 0, now())");

        assertThat(bootstrap.run()).isEqualTo(-1);

        assertThat(strings("SELECT name FROM section")).containsExactly("Sport");
    }

    private void insertArticle() {
        execute("INSERT INTO article (status, author_sub, author_username, author_display_name, created_at, updated_at) "
                + "VALUES ('DRAFT', 'sub-old', 'old', 'Old', now(), now())");
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
