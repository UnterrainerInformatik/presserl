package info.unterrainer.presserl.reader;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class LoginTargetTest {

    @ParameterizedTest
    @ValueSource(strings = { "/", "/articles/7", "/articles/a%20b", "/?x=1", "/articles/7#top" })
    void acceptsSameOriginPaths(String next) {
        assertThat(LoginTarget.of(next)).isEqualTo(next);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "//evil.example/x", "/\\evil.example/x", "https://evil.example/x", "articles/7",
            "javascript:alert(1)", "/articles/7\r\nSet-Cookie: x=1", "/a\tb", "/a\u007fb", "/a b", "/a|b" })
    void fallsBackToTheFrontPage(String next) {
        assertThat(LoginTarget.of(next)).isEqualTo("/");
    }

    @Test
    void loginForArticleEncodesTheId() {
        assertThat(LoginTarget.loginForArticle("7")).isEqualTo("/login?next=/articles/7");
        assertThat(LoginTarget.loginForArticle("a b")).isEqualTo("/login?next=/articles/a%2520b");
        assertThat(LoginTarget.loginForArticle("x?y&z")).isEqualTo("/login?next=/articles/x%253Fy%2526z");
    }
}
