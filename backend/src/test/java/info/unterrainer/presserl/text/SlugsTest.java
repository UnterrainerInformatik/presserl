package info.unterrainer.presserl.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class SlugsTest {

    private static final int SECTION_MAX = 40;

    @Test
    void ampersandAndSpacesBecomeOneDash() {
        assertThat(Slugs.fold("Sport & Spiel", SECTION_MAX, "section")).isEqualTo("sport-spiel");
    }

    @Test
    void umlautsAreSpelledOut() {
        assertThat(Slugs.fold("Größte Tüten", SECTION_MAX, "section")).isEqualTo("groesste-tueten");
    }

    @Test
    void cutAtMaximumLengthWithoutTrailingDash() {
        assertThat(Slugs.fold("a".repeat(50), SECTION_MAX, "section")).isEqualTo("a".repeat(40));
        assertThat(Slugs.fold("a".repeat(39) + " bcd", SECTION_MAX, "section")).isEqualTo("a".repeat(39));
    }

    @Test
    void nothingUsableLeftIsTheFallback() {
        assertThat(Slugs.fold("!!!", SECTION_MAX, "section")).isEqualTo("section");
        assertThat(Slugs.fold("李", SECTION_MAX, "section")).isEqualTo("section");
    }

    @Test
    void suffixKeepsTheMaximumLength() {
        assertThat(Slugs.withSuffix("a".repeat(40), 2, SECTION_MAX)).isEqualTo("a".repeat(38) + "-2").hasSize(40);
    }

    @Test
    void firstFreeSkipsTakenSlugs() {
        assertThat(Slugs.firstFree("sport", SECTION_MAX, Set.of("sport")::contains)).isEqualTo("sport-2");
        assertThat(Slugs.firstFree("kultur", SECTION_MAX, Set.of("sport")::contains)).isEqualTo("kultur");
    }
}
