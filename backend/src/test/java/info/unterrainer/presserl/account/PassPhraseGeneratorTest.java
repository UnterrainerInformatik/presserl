package info.unterrainer.presserl.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class PassPhraseGeneratorTest {

    private static final String PASSWORD = "^[a-z]{3,8}(-[a-z]{3,8}){3}$";

    private final List<String> words = PassPhraseGenerator.load();

    @Test
    void wordListHasAtLeastThousandWords() {
        assertThat(words).hasSizeGreaterThanOrEqualTo(1000);
    }

    @Test
    void wordListEntriesAreLowerCaseAsciiLettersOfThreeToEight() {
        assertThat(words).allSatisfy(word -> assertThat(word).matches("[a-z]{3,8}"));
    }

    @Test
    void wordListEntriesAreDistinct() {
        assertThat(words).doesNotHaveDuplicates();
    }

    @Test
    void passwordIsFourWordsFromTheList() {
        Set<String> list = new HashSet<>(words);
        String password = new PassPhraseGenerator().generate();

        assertThat(password).matches(PASSWORD);
        assertThat(password.split("-")).hasSize(4).allSatisfy(word -> assertThat(list).contains(word));
    }

    @Test
    void twoPasswordsDiffer() {
        PassPhraseGenerator generator = new PassPhraseGenerator();

        assertThat(generator.generate()).isNotEqualTo(generator.generate());
    }
}
