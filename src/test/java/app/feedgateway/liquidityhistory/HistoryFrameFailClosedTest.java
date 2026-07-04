package app.feedgateway.liquidityhistory;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static app.feedgateway.liquidityhistory.LiquidityHistoryTestSupport.MAPPER;
import static app.feedgateway.liquidityhistory.LiquidityHistoryTestSupport.cell;
import static app.feedgateway.liquidityhistory.LiquidityHistoryTestSupport.frame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Fail-closed ingestion of the dashboard-history topic (institutional-review finding): absent/unknown
 * freshness or inputQuality must never render as active/full liquidity, and a present cell missing a
 * measured-size field is malformed, not "zero liquidity".
 */
class HistoryFrameFailClosedTest {

    private static ObjectNode obj(String frameJson) throws Exception {
        return (ObjectNode) MAPPER.readTree(frameJson);
    }

    @Test
    void validFrameParsesWithItsDeclaredQuality() throws Exception {
        String json = frame("SPX", "2026-07-06", 1_000_000L, "LIVE", "FULL",
                List.of(6000.0), cell(6000.0, "CALL"));
        HistoryFrame f = HistoryFrame.parse(json, MAPPER);
        assertNotNull(f);
        assertEquals("LIVE", f.freshness());
        assertEquals("FULL", f.inputQuality());
    }

    @Test
    void absentFreshnessAndQualityFailClosedToStaleDegraded() throws Exception {
        ObjectNode f = obj(frame("SPX", "2026-07-06", 1_000_000L, "LIVE", "FULL",
                List.of(6000.0), cell(6000.0, "CALL")));
        f.remove("freshness");
        f.remove("inputQuality");
        HistoryFrame parsed = HistoryFrame.parse(f.toString(), MAPPER);
        assertNotNull(parsed);
        assertEquals("STALE", parsed.freshness(), "absent freshness must NOT default to LIVE");
        assertEquals("DEGRADED", parsed.inputQuality(), "absent inputQuality must NOT default to FULL");
    }

    @Test
    void unknownFreshnessAndQualityFailClosedToWorst() throws Exception {
        ObjectNode f = obj(frame("SPX", "2026-07-06", 1_000_000L, "SOMETHING_NEW", "MAYBE_OK",
                List.of(6000.0), cell(6000.0, "CALL")));
        HistoryFrame parsed = HistoryFrame.parse(f.toString(), MAPPER);
        assertNotNull(parsed);
        assertEquals("STALE", parsed.freshness(), "unknown/future freshness treated conservatively as STALE");
        assertEquals("DEGRADED", parsed.inputQuality(), "unknown inputQuality treated as DEGRADED");
    }

    @Test
    void gapFreshnessIsPreserved() throws Exception {
        String json = frame("SPX", "2026-07-06", 1_000_000L, "GAP", "FULL",
                List.of(6000.0), cell(6000.0, "CALL"));
        assertEquals("GAP", HistoryFrame.parse(json, MAPPER).freshness());
    }

    @Test
    void presentCellMissingMeasuredSizeIsMalformedNotZero() throws Exception {
        ObjectNode f = obj(frame("SPX", "2026-07-06", 1_000_000L, "LIVE", "FULL",
                List.of(6000.0), cell(6000.0, "CALL")));
        // Drop a required size key from the (otherwise valid) present cell.
        ((ObjectNode) f.get("cells").get(0)).remove("lastBidSize");
        assertNull(HistoryFrame.parse(f.toString(), MAPPER),
                "a present cell missing a measured size is rejected, never folded as zero liquidity");
    }
}
