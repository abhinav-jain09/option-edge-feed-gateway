package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.feedgateway.readable.ReferencePrice;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.optionsedge.contracts.hpsf.UnderlyingPriceEvent;
import java.util.List;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>The price boundary, driven through BOTH sides of it.</b>
 *
 * <p>C-74 read the wrong close for a reason no test could see: {@code underlying.spx.price} carries
 * a <b>typed</b> {@link UnderlyingPriceEvent} — the gateway's deserializer returns typed records
 * for exactly that topic, {@code underlying.vix.price} and the spot-vol regime, and generic ones
 * for everything else — while the start-up seek converted with {@code avroJson}, which requires a
 * {@code GenericRecord} and returned null for every single record. Measured on dev: <b>45,075
 * records polled, ZERO converted.</b> Every unit test passed, because every fixture was
 * hand-written JSON in the shape the decoder wanted; nothing bound the real converter's OUTPUT to
 * the real decoder's INPUT, so the one link that was broken was the one nothing crossed.
 *
 * <p>So this test starts from a real typed event, renders it with the converter the seek now uses,
 * and resolves a reference price from the result. If the converter and the decoder ever disagree
 * about a field name again, this fails — and a drain that converts nothing is separately reported
 * by {@code ReadableReferenceHistory.Drain.polledButConvertedNothing()}.
 */
class SpxPriceBoundaryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 2026-11-19 16:04:03 New York, the settlement-side print the reviewer named. */
    private static final long PRINT =
            java.time.Instant.parse("2026-11-19T21:04:03Z").toEpochMilli();
    private static final long NOW =
            java.time.Instant.parse("2026-11-20T05:30:00Z").toEpochMilli();

    private static UnderlyingPriceEvent event(long atMs, double price, String source,
            String quality) {
        return new UnderlyingPriceEvent(1, "SPX",
                java.time.Instant.ofEpochMilli(atMs).toString(),
                price, 0L, source, quality,
                price, atMs * 1_000_000L, "epoch-1", 1L, quality);
    }

    private static ReferencePrice.History history(List<String> prices) {
        return new ReferencePrice.History() {
            @Override
            public List<String> pricesBetween(long fromMs, long toMs, int limit) {
                return prices;
            }

            @Override
            public List<String> barsBetween(long fromMs, long toMs) {
                return List.of();
            }
        };
    }

    @Test
    @DisplayName("a typed price event is NOT a GenericRecord, which is the whole defect")
    void aTypedEventIsNotAGenericRecord() {
        Object value = event(PRINT, 7176.24, "NATIVE_SPX_INDEX", "LIVE_INDEX");
        assertTrue(!(value instanceof GenericRecord),
                "if this ever becomes a GenericRecord the old converter would start working and "
                        + "this test's reason for existing should be re-read, not deleted");
        assertNotNull(SpxPriceJson.render(MAPPER, value),
                "and the converter the seek now uses reads it");
    }

    @Test
    @DisplayName("the converter's own output resolves to the close the reviewer named")
    void theConvertersOutputResolvesTheClose() {
        List<String> converted = List.of(
                SpxPriceJson.render(MAPPER, event(PRINT - 3_600_000L, 7181.10,
                        "NATIVE_SPX_INDEX", "LIVE_INDEX")),
                SpxPriceJson.render(MAPPER, event(PRINT, 7176.24,
                        "NATIVE_SPX_INDEX", "LIVE_INDEX")),
                // After the close the price keeps ticking off the ES basis. It is not a close.
                SpxPriceJson.render(MAPPER, event(PRINT + 7_200_000L, 7160.10,
                        "ES_BASIS_DERIVED", "LIVE_ES_BASIS")));
        converted.forEach(json -> assertNotNull(json, "every record converted"));

        ReferencePrice.Resolved r = ReferencePrice.resolve(NOW, history(converted));

        assertEquals(ReferencePrice.From.INDEX_PRINT, r.from(),
                "the close comes from the index's own print, not a futures bar: " + r.label());
        assertNotNull(r.close(), "it resolved a close at all");
        assertEquals(7176.24, r.close().spx(), 0.005,
                "the newest index print at or before now, which is 16:04:03 New York");
        assertEquals("NATIVE_SPX_INDEX", r.close().source(),
                "and it says which source it came from, as the page prints");
    }

    @Test
    @DisplayName("the OLD converter would have resolved nothing, so the fixtures were the illusion")
    void theOldConverterWouldHaveResolvedNothing() {
        // avroJson's contract in one line: a GenericRecord or nothing. This is what the seek was
        // doing to all 45,075 records.
        List<String> asTheOldSeekSawThem = new java.util.ArrayList<>();
        for (long t : new long[] {PRINT - 3_600_000L, PRINT}) {
            Object value = event(t, 7176.24, "NATIVE_SPX_INDEX", "LIVE_INDEX");
            String json = value instanceof GenericRecord ? "{}" : null;
            if (json != null) {
                asTheOldSeekSawThem.add(json);
            }
        }
        assertTrue(asTheOldSeekSawThem.isEmpty(),
                "nothing survived the generic renderer, which is why C-74 fell through to a bar");

        ReferencePrice.Resolved r = ReferencePrice.resolve(NOW, history(asTheOldSeekSawThem));
        assertEquals(ReferencePrice.From.NONE, r.from(),
                "and with the bars empty too it resolves nothing at all: " + r.label());
        assertNull(r.close(), "an absence, never a number");
    }
}
