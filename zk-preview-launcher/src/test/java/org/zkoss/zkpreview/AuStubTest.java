package org.zkoss.zkpreview;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.zkoss.zkpreview.testutil.Variants;
import org.zkoss.zkpreview.testutil.ZkClasspathResolver;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The preview is a one-shot render with no live desktop, so any interaction
 * (expanding a tree node, sorting a grid, paging a listbox) fires an AU
 * ({@code POST /zkau}) request the server cannot fulfil. The ZK client engine
 * {@code JSON.parse()}s the AU response ({@code zAu.pushReqCmds}), so the stub
 * must be a valid <b>empty AU envelope</b> — a JSON object with an empty
 * {@code rs} command list — not XML. The old {@code "<content/>"} stub started
 * with {@code '<'} and produced the browser error <i>"The response could not be
 * parsed: Expected JSON format ... Unexpected token '&lt;'"</i>. With a valid
 * empty envelope the client processes zero commands and the interaction is an
 * inert no-op (no error dialog).
 */
class AuStubTest {

    private static final Path FIXTURES = Paths.get("src/test/resources/fixtures");
    private static final Pattern EMPTY_RS = Pattern.compile("\"rs\"\\s*:\\s*\\[\\s*\\]");

    static Stream<Variants.Named> variants() {
        return Variants.both();
    }

    @ParameterizedTest(name = "AU stub is a valid empty JSON envelope [{0}]")
    @MethodSource("variants")
    void auStubIsValidEmptyJsonEnvelopeNotXml(Variants.Named variant) throws Exception {
        ZkClasspathResolver.Resolution res = variant.resolve();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);

        RenderEngine engine = RenderEngineFactory.create(res.jars, FIXTURES, null);
        PreviewHttpServer server = new PreviewHttpServer(engine, 0);
        server.start();
        try {
            int port = server.getPort();
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            // Mirror what the browser sends when expanding a tree node / interacting.
            HttpResponse<String> resp = client.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + port + "/zkau"))
                            .timeout(Duration.ofSeconds(10))
                            .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                            .POST(HttpRequest.BodyPublishers.ofString("dtid=z_dt&cmd_0=onOpen&uuid_0=z_1"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            assertEquals(200, resp.statusCode(), () -> "AU POST must succeed: " + resp.body());
            String body = resp.body().trim();
            // Regression guard: the client JSON.parse()es this; a leading '<' is the exact
            // "Unexpected token '<'" the user saw.
            assertFalse(body.startsWith("<"),
                    () -> "AU stub must not be XML/HTML — the ZK client parses it as JSON: " + body);
            // Must be a JSON object carrying an empty command list => the client runs zero
            // commands (a clean no-op interaction), never an error dialog.
            assertTrue(body.startsWith("{"), () -> "AU stub must be a JSON object envelope: " + body);
            assertTrue(EMPTY_RS.matcher(body).find(),
                    () -> "AU stub must carry an empty \"rs\" command list: " + body);
        } finally {
            server.stop();
            engine.close();
        }
    }
}
