package app.feedgateway;

import com.optionsedge.contracts.liquiditywall.LiquidityWallSchemas;
import java.util.List;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericRecordBuilder;

/** The three relayed records of the liquidity-wall family, built from the REAL schemas of the contracts jar (0.66.0, ids 3403/3404/3407). */
final class EsLiquidityWallFixtures {
    static final long NOW = 1_800_000_000_000L;
    static final String ENV = "Rithmic Test";
    static final String CONTRACT = "ESZ6";
    static final String KEY = "CME|" + CONTRACT;
    static final String FINGERPRINT = "sha256:0123";

    private EsLiquidityWallFixtures() {
    }

    static Schema element(Schema record, String arrayField) {
        return record.getField(arrayField).schema().getElementType();
    }

    static Schema unionRecord(Schema record, String field) {
        for (Schema branch : record.getField(field).schema().getTypes()) {
            if (branch.getType() == Schema.Type.RECORD) {
                return branch;
            }
        }
        throw new IllegalStateException(field);
    }

    static GenericRecord level(String side, long tick, long lastSize, long smoothed, String state) {
        return new GenericRecordBuilder(element(LiquidityWallSchemas.DASHBOARD.schema(), "levels"))
                .set("side", side).set("tick", tick).set("lastSize", lastSize).set("smoothedSize", smoothed).set("state", state).build();
    }

    static GenericRecord cluster(String id, String side, long lo, long hi) {
        return new GenericRecordBuilder(element(LiquidityWallSchemas.DASHBOARD.schema(), "clusters"))
                .set("clusterId", id).set("side", side).set("loTick", lo).set("hiTick", hi).set("peakTick", hi).set("peakSize", 900L).set("totalSize", 2100L)
                .set("levelCount", (int) (hi - lo + 1)).set("geometry", "OK").set("persistenceRun", 4).build();
    }

    static GenericRecord wall(Schema wallSchema, String id, String lane, long lo, long hi, long birth) {
        GenericRecordBuilder b = new GenericRecordBuilder(wallSchema)
                .set("clusterId", id).set("lane", lane).set("loTick", lo).set("hiTick", hi).set("frozenTicks", List.of(lo, lo + 1, hi)).set("birthBucketEnd", birth)
                .set("peakSizeAtBirth", 900L).set("totalSizeAtBirth", 2100L).set("ladderMedianAtBirth", 120L).set("levelCountAtBirth", 3)
                .set("heldBuckets", 12).set("defendedBuckets", 2).set("brokenBuckets", 0).set("brokenBucketsFrozen", null)
                .set("termHold", 12).set("termDefense", 4).set("termPeak", 7).set("termLevels", 3).set("termBreaks", 0).set("scoreRaw", 26).set("scorePublished", 26)
                .set("scoreSettled", false).set("grade", "STRONG").set("defendedState", true).set("brokenState", false).set("lastEvidenceBucketEnd", birth)
                .set("breachOpen", false).set("breachT0", null).set("breachTickReached", null);
        return b.build();
    }

    static GenericRecord dashboardWall(String id, String lane, long lo, long hi, long birth) {
        return wall(element(LiquidityWallSchemas.DASHBOARD.schema(), "walls"), id, lane, lo, hi, birth);
    }

    static GenericRecord eventWall(String id, String lane, long lo, long hi, long birth) {
        return wall(unionRecord(LiquidityWallSchemas.EVENT.schema(), "wall"), id, lane, lo, hi, birth);
    }

    static GenericRecordBuilder dashboard(long bucketEnd, String quality) {
        return new GenericRecordBuilder(LiquidityWallSchemas.DASHBOARD.schema())
                .set("schemaVersion", 1).set("depthEnvironment", ENV).set("contract", CONTRACT).set("bucketEnd", bucketEnd).set("configFingerprint", FINGERPRINT)
                .set("quality", quality).set("closePath", "WATERMARK").set("layerBEnabled", true).set("spotTick", 30449L).set("spotAgeMs", 120L)
                .set("levels", List.of(level("BID", 30440L, 150L, 140L, "NEUTRAL"), level("ASK", 30460L, 900L, 880L, "STABLE_WALL")))
                .set("clusters", List.of(cluster("C1", "ASK", 30459L, 30461L)))
                .set("walls", List.of(dashboardWall("C1", "RESISTANCE", 30459L, 30461L, bucketEnd - 12_000L)))
                .set("leanThousandths", -412).set("above", 2400L).set("below", 1000L).set("sideRead", "UP_HEAVY").set("confirmedSide", "UP_HEAVY")
                .set("lastAdmittedFrameReceivedAtMs", bucketEnd - 80L).set("lastAdmittedStreamId", "stream-1").set("closingFramePresent", true);
    }

    static GenericRecord dashboard(long bucketEnd) {
        return dashboard(bucketEnd, "LIVE").build();
    }

    static GenericRecordBuilder event(long bucketEnd, String kind) {
        return new GenericRecordBuilder(LiquidityWallSchemas.EVENT.schema())
                .set("schemaVersion", 1).set("depthEnvironment", ENV).set("contract", CONTRACT).set("bucketEnd", bucketEnd).set("configFingerprint", FINGERPRINT)
                .set("kind", kind).set("spotTick", 30449L).set("wall", null).set("retireReason", null).set("breachT0", null).set("breachConfirmedAt", null)
                .set("breachTickReached", null).set("flip", null);
    }

    static GenericRecord born(long bucketEnd, String clusterId) {
        return event(bucketEnd, "BORN").set("wall", eventWall(clusterId, "RESISTANCE", 30459L, 30461L, bucketEnd)).build();
    }

    static GenericRecord retired(long bucketEnd, String clusterId, String reason) {
        return event(bucketEnd, "RETIRED").set("wall", eventWall(clusterId, "RESISTANCE", 30459L, 30461L, bucketEnd - 60_000L)).set("retireReason", reason).build();
    }

    static GenericRecord flip(long bucketEnd) {
        GenericRecord flip = new GenericRecordBuilder(unionRecord(LiquidityWallSchemas.EVENT.schema(), "flip"))
                .set("direction", "FLIP_TO_DOWNSIDE").set("b0BucketEnd", bucketEnd - 30_000L).set("b1BucketEnd", bucketEnd).set("fadeShareThousandths", 640)
                .set("riseRatioThousandths", 2310).set("spotTickB0", 30451L).set("spotTickB1", 30449L).set("aboveB0", 2400L).set("belowB0", 900L).set("aboveB1", 800L)
                .set("belowB1", 2600L).set("leanB0Thousandths", -455).set("leanB1Thousandths", 529).build();
        return event(bucketEnd, "FLIP").set("flip", flip).build();
    }

    static GenericRecord voice(long bucketEnd) {
        return new GenericRecordBuilder(LiquidityWallSchemas.VOICE.schema())
                .set("schemaVersion", 1).set("depthEnvironment", ENV).set("contract", CONTRACT).set("bucketEnd", bucketEnd).set("configFingerprint", FINGERPRINT)
                .set("voiceId", CONTRACT + "|" + bucketEnd).set("text", "liquidity flip - downside, 7612.25").set("direction", "FLIP_TO_DOWNSIDE").set("spotTick", 30449L).build();
    }
}
