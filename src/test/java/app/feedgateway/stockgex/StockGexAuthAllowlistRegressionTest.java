package app.feedgateway.stockgex;

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
 * {@code /api/stock-gex/*} are DATA endpoints and must stay behind the same bearer guard as every other
 * one — only scripts and static assets are ever public. Mirrors {@code PinFlowAuthAllowlistRegressionTest}:
 * this gateway has NO Spring Security filter chain (see the pom comment — adding servlet security would
 * lock down every endpoint behind a generated password), so "public" here means "does not call the shared
 * verifier", and the invariant is asserted at the source level.
 *
 * <p>The behavioural counterpart — a missing/invalid token → 401 and 403, before the upstream is called at
 * all — lives in {@link StockGexControllerTest#authIsEnforcedBeforeTheUpstreamIsEvenCalled()}.
 */
class StockGexAuthAllowlistRegressionTest {

    private static final Path MAIN = Path.of("src/main/java");

    @Test
    void bothHandlersInvokeTheSharedAuthVerifier() {
        String controller = read(MAIN.resolve("app/feedgateway/stockgex/StockGexController.java"));
        assertTrue(controller.contains("LiquidityHistoryAuth"),
                "auth must be the SAME shared bean that protects the other /api endpoints, not a re-implementation");
        int calls = controller.split("auth\\.authenticate\\(", -1).length - 1;
        assertTrue(calls >= 2, "BOTH the board and the stream handler must authenticate; found " + calls);
    }

    @Test
    void stockGexIsNotOnAnyPublicAllowlist() {
        try (Stream<Path> paths = Files.walk(MAIN)) {
            List<Path> offenders = paths
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        String src = read(p);
                        boolean mentionsStockGex = src.contains("/api/stock-gex");
                        boolean hasAllowlistWord = src.contains("permitAll")
                                || src.contains("requestMatchers")
                                || src.contains("antMatchers")
                                || src.contains("PUBLIC_PATHS");
                        return mentionsStockGex && hasAllowlistWord;
                    })
                    .toList();
            assertTrue(offenders.isEmpty(),
                    "/api/stock-gex/* must never be on a public allowlist; offenders: " + offenders);
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
