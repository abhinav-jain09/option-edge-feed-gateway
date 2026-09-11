package app.feedgateway;

import static app.feedgateway.VolPremiumFixtures.MAPPER;
import static app.feedgateway.VolPremiumFixtures.Row;
import static app.feedgateway.VolPremiumFixtures.SESSION;
import static app.feedgateway.VolPremiumFixtures.SESSION_MIDNIGHT_MS;
import static app.feedgateway.VolPremiumFixtures.edit;
import static app.feedgateway.VolPremiumFixtures.longField;
import static app.feedgateway.VolPremiumFixtures.readingAt;
import static app.feedgateway.VolPremiumFixtures.readings;
import static app.feedgateway.VolPremiumFixtures.shiftedWarning;
import static app.feedgateway.VolPremiumFixtures.warnings;
import static app.feedgateway.VolPremiumFixtures.with;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.feedgateway.VolPremiumSessionStore.Admission;
import app.feedgateway.VolPremiumSessionStore.Item;
import app.feedgateway.VolPremiumSessionStore.Position;
import app.feedgateway.VolPremiumSessionStore.Refusal;
import app.feedgateway.VolPremiumSessionStore.Stream;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.optionsedge.contracts.volpremium.EarlyWarning;
import com.optionsedge.contracts.volpremium.EarlyWarningDirection;
import com.optionsedge.contracts.volpremium.EarlyWarningState;
import com.optionsedge.contracts.volpremium.EarlyWarningType;
import com.optionsedge.contracts.volpremium.EpisodeOutcome;
import com.optionsedge.contracts.volpremium.IvRvParameterSet;
import com.optionsedge.contracts.volpremium.IvRvReading;
import com.optionsedge.contracts.volpremium.TimeOfDayNormalisation;
import com.optionsedge.contracts.volpremium.WarningSummary;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The supported-output envelope of VolPremiumSessionStore (Codex gateway r3, finding 3 of r2): the widths DERIVED
 * from the contract and the engine's serialiser, the position caps derived from the engine's emission bound, and the
 * whole envelope admitted through the production store and replayed from its disk log, completely and in order.
 */
class VolPremiumEnvelopeTest {

    /** Bytes Jackson can write for ONE char of a free string: a control char becomes a six-byte escape. */
    static final int ESCAPED = 6;

    /**
     * The widest text of a string component, {@code perChar} bytes for each char of a FREE string (bounded by the
     * contract in length only). An episode id is DERIVED ({@code symbol|sessionDate|type|openedFrameSeq}), so only
     * its symbol is free. A string component with no bound here fails the test, so a free string added to a contract
     * cannot escape the envelope.
     */
    private static int widestString(Class<?> owner, String component, int perChar) {
        int typeName = 0;
        for (EarlyWarningType type : EarlyWarningType.values()) {
            typeName = Math.max(typeName, type.name().length());
        }
        int episodeIdChars = Math.min(EarlyWarning.MAX_EPISODE_ID_CHARS,
                IvRvReading.MAX_SYMBOL_CHARS + 1 + "yyyy-MM-dd".length() + 1 + typeName + 1 + 20);
        String name = owner.getSimpleName() + "." + component;
        return switch (name) {
            case "IvRvReading.symbol", "EarlyWarning.symbol" -> IvRvReading.MAX_SYMBOL_CHARS * perChar;
            case "IvRvReading.sessionDate", "EarlyWarning.sessionDate" -> "yyyy-MM-dd".length();
            case "IvRvReading.baselineMode" ->
                    Math.max(IvRvReading.MODE_PINNED.length(), IvRvReading.MODE_UNCALIBRATED.length());
            case "IvRvReading.codeVersion", "EarlyWarning.codeVersion" -> IvRvReading.MAX_VERSION_CHARS * perChar;
            case "IvRvReading.spreadBasis" -> IvRvReading.SPREAD_BASIS_RAW_LEVEL_DIFFERENCE.length();
            case "IvRvReading.parameterSetHash", "EarlyWarning.parameterSetHash" ->
                    IvRvParameterSet.V1.contentHash().length();
            case "Provenance.chainTopic", "Provenance.spotTopic" -> IvRvReading.Provenance.MAX_TOPIC_CHARS * perChar;
            case "Provenance.chainFeedEpochId" -> IvRvReading.Provenance.MAX_EPOCH_ID_CHARS * perChar;
            case "WarningSummary.episodeId", "EarlyWarning.episodeId" ->
                    episodeIdChars + IvRvReading.MAX_SYMBOL_CHARS * (perChar - 1);
            default -> throw new AssertionError("no contract bound for the string " + name);
        };
    }

