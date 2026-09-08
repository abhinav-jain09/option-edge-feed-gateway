package app.feedgateway;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The application context must REFRESH. This is the one thing every other test in this suite takes
 * for granted and none of them checks.
 *
 * <p>The ES Footprint change added a package-private test-seam constructor to {@code FeedGatewayService}
 * and another to {@code GatewayController}. Both are Spring-managed and neither constructor carried
 * {@code @Autowired}, so the container had no candidate, fell back to a no-arg constructor that does
 * not exist, and the context failed to refresh:
 *
 * <pre>
 * Failed to instantiate [app.feedgateway.FeedGatewayService]: No default constructor found
 *   Caused by: java.lang.NoSuchMethodException: app.feedgateway.FeedGatewayService.&lt;init&gt;()
 * </pre>
 *
 * <p>The gateway did not start AT ALL — the footprint flag was irrelevant, and every environment was
 * affected. All 1,081 tests stayed green because every one of them constructs its collaborators
 * DIRECTLY, so not one of them ever asked the container to build anything.
 *
 * <h2>Isolation</h2>
 *
 * <p>Booting the real context in the default suite is only safe if the container cannot reach outside
 * the build, and that has to be guaranteed rather than hoped for. {@code GatewaySettings.value()}
 * gives {@code System.getenv()} precedence over {@code System.getProperty()}, so a system property set
 * from this class could NOT override an inherited {@code KAFKA_ENABLED=true} — the smoke test would
 * have opened real consumers on any developer shell or CI agent that exports it. The flags are
 * therefore pinned on the forked test JVM's own environment, by the surefire {@code context-smoke}
 * execution in {@code pom.xml}, and asserted below in {@code @BeforeAll} — which JUnit runs BEFORE the
 * Spring extension builds the context, so removing that execution fails this test instead of quietly
 * connecting to a broker.
 *
 * <p>The ordering is MEASURED, not assumed: JUnit runs user {@code @BeforeAll} methods before the test
 * instance exists, and {@code SpringExtension} loads the context from
 * {@code postProcessTestInstance}, which runs per instance — after them. Removing the pin and running
 * the suite produces, inside this execution, the assertion failure and NOTHING else: no Spring
 * bootstrap line, no {@code ConsumerConfig}, 0.17s elapsed. The context is never built.
 *
 * <p>The pin is scoped to this one execution rather than the whole suite because {@code KAFKA_ENABLED}
 * also decides whether {@link FeedGatewayService#start()} returns before its footprint preflight:
 * pinning it globally silently defeats {@code FootprintSeamTest}'s "an existing invalid topic refuses
 * start-up" case, which is how the first attempt at this was caught. Every other test keeps the
 * environment it was written against.
 *
 * <p>Two Maven user properties would otherwise reach past the pin, and the POM closes both by
 * setting the value explicitly — POM configuration beats a user property. {@code -DforkCount=0}
 * would run this execution inside Maven's own JVM, where surefire's {@code environmentVariables}
 * do not apply, so the execution pins {@code forkCount}. {@code -Dtest=} overrides an execution's
 * include and exclude PATTERNS, so the execution pins {@code test} instead of matching a filename:
 * routing by pattern made every {@code -Dtest=Something} run Something TWICE, the second time with
 * {@code KAFKA_ENABLED=false} — which is how
 * {@code FootprintSeamTest.aPreflightRefusalLeavesRunningFalse} began failing, since with Kafka
 * off {@code start()} returns before the preflight and the refusal it asserts never happens.
 *
 * <p>With {@code KAFKA_ENABLED=false}, {@link FeedGatewayService#start()} returns before opening a
 * consumer and {@code LiquidityHistoryStore.start()} is a no-op. With {@code GATEWAY_AUTH_ENABLED=false}
 * the Keycloak/Redis wiring is not created. The remaining external integration is Postgres, which
 * {@code PinFlowDataSourceConfig} builds {@code @Lazy} with {@code initializationFailTimeout=-1} and
 * only when a JDBC URL resolves, so it opens nothing at boot by construction. {@code @SpringBootTest}
 * uses a mock servlet environment, so no port is bound. The context does create one JVM temp directory
 * ({@code SellerActivityDiskStore} calls {@code Files.createTempDirectory}); that is the only filesystem
 * side effect, and it is not a fixed path.
 *
 * <p>Everything else is the real thing — the same component scan, configuration classes and
 * post-processors the deployed jar refreshes.
 *
 * <p>{@link app.feedgateway.mtsession.gateway.GatewayContextBootIT} remains the opt-in test for the
 * flag-gated auth wiring, which genuinely needs Keycloak and Redis.
 */
@SpringBootTest
// The tag EXCLUDES this class from default-test. Selecting it INTO the pinned execution is done by
// that execution's own <test> entry in pom.xml — the tag does not do that. The two together are what
// keep the class in exactly one execution under `-Dtest=`: without the tag,
// `-Dtest=GatewayContextSmokeTest` would also run it in the unpinned default-test.
@Tag(GatewayContextSmokeTest.TAG)
class GatewayContextSmokeTest {

    /** Excludes this class from the default-test execution; see the excludedGroups entry in pom.xml. */
    static final String TAG = "context-boot";

    @BeforeAll
    static void isolationIsInPlaceBeforeTheContextIsBuilt() {
        // Fail CLOSED: if the surefire environmentVariables block is removed or renamed, this test
        // must stop rather than boot a container that reaches for a real broker.
        assertFalse(GatewaySettings.boolValue("KAFKA_ENABLED", true),
                "KAFKA_ENABLED is not false in the test JVM. The surefire 'context-smoke' execution "
                        + "in pom.xml is what pins it; without that pin this test would open real Kafka "
                        + "consumers against the configured broker.");
        assertFalse(GatewaySettings.boolValue("GATEWAY_AUTH_ENABLED", false),
                "GATEWAY_AUTH_ENABLED is not false in the test JVM. The surefire 'context-smoke' "
                        + "execution in pom.xml is what pins it; without that pin this test would need "
                        + "Keycloak and Redis.");
    }

    @Autowired
    ApplicationContext ctx;

    @Test
    void contextRefreshesAndBuildsTheBeansThatCarryTestSeams() {
        assertNotNull(ctx, "the context did not refresh");

        // Both classes carry a production constructor AND a package-private test seam. Getting the
        // bean at all proves the container chose one; the seam takes an extra argument no bean
        // definition supplies, so a container that chose it could not have reached this line.
        assertNotNull(ctx.getBean(FeedGatewayService.class));
        assertNotNull(ctx.getBean(GatewayController.class));

        // Guard against the seams being deleted and this test quietly becoming vacuous.
        assertTrue(FeedGatewayService.class.getDeclaredConstructors().length >= 2,
                "FeedGatewayService no longer has a second constructor — re-read this test rather "
                        + "than deleting it; the ambiguity it guards against no longer exists");
        assertTrue(GatewayController.class.getDeclaredConstructors().length >= 2,
                "GatewayController no longer has a second constructor — re-read this test rather "
                        + "than deleting it; the ambiguity it guards against no longer exists");
    }
}
