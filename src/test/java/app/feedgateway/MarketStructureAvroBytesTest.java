package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

/** The byte limit of a market-structure frame is a UTF-8 byte limit, enforced before retention, not a count of UTF-16 units. */
class MarketStructureAvroBytesTest {

    @Test
    void aFrameWithinTheCharacterBoundButOverTheByteBoundIsRefused() {
        int fits = FeedGatewayService.MARKET_STRUCTURE_MAX_BYTES / 2 - 4096;
        GenericRecord ascii = TaFixtures.put(TaFixtures.uiProjection(1L), "warning", "e".repeat(fits));
        assertNotNull(MarketStructureAvro.uiFrame(ascii), "ASCII within the limit is relayed");
        // 2-byte characters: the same character count is now about twice the byte limit's half, so push it over in bytes only
        int over = FeedGatewayService.MARKET_STRUCTURE_MAX_BYTES * 3 / 4;   // chars well under the 128 KiB character bound, bytes over the byte bound
        GenericRecord wide = TaFixtures.put(TaFixtures.uiProjection(1L), "warning", "é".repeat(over));
        String chars = TaFixtures.frame(wide);
        assertTrue(chars.length() <= FeedGatewayService.MARKET_STRUCTURE_MAX_BYTES, "within the character bound: " + chars.length());
        assertTrue(StudiesRelay.utf8Length(chars) > FeedGatewayService.MARKET_STRUCTURE_MAX_BYTES, "over the byte bound: " + StudiesRelay.utf8Length(chars));
        assertNull(MarketStructureAvro.uiFrame(wide), "refused on bytes");
    }
}
