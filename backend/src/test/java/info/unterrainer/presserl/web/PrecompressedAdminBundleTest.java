package info.unterrainer.presserl.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PrecompressedAdminBundleTest {

    @ParameterizedTest
    @ValueSource(strings = { "br", "gzip, deflate, br", "gzip, deflate, br, zstd", "BR", "br;q=0.5", "gzip;q=1, br ; q=1.0" })
    void brotliIsAccepted(String acceptEncoding) {
        assertThat(PrecompressedAdminBundle.acceptsBrotli(acceptEncoding)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "gzip, deflate", "identity", "br;q=0", "br;q=0.0", "br;q=x", "brotli" })
    void brotliIsNotAccepted(String acceptEncoding) {
        assertThat(PrecompressedAdminBundle.acceptsBrotli(acceptEncoding)).isFalse();
    }

    @Test
    void missingHeaderDoesNotAcceptBrotli() {
        assertThat(PrecompressedAdminBundle.acceptsBrotli(null)).isFalse();
    }

    @Test
    void onlyAdminWasmAndScriptsHaveVariants() {
        assertThat(PrecompressedAdminBundle.contentType("/admin/0123.wasm")).isEqualTo("application/wasm");
        assertThat(PrecompressedAdminBundle.contentType("/admin/composeApp.js")).isEqualTo("text/javascript;charset=UTF-8");
        assertThat(PrecompressedAdminBundle.contentType("/admin/styles.css")).isNull();
        assertThat(PrecompressedAdminBundle.contentType("/admin/composeApp.js.br")).isNull();
        assertThat(PrecompressedAdminBundle.contentType("/reader.js")).isNull();
    }
}
