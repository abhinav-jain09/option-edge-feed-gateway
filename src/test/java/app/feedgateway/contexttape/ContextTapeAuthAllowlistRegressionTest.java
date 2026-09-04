package app.feedgateway.contexttape;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * {@code /api/context-tape/session} is a DATA endpoint and must stay behind the same bearer guard as
 * every other one — only scripts and static assets are ever public. Mirrors
 * {@code StockGexAuthAllowlistRegressionTest}: this gateway has NO Spring Security filter chain (see
 * the pom comment — adding servlet security would lock down every endpoint behind a generated
 * password), so "public" here means "does not call the shared verifier", and the invariant is asserted
 * at the source level.
 *
 * <p>The behavioural counterpart — a missing/invalid token → 401 and 403, before the upstream is
 * called at all — lives in {@link ContextTapeControllerTest#authIsEnforcedBeforeTheUpstreamIsEvenCalled()}.
 */
class ContextTapeAuthAllowlistRegressionTest {

    private static final Path MAIN = Path.of("src/main/java");

    @Test
    void theHandlerInvokesTheSharedAuthVerifier() {
        String controller = read(MAIN.resolve("app/feedgateway/contexttape/ContextTapeController.java"));
        assertTrue(controller.contains("LiquidityHistoryAuth"),
                "auth must be the SAME shared bean that protects the other /api endpoints, not a re-implementation");
        int calls = controller.split("auth\\.authenticate\\(", -1).length - 1;
        assertTrue(calls >= 1, "the session handler must authenticate; found " + calls);
    }

    @Test
    void contextTapeIsNotOnAnyPublicAllowlist() {
        try (Stream<Path> paths = Files.walk(MAIN)) {
            List<Path> offenders = paths
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        String src = read(p);
                        boolean mentionsContextTape = src.contains("/api/context-tape");
                        boolean hasAllowlistWord = src.contains("permitAll")
                                || src.contains("requestMatchers")
                                || src.contains("antMatchers")
                                || src.contains("PUBLIC_PATHS");
                        return mentionsContextTape && hasAllowlistWord;
                    })
                    .toList();
            assertTrue(offenders.isEmpty(),
                    "/api/context-tape/* must never be on a public allowlist; offenders: " + offenders);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
