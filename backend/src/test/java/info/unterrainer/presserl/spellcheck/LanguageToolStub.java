package info.unterrainer.presserl.spellcheck;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.common.TestResourceScope;
import io.quarkus.test.common.WithTestResource;

/**
 * Stands in for LanguageTool in every {@code @QuarkusTest}: a JDK {@link HttpServer} answering
 * {@code POST /v2/check} with canned LanguageTool JSON. Rules on the checked text:
 * <ul>
 * <li>{@code gros} is a misspelling (six replacements, of which the backend passes five),</li>
 * <li>{@code hund} is a casing error,</li>
 * <li>{@code sehr sehr} is a style finding the backend must drop,</li>
 * <li>a text containing {@code SLOW} is answered after six seconds, one containing {@code FAIL}
 * with {@code 500}.</li>
 * </ul>
 * Offsets are Java {@code char} indices, as LanguageTool reports them. {@code GET /stub/calls}
 * returns the number of checks received and {@code GET /stub/language} the last language, so tests
 * see them over HTTP regardless of class loaders.
 */
@WithTestResource(value = LanguageToolStub.class, scope = TestResourceScope.GLOBAL)
public class LanguageToolStub implements QuarkusTestResourceLifecycleManager {

    public static final String URL = "presserl.spell-check.url";

    private static final Pattern GROS = Pattern.compile("\\bgros\\b");
    private static final Pattern HUND = Pattern.compile("\\bhund\\b");
    private static final Pattern SEHR_SEHR = Pattern.compile("\\bsehr sehr\\b");

    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> language = new AtomicReference<>("");
    private HttpServer server;

    @Override
    public Map<String, String> start() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/v2/check", this::check);
        server.createContext("/stub/calls", exchange -> send(exchange, 200, String.valueOf(calls.get())));
        server.createContext("/stub/language", exchange -> send(exchange, 200, language.get()));
        server.start();
        return Map.of(URL, "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void check(HttpExchange exchange) throws IOException {
        calls.incrementAndGet();
        Map<String, String> form = form(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        String text = form.getOrDefault("text", "");
        language.set(form.getOrDefault("language", ""));
        if (text.contains("FAIL")) {
            send(exchange, 500, "internal error");
            return;
        }
        if (text.contains("SLOW")) {
            try {
                Thread.sleep(6_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        List<String> matches = new ArrayList<>();
        add(matches, text, GROS, "Möglicher Tippfehler gefunden.", "misspelling", "TYPOS",
                "\"groß\", \"Gros\", \"grob\", \"gro\", \"ros\", \"gross\"");
        add(matches, text, HUND, "Außer am Satzanfang werden nur Nomen und Eigennamen großgeschrieben.",
                "typographical", "CASING", "\"Hund\"");
        add(matches, text, SEHR_SEHR, "Möglicherweise eine Wortwiederholung.", "style", "STYLE", "\"sehr\"");
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        send(exchange, 200, "{\"matches\": [" + String.join(",", matches) + "]}");
    }

    private static void add(List<String> matches, String text, Pattern pattern, String message, String issueType,
            String category, String replacements) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String values = Pattern.compile("\"([^\"]*)\"").matcher(replacements).results()
                    .map(r -> "{\"value\": \"" + r.group(1) + "\"}").reduce((a, b) -> a + "," + b).orElse("");
            matches.add("{\"message\": \"" + message + "\", \"shortMessage\": \"\", \"offset\": " + matcher.start()
                    + ", \"length\": " + (matcher.end() - matcher.start()) + ", \"replacements\": [" + values
                    + "], \"rule\": {\"id\": \"STUB_" + category + "\", \"issueType\": \"" + issueType
                    + "\", \"category\": {\"id\": \"" + category + "\", \"name\": \"" + category + "\"}}}");
        }
    }

    private static Map<String, String> form(String body) {
        Map<String, String> values = new HashMap<>();
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                values.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return values;
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
