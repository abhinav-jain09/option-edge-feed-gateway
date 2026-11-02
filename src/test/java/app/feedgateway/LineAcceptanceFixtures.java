package app.feedgateway;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;

/**
 * Line acceptance / rejection fixtures: the producer contract's own schemas (src/test/resources/lineacc, byte-identical copies of options-edge-contracts 0.60.0 schemas-registry/ta,
 * ids 3101-3108, pinned by LineAcceptanceSchemaPinsTest) and synthetic records built from them. The real producer is built in parallel: these are the real Avro schemas with mock records.
 */
final class LineAcceptanceFixtures {
    private LineAcceptanceFixtures() {
    }

    static Schema schema(String name) {
        try (InputStream in = LineAcceptanceFixtures.class.getResourceAsStream("/lineacc/" + name + ".avsc")) {
            if (in == null) {
                throw new IllegalStateException("test resource /lineacc/" + name + ".avsc is missing");
            }
            return new Schema.Parser().parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static GenericRecord common(String name, String tf, String date, long seq, long asOf) {
        GenericRecord r = TaFixtures.minimal(schema(name));
        TaFixtures.put(r, "schemaVersion", 1);
        TaFixtures.put(r, "sessionDate", date);
        TaFixtures.put(r, "sessionSegment", "RTH");
        TaFixtures.put(r, "timeframe", tf);
        TaFixtures.put(r, "publishSeq", seq);
        TaFixtures.put(r, "asOfMs", asOf);
        return r;
    }

    /** A core that passes the boundary: a REJECTED VWAP_MEAN episode. */
    static GenericRecord core(String tf, String date, String id, long seq, long asOf) {
        GenericRecord r = common("LineEpisodeCore", tf, date, seq, asOf);
        TaFixtures.put(r, "recordType", "EPISODE_CORE");
        TaFixtures.put(r, "episodeId", id);
        TaFixtures.put(r, "s0Verdict", "REJECTED");
        TaFixtures.put(r, "approachDirection", "FROM_ABOVE");
        TaFixtures.put(r, "vwapSource", "VWAP_LIVE");
        GenericRecord line = TaFixtures.child(r, "line");
        TaFixtures.put(line, "kind", "VWAP_MEAN");
        TaFixtures.put(line, "lineClass", "VWAP");
        TaFixtures.put(line, "lineId", "vwap:mean");
        TaFixtures.put(r, "parameterSetHash", "ab".repeat(32));
        TaFixtures.put(r, "stateRule", "S0");
        return r;
    }

    static GenericRecord evidence(String tf, String date, String id, int version, long seq, long asOf) {
        GenericRecord r = common("LineEpisodeEvidence", tf, date, seq, asOf);
        TaFixtures.put(r, "recordType", "EPISODE_EVIDENCE");
        TaFixtures.put(r, "episodeId", id);
        TaFixtures.put(r, "evidenceVersion", version);
        return r;
    }

    static GenericRecord outcome(String tf, String date, String id, int version, String s1, long seq, long asOf) {
        GenericRecord r = common("LineEpisodeOutcome", tf, date, seq, asOf);
        TaFixtures.put(r, "recordType", "EPISODE_OUTCOME");
        TaFixtures.put(r, "episodeId", id);
        TaFixtures.put(r, "s1Version", version);
        TaFixtures.put(r, "s1Outcome", s1);
        return r;
    }

    static GenericRecord integrity(String tf, String date, String id, int version, long seq, long asOf) {
        GenericRecord r = common("LineEpisodeIntegrity", tf, date, seq, asOf);
        TaFixtures.put(r, "recordType", "EPISODE_INTEGRITY");
        TaFixtures.put(r, "episodeId", id);
        TaFixtures.put(r, "integrityVersion", version);
        return r;
    }

    static GenericRecord lean(String tf, String date, String id, int ordinal, String lean, long seq, long asOf) {
        GenericRecord r = common("LeanEvent", tf, date, seq, asOf);
        TaFixtures.put(r, "recordType", "LEAN_EVENT");
        TaFixtures.put(r, "episodeId", id);
        TaFixtures.put(r, "leanBarOrdinal", ordinal);
        TaFixtures.put(r, "lean", lean);
        TaFixtures.put(r, "reason", "UNVALIDATED");
        TaFixtures.put(r, "parameterSetHash", "ab".repeat(32));
        return r;
    }

    static GenericRecord coverage(String tf, String date, int version, long seq, long asOf) {
        GenericRecord r = common("SessionCoverage", tf, date, seq, asOf);
        TaFixtures.put(r, "recordType", "SESSION_COVERAGE");
        TaFixtures.put(r, "coverageVersion", version);
        return r;
    }

    static GenericRecord monitor(String tf, String date, String lineId, String state, long seq, long asOf) {
        GenericRecord r = common("LineMonitorStatus", tf, date, seq, asOf);
        TaFixtures.put(r, "recordType", "MONITOR_STATUS");
        TaFixtures.put(r, "lineId", lineId);
        TaFixtures.put(r, "monitorState", state);
        TaFixtures.put(r, "settledSide", "ABOVE");
        return r;
    }

    static GenericRecord status(String cell, int version, String state) {
        GenericRecord r = TaFixtures.minimal(schema("CalibrationStatus"));
        TaFixtures.put(r, "schemaVersion", 1);
        TaFixtures.put(r, "cellId", cell);
        TaFixtures.put(r, "parameterSetHash", "ab".repeat(32));
        TaFixtures.put(r, "statusVersion", version);
        TaFixtures.put(r, "effectiveAtMs", 1_000L * version);
        TaFixtures.put(r, "state", state);
        return r;
    }
}