    private static int longestName(Class<?> enumType) {
        int n = 0;
        for (Object constant : enumType.getEnumConstants()) {
            n = Math.max(n, ((Enum<?>) constant).name().length());
        }
        return n;
    }

    /**
     * The widest compact JSON of a contract record: every component present, each at the widest text its type can
     * print (int 11, long 20, double 24 — {@code "-2.2250738585072014E-308"}), strings at their bound, enums at their
     * longest name, a set of enums holding every name, and lists at the count the contract requires: one trend per
     * horizon of the shipped parameter set, one summary per warning type.
     */
    static long widestJson(Class<?> type, int perChar) {
        RecordComponent[] components = type.getRecordComponents();
        long n = 2 + (components.length - 1);
        for (RecordComponent c : components) {
            n += c.getName().length() + 3 + widestValue(type, c, perChar);
        }
        return n;
    }

    private static long widestValue(Class<?> owner, RecordComponent c, int perChar) {
        Class<?> t = c.getType();
        if (t == int.class || t == Integer.class) {
            return 11;
        }
        if (t == long.class || t == Long.class) {
            return 20;
        }
        if (t == double.class || t == Double.class) {
            return 24;
        }
        if (t.isEnum()) {
            return 2 + longestName(t);
        }
        if (t == String.class) {
            return 2 + widestString(owner, c.getName(), perChar);
        }
        if (t.isRecord()) {
            return widestJson(t, perChar);
        }
        if (List.class.isAssignableFrom(t) || Set.class.isAssignableFrom(t)) {
            Class<?> element = (Class<?>) ((ParameterizedType) c.getGenericType()).getActualTypeArguments()[0];
            if (element.isEnum()) {
                Object[] all = element.getEnumConstants();
                long n = 2 + (all.length - 1);
                for (Object constant : all) {
                    n += 2 + ((Enum<?>) constant).name().length();
                }
                return n;
            }
            int count = element == IvRvReading.IvRvTrend.class ? IvRvParameterSet.V1.horizonsMs().size()
                    : element == WarningSummary.class ? EarlyWarningType.values().length : -1;
            if (count < 0) {
                throw new AssertionError("no contract count for " + owner.getSimpleName() + "." + c.getName());
            }
            return 2 + count * widestJson(element, perChar) + (count - 1);
        }
        throw new AssertionError("no width rule for " + owner.getSimpleName() + "." + c.getName() + " (" + t + ")");
    }

    /** Asserts a JSON object carries exactly the record's components: the width rule counts what the producer writes. */
    private static void assertWritesExactlyItsComponents(Class<?> type, JsonNode node) {
        Set<String> written = new TreeSet<>();
        node.fieldNames().forEachRemaining(written::add);
        Set<String> components = new TreeSet<>();
        for (RecordComponent c : type.getRecordComponents()) {
            components.add(c.getName());
        }
        assertEquals(components, written, type.getSimpleName());
    }

