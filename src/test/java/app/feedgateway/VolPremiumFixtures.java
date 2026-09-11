package app.feedgateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * The REAL vol-premium engine output, committed under {@code src/test/resources/vol-premium/}: the
 * canonical v2 reading and v1 early warning (byte-for-byte the files the web repo carries too) and a
 * full-session stream of 371 observations and 15 transitions, in publication order. The observation
 * stream is gzipped only to keep 2 MB of text out of the repository; its DECOMPRESSED bytes are
 * pinned by SHA-256 below, as are the other three files, so a fixture cannot drift from what the
 * engine produced without a test saying so.
 *
 * <p>Every payload a test offers is one of these records or a single-field variant of one, and every
 * variant used here was run through the contract before it was relied on. They are dated to the
 * session the engine produced them in, so the gateway is told the time through its vol-premium clock
 * seam ({@link #FIXTURE_NOW_MS}) instead of judging a fixed date against the wall clock — which is
 * what made the previous fixtures a date bomb.
 */
final class VolPremiumFixtures {

    static final ObjectMapper MAPPER = new ObjectMapper();

    static final String CANONICAL_READING_SHA256 = "1406e7ba57d3ec9d6bee66f3d88ded0d837132a33c03bbd1f30a4eaa8a2cbc21";
    static final String CANONICAL_WARNING_SHA256 = "7a3bd49a79ae70113c2a0c216affdcc4ab15a9f3c6241914649fd3ddeee71802";
    static final String READINGS_TSV_SHA256 = "141dd9b2ca5deef831ae82cd0e8599766070f7c35d0eb3ca097b5d86eab563e5";
    static final String WARNINGS_TSV_SHA256 = "951504d4c4ccc2baf316da5c3a6ec38755514f18b44276a32e89a2ca463ea0b3";

    static final String SESSION = "2026-08-27";
    /** The stream's first observation (frameSeq 6840, 09:30:00 ET) and last (7210). */
    static final long STREAM_FIRST_EVENT_MS = 1787837400000L;
    static final long STREAM_LAST_EVENT_MS = 1787839250000L;
    /** "Now" for every fixture test: one second after the stream's last observation. */
    static final long FIXTURE_NOW_MS = STREAM_LAST_EVENT_MS + 1_000L;
    /** ET midnight that opens the session, and the last instant the contract still files under it. */
    static final long SESSION_MIDNIGHT_MS = 1787803200000L;
    static final long SESSION_LAST_INSTANT_MS = SESSION_MIDNIGHT_MS + 86_400_000L
            + com.optionsedge.contracts.volpremium.IvRvReading.MAX_AFTER_MIDNIGHT_MS;

    /** A Kafka record as the producer wrote it: the key, and the value's exact bytes. */
    record Row(String key, String json) {
    }

    private static List<Row> readings;
    private static List<Row> warnings;

    private VolPremiumFixtures() {
    }

    static byte[] resource(String name) {
        try (InputStream in = VolPremiumFixtures.class.getResourceAsStream("/vol-premium/" + name)) {
            if (in == null) {
                throw new IllegalStateException("missing test resource vol-premium/" + name);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static byte[] readingsTsvBytes() {
        try (InputStream in = new GZIPInputStream(new java.io.ByteArrayInputStream(resource("ivrv-readings.tsv.gz")))) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** All 371 observations of the stream, in publication order. */
    static synchronized List<Row> readings() {
        if (readings == null) {
            readings = rows(readingsTsvBytes());
        }
        return readings;
    }

    /** All 15 warning transitions of the stream, in publication order. */
    static synchronized List<Row> warnings() {
        if (warnings == null) {
            warnings = rows(resource("warnings.tsv"));
        }
        return warnings;
    }

    /** The stream's observation at one ordinal (the stream has exactly one per ordinal). */
    static Row readingAt(long frameSeq) {
        String suffix = "|" + frameSeq;
        for (Row row : readings()) {
            if (row.key().endsWith(suffix)) {
                return row;
            }
        }
        throw new IllegalArgumentException("no observation at ordinal " + frameSeq);
    }

    static Row canonicalReading() {
        return canonical("ivrv-reading.canonical.v2.json");
    }

    static Row canonicalWarning() {
        return canonical("early-warning.canonical.v1.json");
    }

    /** One top-level field replaced; every other byte of the record's content kept. */
    static String with(String json, String field, long value) {
        ObjectNode node = object(json);
        node.put(field, value);
        return write(node);
    }

    static String with(String json, String field, String value) {
        ObjectNode node = object(json);
        node.put(field, value);
        return write(node);
    }

    static String withNull(String json, String field) {
        ObjectNode node = object(json);
        node.putNull(field);
        return write(node);
    }

    static String without(String json, String field) {
        ObjectNode node = object(json);
        node.remove(field);
        return write(node);
    }

    /**
     * The same observation one session later or earlier: every instant moved by whole days, the
     * session date with them. The ordinal is unchanged because it is measured from the session's own
     * midnight, and no DST change falls between 2026-08-26 and 2026-08-28.
     */
    static String shiftedDays(String json, int days, String sessionDate) {
        ObjectNode node = object(json);
        long delta = days * 86_400_000L;
        for (String field : new String[] {"eventTimeMs", "impliedAsOfMs", "spotAsOfMs", "measurementEpochMs"}) {
            if (node.hasNonNull(field)) {
                node.put(field, node.get(field).asLong() + delta);
            }
        }
        node.put("sessionDate", sessionDate);
        return write(node);
    }

    static long longField(String json, String field) {
        return object(json).get(field).asLong();
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Row canonical(String name) {
        try {
            JsonNode tree = MAPPER.readTree(resource(name));
            return new Row(tree.get("key").asText(), MAPPER.writeValueAsString(tree.get("value")));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Row> rows(byte[] tsv) {
        List<Row> out = new ArrayList<>();
        for (String line : new String(tsv, StandardCharsets.UTF_8).split("\n")) {
            if (line.isEmpty()) {
                continue;
            }
            int tab = line.indexOf('\t');
            out.add(new Row(line.substring(0, tab), line.substring(tab + 1)));
        }
        return List.copyOf(out);
    }

    private static ObjectNode object(String json) {
        try {
            return (ObjectNode) MAPPER.readTree(json);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String write(ObjectNode node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
