package info.unterrainer.presserl.spellcheck;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The part of LanguageTool's {@code /v2/check} answer the backend reads.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LanguageToolResponse(List<Match> matches) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Match(String message, int offset, int length, List<Replacement> replacements, Rule rule) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Replacement(String value) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Rule(String id, String issueType, Category category) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Category(String id) {
    }
}