    @Test
    void theEnvelopeIsTheWidestRecordsTheEngineCanSerialiseTimesThePositionsItsCodeCanEmit() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> IvRvParameterSet.forVersion(2),
                "V1 is the only parameter set shipped, so an observation carries exactly its four trends");
        // ASCII widths (r2's figures) and the widths with every free char escaped, which is the most the serialiser
        // writes for a contract-valid value: the contracts bound those strings in length only.
        assertEquals(9_685L, widestJson(IvRvReading.class, 1));
        assertEquals(1_557L, widestJson(EarlyWarning.class, 1));
        assertEquals(VolPremiumSessionStore.WIDEST_OBSERVATION_BYTES, widestJson(IvRvReading.class, ESCAPED));
        assertEquals(VolPremiumSessionStore.WIDEST_WARNING_BYTES, widestJson(EarlyWarning.class, ESCAPED));
        // ...and ESCAPED is the serialiser's own maximum per char: every char, and every surrogate pair, measured.
        int widest = 0;
        for (char c = 0; c < Character.MIN_SURROGATE; c++) {
            widest = Math.max(widest, MAPPER.writeValueAsString(String.valueOf(c)).getBytes(StandardCharsets.UTF_8).length - 2);
        }
        for (char c = (char) (Character.MAX_SURROGATE + 1); c != 0; c++) {
            widest = Math.max(widest, MAPPER.writeValueAsString(String.valueOf(c)).getBytes(StandardCharsets.UTF_8).length - 2);
        }
        assertEquals(ESCAPED, widest, "a control char, written as a six-byte escape");
        assertEquals(4, MAPPER.writeValueAsString("😀").getBytes(StandardCharsets.UTF_8).length - 2,
                "a surrogate pair: 4 bytes for 2 chars");
        // The width rule counts exactly the fields the producer writes, at every level of both records...
        JsonNode reading = MAPPER.readTree(readingAt(7141).json());
        assertWritesExactlyItsComponents(IvRvReading.class, reading);
        assertWritesExactlyItsComponents(IvRvReading.IvRvTrend.class, reading.get("trends").get(0));
        assertWritesExactlyItsComponents(IvRvReading.Provenance.class, reading.get("provenance"));
        assertWritesExactlyItsComponents(WarningSummary.class, reading.get("warnings").get(0));
        JsonNode warning = MAPPER.readTree(warnings().get(0).json());
        assertWritesExactlyItsComponents(EarlyWarning.class, warning);
        assertWritesExactlyItsComponents(EarlyWarning.Components.class, warning.get("components"));
        // ...and no record the engine produced is wider.
        assertTrue(readings().stream().allMatch(r -> r.json().length() <= 9_685));
        assertTrue(warnings().stream().allMatch(r -> r.json().length() <= 1_557));

        // The positions: every supported ordinal, plus one full re-publication for epoch overlap; two records per
        // type per observation (IvRvEvidence: an opening, a move, or a resolution with a re-arm), censors included.
        assertEquals(7_560, VolPremiumSessionStore.SUPPORTED_ORDINALS_PER_SESSION);
        assertEquals(15_120, VolPremiumSessionStore.MAX_OBSERVATION_POSITIONS);
        assertEquals(14, EarlyWarningType.values().length);
        assertEquals(2 * 14 * 15_120, VolPremiumSessionStore.MAX_WARNING_POSITIONS);
        assertEquals(423_360, VolPremiumSessionStore.MAX_WARNING_POSITIONS);
        assertEquals(15_120L * 14_015L + 423_360L * 2_037L, VolPremiumSessionStore.ENVELOPE_BYTES);
        assertEquals(1_074_291_120L, VolPremiumSessionStore.ENVELOPE_BYTES);
        // The disk budget covers it with headroom, and the directory must hold every series' file plus one rewrite.
        assertEquals(1_342_177_280L, VolPremiumSessionStore.SERIES_DISK_BUDGET_BYTES);
        long headroom = VolPremiumSessionStore.SERIES_DISK_BUDGET_BYTES - VolPremiumSessionStore.ENVELOPE_BYTES;
        assertEquals(267_886_160L, headroom);
        assertTrue(headroom * 100 > 24L * VolPremiumSessionStore.ENVELOPE_BYTES, "at least 24% headroom");
        assertEquals(3L * 1_342_177_280L + 2L * 65_536L, VolPremiumSessionStore.DISK_REQUIRED_BYTES);
        assertTrue(VolPremiumSessionStore.DISK_REQUIRED_BYTES < 4L << 30, "a 4 GiB volume holds it");
    }

    // ----- the whole envelope, through the production store -----------------------------------------------------

    /** The record's text padded with insignificant whitespace to exactly {@code width} bytes (ASCII records only). */
    static String padded(String json, long width) {
        assertTrue(json.length() <= width, "a record wider than the envelope's width: " + json.length());
        assertEquals(json.length(), json.getBytes(StandardCharsets.UTF_8).length, "precondition: ASCII");
        return "{" + " ".repeat((int) (width - json.length())) + json.substring(1);
    }

    /** Every free string at its bound (ASCII), then padded to {@code width}. */
    static String widened(String json, long width, boolean observation) {
        String wide = edit(json, n -> {
            n.put("codeVersion", "v".repeat(IvRvReading.MAX_VERSION_CHARS));
            if (observation) {
                ObjectNode provenance = (ObjectNode) n.get("provenance");
                provenance.put("chainTopic", "c".repeat(IvRvReading.Provenance.MAX_TOPIC_CHARS));
                provenance.put("spotTopic", "s".repeat(IvRvReading.Provenance.MAX_TOPIC_CHARS));
                if (provenance.hasNonNull("chainFeedEpochId")) {
                    provenance.put("chainFeedEpochId", "e".repeat(IvRvReading.Provenance.MAX_EPOCH_ID_CHARS));
                }
            }
        });
        return padded(wide, width);
    }

    /** The stream's observation for ordinal {@code first + i}: row {@code i} of the stream, moved there. */
    static Row atOrdinal(List<Row> stream, int i, long first) {
        Row source = stream.get(i % stream.size());
        long shift = first + i - longField(source.json(), "frameSeq");
        return shift == 0 ? source : VolPremiumFixtures.shiftedObservation(source, shift);
    }

    /** The raw evidence that scores a type at its V1 maximum, in the type's own direction. */
    private static EarlyWarning.Components strongest(EarlyWarningType type) {
        double sign = type.direction() == EarlyWarningDirection.DOWN ? -1.0 : 1.0;
        double change = sign * 10.0;
        double change1m = type.primaryHorizonMs() == IvRvReading.HORIZON_1M_MS ? change : sign * 10.0;
        double corroborating = type.corroboratingSign() < 0 ? -10.0 : 10.0;
        return EarlyWarning.Components.derive(type, change1m, change, change / (type.primaryHorizonMs() / 60_000.0),
                sign * 10.0, corroborating, 10, null, 0, IvRvParameterSet.V1);
    }

    /**
     * The engine's worst case at one observation, as text templates: for EACH of the 14 types, the CONFIRMED
     * resolution of the episode that opened one cadence earlier and the re-arming opening of the next episode on
     * the same observation (IvRvEvidence 285 and 300) — two records per type. Built once through the contract's
     * constructor at a reference ordinal, then moved by replacing its instants and ordinals.
     */
    private static final class Worst {
        private static final String SEQ = "@SEQ@";
        private static final String AS_OF = "@ASOF@";
        private static final String OPENED_AT = "@OPENAT@";
        private static final String OPENED_SEQ = "@OPENSEQ@";
        private final List<String[]> templates = new ArrayList<>();   // {template, resolution? "1" : "0"}

        Worst() throws Exception {
            IvRvParameterSet p = IvRvParameterSet.V1;
            long k = 6_840L;
            long asOf = SESSION_MIDNIGHT_MS + k * 5_000L;
            List<EarlyWarning> built = new ArrayList<>();
            for (EarlyWarningType type : EarlyWarningType.values()) {
                EarlyWarning.Components c = strongest(type);
                double s = c.strengthUnder(p);
                EarlyWarningState opens = p.stateFor(s, EarlyWarningState.NONE);
                assertEquals(EarlyWarningState.STRONG, opens, type + " scores STRONG: " + s);
                built.add(new EarlyWarning(EarlyWarning.CURRENT_SCHEMA_VERSION, "SPX", SESSION,
                        EarlyWarning.episodeId("SPX", SESSION, type, k - 1), type, type.direction(),
                        EarlyWarningState.STRONG, EarlyWarningState.NONE, s, s, c, TimeOfDayNormalisation.UNCALIBRATED,
                        asOf, k, asOf - 5_000L, k - 1, SESSION_MIDNIGHT_MS + 1L, EpisodeOutcome.CONFIRMED, 5_000L,
                        p.version(), p.contentHash(), "v".repeat(IvRvReading.MAX_VERSION_CHARS)));
                built.add(new EarlyWarning(EarlyWarning.CURRENT_SCHEMA_VERSION, "SPX", SESSION,
                        EarlyWarning.episodeId("SPX", SESSION, type, k), type, type.direction(),
                        EarlyWarningState.NONE, opens, s, s, c, TimeOfDayNormalisation.UNCALIBRATED,
                        asOf, k, asOf, k, SESSION_MIDNIGHT_MS + 1L, null, null,
                        p.version(), p.contentHash(), "v".repeat(IvRvReading.MAX_VERSION_CHARS)));
            }
            for (EarlyWarning w : built) {
                boolean resolution = w.outcome() != null;
                String json = MAPPER.writeValueAsString(w);
                json = VolPremiumFixtures.rawReplace(json, "\"frameSeq\":" + k + ",", "\"frameSeq\":" + SEQ + ",");
                json = VolPremiumFixtures.rawReplace(json, "\"asOfMs\":" + asOf + ",", "\"asOfMs\":" + AS_OF + ",");
                json = VolPremiumFixtures.rawReplace(json, "\"openedAtMs\":" + (resolution ? asOf - 5_000L : asOf) + ",",
                        "\"openedAtMs\":" + OPENED_AT + ",");
                json = VolPremiumFixtures.rawReplace(json, "\"openedFrameSeq\":" + (resolution ? k - 1 : k) + ",",
                        "\"openedFrameSeq\":" + OPENED_SEQ + ",");
                json = VolPremiumFixtures.rawReplace(json, "|" + (resolution ? k - 1 : k) + "\"", "|" + OPENED_SEQ + "\"");
                templates.add(new String[] {json, resolution ? "1" : "0"});
            }
        }

        /** The 28 records at ordinal {@code k} and instant {@code asOf}, in the store's replay order. */
        List<Row> at(long k, long asOf) {
            List<String[]> rendered = new ArrayList<>(templates.size());
            for (String[] t : templates) {
                boolean resolution = "1".equals(t[1]);
                long openedSeq = resolution ? k - 1 : k;
                String json = t[0].replace(SEQ, Long.toString(k)).replace(AS_OF, Long.toString(asOf))
                        .replace(OPENED_AT, Long.toString(resolution ? asOf - 5_000L : asOf))
                        .replace(OPENED_SEQ, Long.toString(openedSeq));
                String type = json.substring(json.indexOf("\"type\":\"") + 8);
                type = type.substring(0, type.indexOf('"'));
                String episodeId = "SPX|" + SESSION + "|" + type + "|" + openedSeq;
                int rank = resolution
                        ? VolPremiumSessionStore.transitionRank(EarlyWarningState.STRONG, EarlyWarningState.NONE)
                        : VolPremiumSessionStore.transitionRank(EarlyWarningState.NONE, EarlyWarningState.STRONG);
                rendered.add(new String[] {episodeId, String.format("%03d", rank), json});
            }
            rendered.sort(Comparator.<String[], String>comparing(r -> r[0]).thenComparing(r -> r[1]));
            List<Row> out = new ArrayList<>(rendered.size());
            for (String[] r : rendered) {
                out.add(new Row(r[0], padded(r[2], VolPremiumSessionStore.WIDEST_WARNING_BYTES)));
            }
            return out;
        }
    }

    /** A digest of records joined by commas, as a page writes them. */
    private static final class Joined {
        final MessageDigest digest;
        long count;

        Joined() throws Exception {
            digest = MessageDigest.getInstance("SHA-256");
        }

        void add(byte[] record) {
            if (count++ > 0) {
                digest.update((byte) ',');
            }
            digest.update(record);
        }

        byte[] sum() {
            return digest.digest();
        }
    }

    /** An OutputStream that only digests what is written to it. */
    private static OutputStream digesting(MessageDigest digest) {
        return new OutputStream() {
            @Override
            public void write(int b) {
                digest.update((byte) b);
            }

            @Override
            public void write(byte[] b, int off, int len) {
                digest.update(b, off, len);
            }
        };
    }

    @Test
    void theWholeDerivedEnvelopeIsAdmittedAndReplayedCompletelyAndInOrderThroughTheDiskLog() throws Exception {
        // EVERY position the envelope allows, at the widest width: 7,560 ordinals on two epochs (15,120
        // observations), and at each observation the engine's worst case for all 14 types, a CONFIRMED resolution and
        // a re-arm (423,360 transitions). Through the PRODUCTION store (no-argument constructor), then replayed from
        // its disk log by the socket walk and by the REST page: all of it, in order, byte for byte.
        List<Row> stream = readings();
        long epoch = longField(stream.get(0).json(), "measurementEpochMs");
        Worst worst = new Worst();
        VolPremiumSessionStore store = new VolPremiumSessionStore();
        Joined observations = new Joined();
        Joined transitions = new Joined();
        long offset = 0;
        long lastEvent = 0;
        long nowMs = SESSION_MIDNIGHT_MS + 20_160L * 5_000L - 4_000L;   // 03:59:56 the next day: every record is past
        try {
            for (long[] block : new long[][] {{6_840L, 4_680L}, {86_400_000L / 5_000L, 2_880L}}) {
                for (int i = 0; i < block[1]; i++) {
                    long k = block[0] + i;
                    Row base = atOrdinal(stream, i, block[0]);
                    String onFirst = widened(base.json(), VolPremiumSessionStore.WIDEST_OBSERVATION_BYTES, true);
                    // The second epoch at this ordinal: the engine's first (warming) frame moved here, whose trends have
                    // no reference yet — the contract refuses a change that crosses an epoch — on an epoch 1 ms earlier.
                    Row warming = VolPremiumFixtures.shiftedObservation(stream.get(0), k - 6_840L);
                    assertEquals(base.key(), warming.key(), "precondition: the same ordinal");
                    String onEarlier = widened(with(warming.json(), "measurementEpochMs", epoch - 1L),
                            VolPremiumSessionStore.WIDEST_OBSERVATION_BYTES, true);
                    for (String json : List.of(onEarlier, onFirst)) {   // replay order: the earlier epoch first
                        Admission a = store.acceptObservation("DATABENTO", base.key(), 0, offset++, json, nowMs);
                        assertTrue(a.admitted(), base.key() + ": " + a.refusal());
                        observations.add(json.getBytes(StandardCharsets.UTF_8));
                    }
                    long t = SESSION_MIDNIGHT_MS + k * 5_000L;
                    lastEvent = Math.max(lastEvent, longField(base.json(), "eventTimeMs"));
                    for (long asOf : new long[] {t, t + 1_000L}) {   // one group per observation of the ordinal
                        for (Row w : worst.at(k, asOf)) {
                            Admission a = store.acceptWarning("DATABENTO", w.key(), 0, offset++, w.json(), nowMs);
                            assertTrue(a.admitted(), w.key() + " at " + k + ": " + a.refusal());
                            transitions.add(w.json().getBytes(StandardCharsets.UTF_8));
                        }
                    }
                }
            }
            assertTrue(lastEvent < nowMs);
            assertEquals(VolPremiumSessionStore.MAX_OBSERVATION_POSITIONS, store.heldObservations());
            assertEquals(VolPremiumSessionStore.MAX_WARNING_POSITIONS, store.heldWarnings());
            assertEquals(VolPremiumSessionStore.ENVELOPE_BYTES, store.heldBytes(), "the envelope, at its widest");
            assertEquals(VolPremiumSessionStore.ENVELOPE_BYTES, store.logFileBytes(), "and nothing superseded");
            assertTrue(store.heldBytes() <= VolPremiumSessionStore.SERIES_DISK_BUDGET_BYTES);
            assertEquals(0L, store.refusals(Stream.OBSERVATION, Refusal.SESSION_BUDGET)
                    + store.refusals(Stream.WARNING, Refusal.SESSION_BUDGET));

            // The heap: what the JVM reports for every object of the index, within what it is charged, within the budget.
            Instrumentation jvm = net.bytebuddy.agent.ByteBuddyAgent.install();
            long measured = 0;
            long chunks = 0;
            for (Object o : store.indexObjectsForTest()) {
                measured += jvm.getObjectSize(o);
                chunks += o instanceof VolPremiumSessionStore.SlotList.Chunk ? 1 : 0;
            }
            measured += 2 * (16 + 4 * (long) Math.ceil(chunks * 1.5 + 10));   // the two directories' arrays, bounded
            assertTrue(measured <= store.indexBytes(), "measured " + measured + " > charged " + store.indexBytes());
            assertEquals(VolPremiumSessionStore.SERIES_INDEX_BUDGET_BYTES, store.indexBytes(), "the caps, exactly");
            System.out.println("INFO vol-premium envelope: " + store.heldBytes() + " bytes on disk, index measured "
                    + measured + " bytes of heap (" + (measured / (15_120 + 423_360)) + " per position), charged "
                    + store.indexBytes());

            // The socket walk, from the log: every record once, in order, byte for byte.
            Joined walkedObservations = new Joined();
            Joined walkedTransitions = new Joined();
            Position at = null;
            for (Item item = store.next(null, nowMs); item != null; item = store.next(at, nowMs)) {
                assertTrue(at == null || at.compareTo(item.position()) < 0, "strictly in replay order");
                (item.position().phase() == Position.OBSERVATIONS ? walkedObservations : walkedTransitions)
                        .add(item.json().getBytes(StandardCharsets.UTF_8));
                at = item.position();
            }
            byte[] expectedObservations = observations.sum();
            byte[] expectedTransitions = transitions.sum();
            assertEquals(observations.count, walkedObservations.count);
            assertEquals(transitions.count, walkedTransitions.count);
            assertArrayEquals(expectedObservations, walkedObservations.sum(), "the observations, verbatim, in order");
            assertArrayEquals(expectedTransitions, walkedTransitions.sum(), "the transitions, verbatim, in order");

            // The REST page, streamed in chunks from the same log: the same bytes, and a whole session.
            VolPremiumSessionStore.Page page = store.page("DATABENTO|SPX", () -> nowMs);
            MessageDigest pageObservations = MessageDigest.getInstance("SHA-256");
            MessageDigest pageTransitions = MessageDigest.getInstance("SHA-256");
            page.writeObservations(digesting(pageObservations));
            page.writeWarnings(digesting(pageTransitions));
            assertArrayEquals(expectedObservations, pageObservations.digest());
            assertArrayEquals(expectedTransitions, pageTransitions.digest());
            assertTrue(page.retention().complete());

            // The caps bind at exactly the envelope: one more position of either stream is refused, loudly.
            Row third = atOrdinal(stream, 0, 6_840L);
            Admission beyond = store.acceptObservation("DATABENTO", third.key(), 0, offset++,
                    with(third.json(), "measurementEpochMs", epoch - 2L), nowMs);
            assertEquals(Refusal.SESSION_BUDGET, beyond.refusal(), "a third epoch at a full window");
            Row oneMore = worst.at(6_840L, SESSION_MIDNIGHT_MS + 6_840L * 5_000L + 2_000L).get(0);
            assertEquals(Refusal.SESSION_BUDGET,
                    store.acceptWarning("DATABENTO", oneMore.key(), 0, offset++, oneMore.json(), nowMs).refusal());
        } finally {
            store.close();
        }
    }

    @Test
    void codexRoundThreesReproductionIsAdmittedInFull() throws Exception {
        // Codex gateway r3: the r2 window (7,560 ordinals + 24 epoch breaks of 12, 7,848 observations at 9,685
        // chars, charged 78,481,024 bytes on the heap) then warnings; the 2,475th warning and a later observation on a
        // new epoch at ordinal 20159 were refused SESSION_BUDGET. Every one of them is now admitted.
        List<Row> stream = readings();
        List<Row> observations = new ArrayList<>();
        for (int i = 0; i < 4_680; i++) {
            observations.add(atOrdinal(stream, i, 6_840L));
        }
        for (int i = 0; i < 2_880; i++) {
            observations.add(atOrdinal(stream, i, 86_400_000L / 5_000L));
        }
        for (int b = 0; b < 24; b++) {
            Row moved = VolPremiumFixtures.shiftedObservation(stream.get(0), 100L + 150L * b);
            Row restarted = new Row(moved.key(), with(moved.json(), "measurementEpochMs", longField(moved.json(), "eventTimeMs")));
            for (int j = 0; j < 12; j++) {
                observations.add(j == 0 ? restarted : VolPremiumFixtures.shiftedObservation(restarted, j));
            }
        }
        assertEquals(7_848, observations.size());
        long nowMs = longField(observations.get(7_559).json(), "eventTimeMs") + 1_000L;
        VolPremiumSessionStore store = new VolPremiumSessionStore();
        long offset = 0;
        try {
            for (Row row : observations) {
                assertTrue(store.acceptObservation("DATABENTO", row.key(), 0, offset++,
                        widened(row.json(), 9_685, true), nowMs).admitted(), row.key());
            }
            int warningsAdmitted = 0;
            for (int shift = 0; shift < 334; shift++) {   // 5,010 warnings: well past the 2,475th
                for (Row w : warnings()) {
                    Row moved = shift == 0 ? w : shiftedWarning(w, shift);
                    Admission a = store.acceptWarning("DATABENTO", moved.key(), 0, offset++,
                            widened(moved.json(), 1_557, false), nowMs);
                    assertTrue(a.admitted(), "warning " + (warningsAdmitted + 1) + ": " + a.refusal());
                    warningsAdmitted++;
                }
            }
            assertEquals(5_010, warningsAdmitted);
            Row last = VolPremiumFixtures.shiftedObservation(stream.get(0), 20_159L - 6_840L);
            String onNewEpoch = with(last.json(), "measurementEpochMs", longField(last.json(), "eventTimeMs"));
            long lastNow = longField(onNewEpoch, "eventTimeMs") + 1_000L;
            Admission a = store.acceptObservation("DATABENTO", last.key(), 0, offset++, onNewEpoch, lastNow);
            assertTrue(a.admitted(), "ordinal 20159 on a new epoch: " + a.refusal());
            assertEquals(7_849, store.heldObservations());
            assertEquals(5_010, store.heldWarnings());
            assertTrue(store.snapshot("DATABENTO|SPX", lastNow).complete(), "nothing was refused");
            int walked = 0;
            Position at = null;
            for (Item item = store.next(null, lastNow); item != null; item = store.next(at, lastNow)) {
                walked++;
                at = item.position();
            }
            assertEquals(7_849 + 5_010, walked, "and all of it replays");
        } finally {
            store.close();
        }
    }
}
