package app.feedgateway.pinflow;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §4 regression: {@code /api/pin-flow} is authenticated by the SAME centralized verifier as
 * {@code /api/liquidity-history} and is NOT public.
 *
 * <p>This gateway has NO Spring Security filter chain (see the pom comment: adding servlet security
 * would lock down every endpoint behind a generated password), so there is no {@code permitAll}
 * allowlist to be absent from. "Public" in this codebase means an endpoint that does not invoke the
 * shared auth bean. This test asserts the invariant at the source level:
 * <ul>
 *   <li>{@code PinFlowController} calls {@code auth.authenticate(...)} before doing work (authenticated);</li>
 *   <li>no source references {@code /api/pin-flow} inside any {@code permitAll}/allowlist construct
 *       (there is none, so a future one would fail this).</li>
 * </ul>
 * The behavioural counterpart — a missing token → 401 — is covered by {@link PinFlowControllerTest}.
 */
class PinFlowAuthAllowlistRegressionTest {

    private static final Path MAIN = Path.of("src/main/java");

    @Test
    void controllerInvokesSharedAuthVerifier() {
        String controller = read(MAIN.resolve("app/feedgateway/pinflow/PinFlowController.java"));
        assertTrue(controller.contains("auth.authenticate("),
                "PinFlowController must enforce auth via the shared LiquidityHistoryAuth verifier (§4)");
        assertTrue(controller.contains("LiquidityHistoryAuth"),
                "auth must be the SAME shared bean that protects /api/liquidity-history, not a re-implementation");
    }

    @Test
    void pinFlowIsNotOnAnyPublicAllowlist() {
        // Scan ALL main sources: no permitAll / public-path allowlist may reference the endpoint.
        try (Stream<Path> paths = Files.walk(MAIN)) {
            List<Path> offenders = paths
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        String src = read(p);
                        boolean mentionsPinFlow = src.contains("/api/pin-flow");
                        boolean hasAllowlistWord = src.contains("permitAll")
                                || src.contains("requestMatchers")
                                || src.contains("antMatchers")
                                || src.contains("PUBLIC_PATHS");
                        // Only the controller may mention the path; nowhere may it appear with an allowlist word.
                        return mentionsPinFlow && hasAllowlistWord;
                    })
                    .toList();
            assertTrue(offenders.isEmpty(),
                    "/api/pin-flow must never be on a public allowlist; offenders: " + offenders);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // Belt-and-suspenders: this gateway must not have introduced a servlet security filter chain
        // (that would silently change the whole endpoint auth model).
        try (Stream<Path> paths = Files.walk(MAIN)) {
            boolean hasFilterChain = paths
                    .filter(p -> p.toString().endsWith(".java"))
                    .anyMatch(p -> read(p).contains("SecurityFilterChain"));
            assertFalse(hasFilterChain,
                    "gateway auth is per-controller via the shared verifier; a SecurityFilterChain would change that");
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
