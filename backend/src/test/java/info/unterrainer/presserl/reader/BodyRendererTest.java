package info.unterrainer.presserl.reader;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import info.unterrainer.presserl.reader.BodyRenderer.Block;
import info.unterrainer.presserl.reader.BodyRenderer.Run;
import info.unterrainer.presserl.reader.BodyRenderer.Type;

class BodyRendererTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode body(String blocks) {
        try {
            return MAPPER.readTree(("{'version': 1, 'blocks': [" + blocks + "]}").replace('\'', '"'));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Test
    void paragraphWithBoldRun() {
        List<Block> blocks = BodyRenderer.blocks(body(
                "{'type': 'paragraph', 'content': [{'text': 'It started '}, {'text': 'in May', 'bold': true}]}"));

        assertThat(blocks).containsExactly(Block.paragraph(List.of(
                new Run(false, List.of("It started ")),
                new Run(true, List.of("in May")))));
    }

    @Test
    void subhead() {
        assertThat(BodyRenderer.blocks(body("{'type': 'subhead', 'text': 'Watering'}")))
                .containsExactly(Block.subhead("Watering"));
    }

    @Test
    void quote() {
        assertThat(BodyRenderer.blocks(body("{'type': 'quote', 'content': [{'text': 'Every day!', 'bold': false}]}")))
                .containsExactly(Block.quote(List.of(new Run(false, List.of("Every day!")))));
    }

    @Test
    void bulletList() {
        assertThat(BodyRenderer.blocks(body("{'type': 'list', 'items': [[{'text': 'Water'}], [{'text': 'Sun'}]]}")))
                .containsExactly(Block.list(List.of(
                        List.of(new Run(false, List.of("Water"))),
                        List.of(new Run(false, List.of("Sun"))))));
    }

    @Test
    void lineFeedsSplitARunIntoLines() {
        Block block = BodyRenderer.blocks(body("{'type': 'paragraph', 'content': [{'text': 'one\\ntwo\\n'}]}")).get(0);

        assertThat(block.runs()).containsExactly(new Run(false, List.of("one", "two", "")));
    }

    @Test
    void blocksKeepTheirOrder() {
        List<Block> blocks = BodyRenderer.blocks(body("""
                {'type': 'subhead', 'text': 'A'},
                {'type': 'paragraph', 'content': [{'text': 'B'}]},
                {'type': 'list', 'items': [[{'text': 'C'}]]},
                {'type': 'quote', 'content': [{'text': 'D'}]}"""));

        assertThat(blocks).extracting(Block::type)
                .containsExactly(Type.SUBHEAD, Type.PARAGRAPH, Type.LIST, Type.QUOTE);
    }

    @Test
    void emptyBody() {
        assertThat(BodyRenderer.blocks(body(""))).isEmpty();
        assertThat(BodyRenderer.blocks(null)).isEmpty();
    }

    @Test
    void unknownTypeIsSkipped() {
        List<Block> blocks = BodyRenderer.blocks(body(
                "{'type': 'image', 'src': 'x.png'}, {'type': 'subhead', 'text': 'Kept'}"));

        assertThat(blocks).containsExactly(Block.subhead("Kept"));
    }
}
