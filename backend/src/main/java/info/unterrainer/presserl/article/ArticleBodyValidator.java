package info.unterrainer.presserl.article;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import info.unterrainer.presserl.api.FieldError;

/**
 * Validates an article body against format version 1 and collects every violation with its path.
 * <pre>
 * {"version": 1, "blocks": [
 *   {"type": "paragraph", "content": [runs]},
 *   {"type": "subhead", "text": "..."},
 *   {"type": "quote", "content": [runs]},
 *   {"type": "list", "items": [[runs], ...]}]}
 * run = {"text": "...", "bold": true|false}   (bold optional, text non-empty, \n allowed)
 * </pre>
 * Text is never interpreted as markup; escaping is the reader's job.
 */
public final class ArticleBodyValidator {

    private static final Set<String> BODY_FIELDS = Set.of("version", "blocks");
    private static final Set<String> RUN_FIELDS = Set.of("text", "bold");
    private static final Map<String, Set<String>> BLOCK_FIELDS = Map.of(
            "paragraph", Set.of("type", "content"),
            "subhead", Set.of("type", "text"),
            "quote", Set.of("type", "content"),
            "list", Set.of("type", "items"));

    private final List<FieldError> errors = new ArrayList<>();
    private int textLength;

    private ArticleBodyValidator() {
    }

    /**
     * Returns all violations of {@code body}, found at {@code path}; empty when it is valid.
     */
    public static List<FieldError> validate(JsonNode body, String path) {
        ArticleBodyValidator validator = new ArticleBodyValidator();
        validator.body(body, path);
        return validator.errors;
    }

    private void body(JsonNode body, String path) {
        if (!body.isObject()) {
            error(path, "must be an object");
            return;
        }
        unknownFields(body, BODY_FIELDS, path);
        JsonNode version = body.get("version");
        if (version == null) {
            error(path + ".version", "is required");
        } else if (!version.isIntegralNumber() || version.asLong() != ArticleLimits.BODY_FORMAT_VERSION) {
            error(path + ".version", "must be " + ArticleLimits.BODY_FORMAT_VERSION);
        }
        JsonNode blocks = body.get("blocks");
        if (blocks == null) {
            error(path + ".blocks", "is required");
        } else if (!blocks.isArray()) {
            error(path + ".blocks", "must be an array");
        } else {
            if (blocks.size() > ArticleLimits.BODY_BLOCKS_MAX) {
                error(path + ".blocks", "must contain at most " + ArticleLimits.BODY_BLOCKS_MAX + " blocks");
            }
            for (int i = 0; i < blocks.size(); i++) {
                block(blocks.get(i), path + ".blocks[" + i + "]");
            }
        }
        if (textLength > ArticleLimits.BODY_TEXT_MAX) {
            error(path, "must contain at most " + ArticleLimits.BODY_TEXT_MAX + " characters of text");
        }
    }

    private void block(JsonNode block, String path) {
        if (!block.isObject()) {
            error(path, "must be an object");
            return;
        }
        JsonNode type = block.get("type");
        if (type == null) {
            error(path + ".type", "is required");
            return;
        }
        Set<String> fields = type.isTextual() ? BLOCK_FIELDS.get(type.asText()) : null;
        if (fields == null) {
            error(path + ".type", "unknown block type '" + type.asText() + "'");
            return;
        }
        unknownFields(block, fields, path);
        switch (type.asText()) {
            case "paragraph", "quote" -> runs(required(block, "content", path), path + ".content");
            case "subhead" -> subheadText(required(block, "text", path), path + ".text");
            case "list" -> items(required(block, "items", path), path + ".items");
            default -> throw new IllegalStateException(type.asText());
        }
    }

    private void items(JsonNode items, String path) {
        if (items == null) {
            return;
        }
        if (!items.isArray()) {
            error(path, "must be an array");
            return;
        }
        if (items.isEmpty()) {
            error(path, "must contain at least one item");
        }
        for (int i = 0; i < items.size(); i++) {
            runs(items.get(i), path + "[" + i + "]");
        }
    }

    private void runs(JsonNode runs, String path) {
        if (runs == null) {
            return;
        }
        if (!runs.isArray()) {
            error(path, "must be an array");
            return;
        }
        for (int i = 0; i < runs.size(); i++) {
            run(runs.get(i), path + "[" + i + "]");
        }
    }

    private void run(JsonNode run, String path) {
        if (!run.isObject()) {
            error(path, "must be an object");
            return;
        }
        unknownFields(run, RUN_FIELDS, path);
        JsonNode text = required(run, "text", path);
        if (text != null) {
            if (!text.isTextual()) {
                error(path + ".text", "must be a string");
            } else if (text.asText().isEmpty()) {
                error(path + ".text", "must not be empty");
            } else {
                text(text.asText(), path + ".text", true);
            }
        }
        JsonNode bold = run.get("bold");
        if (bold != null && !bold.isBoolean()) {
            error(path + ".bold", "must be true or false");
        }
    }

    private void subheadText(JsonNode text, String path) {
        if (text == null) {
            return;
        }
        if (!text.isTextual()) {
            error(path, "must be a string");
            return;
        }
        text(text.asText(), path, false);
    }

    private void text(String text, String path, boolean allowLineFeed) {
        textLength += TextRules.length(text);
        if (TextRules.hasControlCharacter(text, allowLineFeed)) {
            error(path, allowLineFeed ? "must not contain control characters other than line feed"
                    : "must not contain control characters");
        }
    }

    private JsonNode required(JsonNode node, String field, String path) {
        JsonNode value = node.get(field);
        if (value == null) {
            error(path + "." + field, "is required");
        }
        return value;
    }

    private void unknownFields(JsonNode node, Set<String> allowed, String path) {
        for (Iterator<String> names = node.fieldNames(); names.hasNext();) {
            String name = names.next();
            if (!allowed.contains(name)) {
                error(path + "." + name, "unknown field");
            }
        }
    }

    private void error(String path, String message) {
        errors.add(new FieldError(path, message));
    }
}
