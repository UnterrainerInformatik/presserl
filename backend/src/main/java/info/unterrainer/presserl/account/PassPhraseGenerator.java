package info.unterrainer.presserl.account;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Generates default passwords: {@value #WORDS} words from the curated, kid-friendly German word
 * list {@value #RESOURCE}, chosen uniformly with a {@link SecureRandom} and joined by {@code -}.
 */
@ApplicationScoped
public class PassPhraseGenerator {

    static final String RESOURCE = "accounts/words-de.txt";
    static final int WORDS = 4;

    private final List<String> words = load();
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        return IntStream.range(0, WORDS)
                .mapToObj(i -> words.get(random.nextInt(words.size())))
                .collect(Collectors.joining("-"));
    }

    /**
     * The word list: one word per line, blank lines ignored.
     */
    static List<String> load() {
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(RESOURCE);
        if (in == null) {
            throw new IllegalStateException("Word list " + RESOURCE + " is missing");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return reader.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
