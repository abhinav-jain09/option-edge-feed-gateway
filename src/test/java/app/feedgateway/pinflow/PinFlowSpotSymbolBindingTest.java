package app.feedgateway.pinflow;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * pin_spot_minute is MULTI-SYMBOL — the spot consumer writes every underlying it sees (measured on one dev
 * feed: SPX ~7478 and VIX ~17.9). Without a symbol predicate their rows collapse into one minute→spot map
 * and the chart can plot the wrong instrument. This pins the predicate and its parameter order so an ES or
 * VIX deployment can never inherit SPX's price line.
 */
class PinFlowSpotSymbolBindingTest {

    private static String store() throws Exception {
        return Files.readString(Path.of("src/main/java/app/feedgateway/pinflow/PinFlowStore.java"));
    }

    @Test
    void spotHistoryQueryIsScopedToOneSymbol() throws Exception {
        String src = store();
        assertTrue(src.contains("FROM pin_spot_minute ")
                        && src.contains("WHERE symbol = ? AND as_of_minute >= ? AND as_of_minute < ?"),
                "spot-history SQL must filter by symbol — the table is multi-symbol");
    }

    @Test
    void symbolIsBoundFirstAndTimestampsShiftAccordingly() throws Exception {
        String src = store();
        assertTrue(src.contains("ps.setString(1, spotSymbol);"), "symbol bound at index 1");
        assertTrue(src.contains("ps.setTimestamp(2, start);"), "window start shifted to index 2");
        assertTrue(src.contains("ps.setTimestamp(3, end);"), "window end shifted to index 3");
    }

    @Test
    void symbolComesFromDeploymentConfigNotAHardcodedDefault() throws Exception {
        String src = store();
        assertTrue(src.contains("this.spotSymbol = settings.initialSymbol();"),
                "production symbol must come from IB_SYMBOL (es4 sets ES), not a literal");
    }

    @Test
    void historyFallbackIsOnlyForAnAbsentTable() throws Exception {
        String src = store();
        assertTrue(src.contains("\"42P01\".equals(e.getSQLState())"),
                "degrade to the legacy column ONLY on undefined_table; timeouts/cancellation must propagate");
    }
}
