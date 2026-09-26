package info.unterrainer.presserl.reader;

import java.util.ArrayList;
import java.util.List;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Turns a body in format version 1 into typed blocks for the article template. The template owns
 * all markup and escapes every text; nothing here produces HTML.
 */
public final class BodyRenderer {

    private static final Logger LOG = Logger.getLogger(BodyRenderer.class);

    public enum Type {
        PARAGRAPH,
        SUBHEAD,
        QUOTE,
        LIST
    }

    /**
     * A text run; {@code lines} are its text split at line feeds.
     */
    public record Run(boolean bold, List<String> lines) {
    }

    /**
     * One block. {@code text} is set for subheads, {@code runs} for paragraphs and quotes,
     * {@code items} for lists; the other fields are empty.
     */
    public record Block(Type type, String text, List<Run> runs, List<List<Run>> items) {

        static Block paragraph(List<Run> runs) {
            return new Block(Type.PARAGRAPH, "", runs, List.of());
        }

        static Block subhead(String text) {
            return new Block(Type.SUBHEAD, text, List.of(), List.of());
        }

        static Block quote(List<Run> runs) {
            return new Block(Type.QUOTE, "", runs, List.of());
        }

        static Block list(List<List<Run>> items) {
            return new Block(Type.LIST, "", List.of(), items);
        }
    }

    private BodyRenderer() {
    }

    /**
     * The blocks of {@code body}; unknown block types are skipped and logged.
     */
    public static List<Block> blocks(JsonNode body) {
        List<Block> blocks = new ArrayList<>();
        if (body == null) {
            return blocks;
        }
        for (JsonNode block : body.path("blocks")) {
            String type = block.path("type").asText();
            switch (type) {
                case "paragraph" -> blocks.add(Block.paragraph(runs(block.path("content"))));
                case "subhead" -> blocks.add(Block.subhead(block.path("text").asText()));
                case "quote" -> blocks.add(Block.quote(runs(block.path("content"))));
                case "list" -> {
                    List<List<Run>> items = new ArrayList<>();
                    for (JsonNode item : block.path("items")) {
                        items.add(runs(item));
                    }
                    blocks.add(Block.list(items));
                }
                default -> LOG.warnf("Skipping body block of unknown type '%s'", type);
            }
        }
        return blocks;
    }

    private static List<Run> runs(JsonNode runs) {
        List<Run> result = new ArrayList<>();
        for (JsonNode run : runs) {
            result.add(new Run(run.path("bold").asBoolean(false), List.of(run.path("text").asText().split("\n", -1))));
        }
        return result;
    }
}
