package info.unterrainer.presserl.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class UsernameDeriverTest {

    @Test
    void umlautAndSpace() {
        assertThat(UsernameDeriver.base("Jürgen Maria")).isEqualTo("juergen-maria");
    }

    @Test
    void germanSpecialLettersAreSpelledOut() {
        assertThat(UsernameDeriver.base("Ärmel Öde Übel Straße")).isEqualTo("aermel-oede-uebel-strasse");
    }

    @Test
    void decomposedUmlautIsSpelledOutToo() {
        assertThat(UsernameDeriver.base("Jürgen")).isEqualTo("juergen");
    }

    @Test
    void otherDiacriticsAreDropped() {
        assertThat(UsernameDeriver.base("Zoë")).isEqualTo("zoe");
        assertThat(UsernameDeriver.base("François Čapek")).isEqualTo("francois-capek");
    }

    @Test
    void runsOfOtherCharactersBecomeOneDashAndEdgesAreTrimmed() {
        assertThat(UsernameDeriver.base("  --Anna--Lena!! (2) ")).isEqualTo("anna-lena-2");
    }

    @Test
    void nothingUsableLeftBecomesUser() {
        assertThat(UsernameDeriver.base("李")).isEqualTo("user");
        assertThat(UsernameDeriver.base("!!!")).isEqualTo("user");
    }

    @Test
    void longNamesAreCutWithoutTrailingDash() {
        assertThat(UsernameDeriver.base("a".repeat(40))).isEqualTo("a".repeat(32));
        assertThat(UsernameDeriver.base("a".repeat(31) + " bcd")).isEqualTo("a".repeat(31));
    }

    @Test
    void suffixKeepsTheWholeWithinLimit() {
        assertThat(UsernameDeriver.withSuffix("anna", 1)).isEqualTo("anna");
        assertThat(UsernameDeriver.withSuffix("anna", 3)).isEqualTo("anna-3");
        assertThat(UsernameDeriver.withSuffix("a".repeat(32), 2)).isEqualTo("a".repeat(30) + "-2").hasSize(32);
        assertThat(UsernameDeriver.withSuffix("a".repeat(32), 10)).isEqualTo("a".repeat(29) + "-10").hasSize(32);
    }

    @Test
    void suffixTrimsDashLeftAtTheCut() {
        String base = "a".repeat(29) + "-bc";
        assertThat(UsernameDeriver.withSuffix(base, 2)).isEqualTo("a".repeat(29) + "-2");
    }

    @Test
    void firstFreeSkipsTakenNames() {
        Set<String> taken = Set.of("anna", "anna-2");
        assertThat(UsernameDeriver.firstFree("anna", taken::contains)).isEqualTo("anna-3");
        assertThat(UsernameDeriver.firstFree("lena", taken::contains)).isEqualTo("lena");
    }

    @Test
    void shortBaseIsNumberedFromOne() {
        assertThat(UsernameDeriver.firstFree("li", Set.<String>of()::contains)).isEqualTo("li-1");
        assertThat(UsernameDeriver.firstFree("li", Set.of("li-1")::contains)).isEqualTo("li-2");
        assertThat(UsernameDeriver.firstFree("a", Set.<String>of()::contains)).isEqualTo("a-1");
    }

    @Test
    void threeCharactersStayUnsuffixed() {
        assertThat(UsernameDeriver.firstFree("max", Set.<String>of()::contains)).isEqualTo("max");
    }
}
