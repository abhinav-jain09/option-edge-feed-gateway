package app.feedgateway;

import static app.feedgateway.VolPremiumFixtures.CANONICAL_READING_SHA256;
import static app.feedgateway.VolPremiumFixtures.CANONICAL_WARNING_SHA256;
import static app.feedgateway.VolPremiumFixtures.FIXTURE_NOW_MS;
import static app.feedgateway.VolPremiumFixtures.MAPPER;
import static app.feedgateway.VolPremiumFixtures.READINGS_TSV_SHA256;
import static app.feedgateway.VolPremiumFixtures.Row;
import static app.feedgateway.VolPremiumFixtures.SESSION;
import static app.feedgateway.VolPremiumFixtures.SESSION_LAST_INSTANT_MS;
import static app.feedgateway.VolPremiumFixtures.SESSION_MIDNIGHT_MS;
import static app.feedgateway.VolPremiumFixtures.WARNINGS_TSV_SHA256;
import static app.feedgateway.VolPremiumFixtures.canonicalReading;
import static app.feedgateway.VolPremiumFixtures.canonicalWarning;
import static app.feedgateway.VolPremiumFixtures.edit;
import static app.feedgateway.VolPremiumFixtures.longField;
import static app.feedgateway.VolPremiumFixtures.rawReplace;
import static app.feedgateway.VolPremiumFixtures.readingAt;
import static app.feedgateway.VolPremiumFixtures.readings;
import static app.feedgateway.VolPremiumFixtures.readingsTsvBytes;
import static app.feedgateway.VolPremiumFixtures.referenceSession;
import static app.feedgateway.VolPremiumFixtures.resource;
import static app.feedgateway.VolPremiumFixtures.sha256;
import static app.feedgateway.VolPremiumFixtures.shiftedWarning;
import static app.feedgateway.VolPremiumFixtures.warnings;
import static app.feedgateway.VolPremiumFixtures.with;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.feedgateway.VolPremiumSessionStore.Admission;
import app.feedgateway.VolPremiumSessionStore.Item;
import app.feedgateway.VolPremiumSessionStore.Position;
import app.feedgateway.VolPremiumSessionStore.Refusal;
import app.feedgateway.VolPremiumSessionStore.Snapshot;
import app.feedgateway.VolPremiumSessionStore.Stream;
import com.fasterxml.jackson.databind.JsonNode;
import com.optionsedge.contracts.volpremium.IvRvReading;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The session store on its own: the admission boundary, the warning transition identity, the enforced
 * byte budget and its heap bound, the one replay order, the repair primitive and the session boundary.
 * The gateway-level behaviour — keys, replacement, epochs, rollover, replay, forwarding — is exercised
 * through the gateway itself in FeedGatewayServiceTest.
 */
class VolPremiumSessionStoreTest {

    private static Admission offer(VolPremiumSessionStore store, Row row, long offset) {
        return store.acceptObservation("DATABENTO", row.key(), 0, offset, row.json(), FIXTURE_NOW_MS);
    }

    private static Admission offerWarning(VolPremiumSessionStore store, Row row, long offset) {
        return store.acceptWarning("DATABENTO", row.key(), 0, offset, row.json(), FIXTURE_NOW_MS);
    }

    /** Everything the store would replay, in order, as the verbatim records. */
    private static List<String> walk(VolPremiumSessionStore store, long nowMs) {
        List<String> out = new ArrayList<>();
        Position at = null;
        for (Item item = store.next(null, nowMs); item != null; item = store.next(at, nowMs)) {
            out.add(item.json());
            at = item.position();
        }
        return out;
    }

    private static List<String> json(List<Row> rows) {
        return rows.stream().map(Row::json).toList();
    }

    @Test
    void theCommittedFixturesAreTheEngineOutputByteForByte() throws Exception {
        // Pinned by hash, so no later edit to a fixture can make a test pass against a record the
        // engine never produced. The gzip is transport only: the DECOMPRESSED stream is what is pinned.
        assertEquals(CANONICAL_READING_SHA256, sha256(resource("ivrv-reading.canonical.v2.json")));
        assertEquals(CANONICAL_WARNING_SHA256, sha256(resource("early-warning.canonical.v1.json")));
        assertEquals(READINGS_TSV_SHA256, sha256(readingsTsvBytes()));
        assertEquals(WARNINGS_TSV_SHA256, sha256(resource("warnings.tsv")));
        assertEquals(371, readings().size());
        assertEquals(15, warnings().size());
        // The canonical reading IS the stream's observation at its ordinal, and the canonical warning is
        // the stream's first transition: one engine run, not two unrelated specimens.
        assertEquals(canonicalReading().key(), readingAt(7141).key());
        assertEquals(MAPPER.readTree(canonicalReading().json()), MAPPER.readTree(readingAt(7141).json()));
        assertEquals(canonicalWarning().key(), warnings().get(0).key());
        assertEquals(MAPPER.readTree(canonicalWarning().json()), MAPPER.readTree(warnings().get(0).json()));
    }

    // ----- the admission boundary (r1 finding 1) --------------------------------------------------

    @Test
    void lossyNumericAndScalarCoercionIsRefusedAndNothingIsRetainedOrForwarded() throws Exception {
        // Each variant is the engine's canonical record with ONE value written in a representation the
        // default Jackson reader silently converts: the contract would then validate a number the bytes
        // do not contain, and the gateway would file — and forward VERBATIM — a record whose bytes say
        // something else. Every one must be refused, and nothing of it may be held, replayed or served.
        VolPremiumSessionStore store = new VolPremiumSessionStore();
        Row canonical = canonicalReading();
        String json = canonical.json();
        // The third column is the refusal: a version the bytes do not state as a JSON integer is refused by
        // the version gate itself (SCHEMA_VERSION) before any contract is chosen; everything else by the
        // strict reader of the version it names (MALFORMED).
        String[][] variants = {
                {"a fractional ordinal (filed as 7141, forwarded as 7141.5)",
                        rawReplace(json, "\"frameSeq\":7141,", "\"frameSeq\":7141.5,"), "MALFORMED"},
                {"an ordinal written as a float", rawReplace(json, "\"frameSeq\":7141,", "\"frameSeq\":7141.0,"),
                        "MALFORMED"},
                {"an ordinal written as a string", rawReplace(json, "\"frameSeq\":7141,", "\"frameSeq\":\"7141\","),
                        "MALFORMED"},
                {"a fractional wire version (would pass the v2 gate as 2)",
                        rawReplace(json, "\"schemaVersion\":2,", "\"schemaVersion\":2.5,"), "SCHEMA_VERSION"},
                {"a wire version written as a string", rawReplace(json, "\"schemaVersion\":2,", "\"schemaVersion\":\"2\","),
                        "SCHEMA_VERSION"},
                {"a string component written as a number",
                        rawReplace(json, "\"codeVersion\":\"code-test\"", "\"codeVersion\":7"), "MALFORMED"},
                {"a string component written as a boolean",
                        rawReplace(json, "\"codeVersion\":\"code-test\"", "\"codeVersion\":true"), "MALFORMED"},
                {"the ordinal twice, the first one different",
                        rawReplace(json, "\"frameSeq\":7141,", "\"frameSeq\":7140,\"frameSeq\":7141,"), "MALFORMED"}};
        long offset = 0;
        long malformed = 0;
        for (String[] v : variants) {
            Admission refused = store.acceptObservation("DATABENTO", canonical.key(), 0, offset++, v[1], FIXTURE_NOW_MS);
            assertEquals(Refusal.valueOf(v[2]), refused.refusal(), v[0] + " must be refused");
            malformed += "MALFORMED".equals(v[2]) ? 1 : 0;
        }
        // An enum is its NAME on the wire: ordinal 0 is IV_EXPANSION_DEVELOPING to Jackson's default
        // reader, and the forwarded bytes would carry a 0 where every consumer expects a type.
        Row warning = canonicalWarning();
        String byOrdinal = rawReplace(warning.json(), "\"type\":\"IV_EXPANSION_DEVELOPING\"", "\"type\":0");
        assertEquals(Refusal.MALFORMED,
                store.acceptWarning("DATABENTO", warning.key(), 0, offset++, byOrdinal, FIXTURE_NOW_MS).refusal(),
                "an enum written as its ordinal must be refused");

        assertEquals(malformed, store.refusals(Stream.OBSERVATION, Refusal.MALFORMED));
        assertEquals(variants.length - malformed, store.refusals(Stream.OBSERVATION, Refusal.SCHEMA_VERSION));
        assertEquals(0, store.heldObservations());
        assertEquals(0, store.heldWarnings());
        assertTrue(walk(store, FIXTURE_NOW_MS).isEmpty(), "nothing refused is ever replayed");
        assertNull(store.snapshot("DATABENTO|SPX", FIXTURE_NOW_MS).sessionDate(), "nor served");

        // The same record with its integers written as integers is admitted, and what the store RETAINS —
        // the exact bytes a replay forwards and REST serves — carries the ordinal and version it is filed
        // under, as JSON integers.
        Admission admitted = offer(store, canonical, offset++);
        assertTrue(admitted.admitted());
        Item held = store.next(null, FIXTURE_NOW_MS);
        assertEquals(json, held.json(), "retained verbatim");
        assertEquals(List.of(json), store.snapshot("DATABENTO|SPX", FIXTURE_NOW_MS).observations());
        JsonNode forwarded = MAPPER.readTree(held.json());
        assertTrue(forwarded.get("frameSeq").isIntegralNumber());
        assertEquals(admitted.position().frameSeq(), forwarded.get("frameSeq").longValue());
        assertTrue(forwarded.get("schemaVersion").isInt());
        assertEquals(IvRvReading.CURRENT_SCHEMA_VERSION, forwarded.get("schemaVersion").intValue());
    }

    // ----- the warning transition identity (r1 finding 2) -----------------------------------------

    /** The stream's first transition: IV_EXPANSION_DEVELOPING opening NONE -> DEVELOPING at ordinal 7141. */
    private static Row opening() {
        return warnings().get(0);
    }

    /**
     * Its resolution ONE SECOND later on the SAME ordinal: the accumulator restarted inside the cadence
     * window (a new measurement epoch), and a new epoch inside an episode data-censors it (§40.10).
     */
    private static Row dataCensoredOneSecondLater() {
        Row opening = opening();
        long t = longField(opening.json(), "asOfMs");
        return new Row(opening.key(), edit(opening.json(), n -> {
            n.put("previousState", "DEVELOPING");
            n.put("state", "NONE");
            n.put("outcome", "DATA_CENSORED");
            n.put("asOfMs", t + 1_000L);
            n.put("durationMs", 1_000L);
            n.put("measurementEpochMs", t + 1_000L);
        }));
    }

    /**
     * The NEXT episode of the same type, re-armed on the very observation that resolved the first (the
     * contract's re-arming rule). It opened at ordinal 7141 too, so its id is the SAME episode id.
     */
    private static Row reArmedOpeningOnTheSameObservation() {
        Row opening = opening();
        long t = longField(opening.json(), "asOfMs");
        return new Row(opening.key(), edit(opening.json(), n -> {
            n.put("asOfMs", t + 1_000L);
            n.put("openedAtMs", t + 1_000L);
            n.put("measurementEpochMs", t + 1_000L);
        }));
    }

    @Test
    void anOpeningAndItsDataCensoredResolutionOneSecondLaterOnTheSameOrdinalAreBothHeld() {
        VolPremiumSessionStore store = new VolPremiumSessionStore();
        Row opening = opening();
        Row censored = dataCensoredOneSecondLater();
        assertEquals(7141L, longField(opening.json(), "frameSeq"));
        assertEquals(longField(opening.json(), "frameSeq"), longField(censored.json(), "frameSeq"),
                "precondition: the same ordinal");
        assertEquals(opening.key(), censored.key(), "precondition: the same episode id");

        assertTrue(offerWarning(store, opening, 0).admitted());
        Admission second = offerWarning(store, censored, 1);
        assertTrue(second.admitted(), "the contract admits a later INSTANT on the same ordinal: " + second.refusal());
        assertEquals(2, store.heldWarnings(), "two transitions, not one overwriting the other");
        assertEquals(List.of(opening.json(), censored.json()), walk(store, FIXTURE_NOW_MS),
                "the opening, then its resolution, in time order");
        assertEquals(List.of(opening.json(), censored.json()),
                store.snapshot("DATABENTO|SPX", FIXTURE_NOW_MS).warnings());
    }

    @Test
    void aResolutionAndTheReArmedNextEpisodeOnTheSameObservationAreBothHeld() {
        // Why (episodeId, frameSeq, asOfMs) is NOT a transition's identity: this resolution and this
        // opening share all three, and both are transitions the contract admits.
        VolPremiumSessionStore store = new VolPremiumSessionStore();
        Row opening = opening();
        Row censored = dataCensoredOneSecondLater();
        Row reArmed = reArmedOpeningOnTheSameObservation();
        assertEquals(censored.key(), reArmed.key());
        assertEquals(longField(censored.json(), "frameSeq"), longField(reArmed.json(), "frameSeq"));
        assertEquals(longField(censored.json(), "asOfMs"), longField(reArmed.json(), "asOfMs"),
                "precondition: the same (episodeId, frameSeq, asOfMs)");

        assertTrue(offerWarning(store, opening, 0).admitted());
        assertTrue(offerWarning(store, censored, 1).admitted());
        Admission third = offerWarning(store, reArmed, 2);
        assertTrue(third.admitted(), "the re-armed opening is contract-valid: " + third.refusal());
        assertEquals(3, store.heldWarnings());
        assertEquals(List.of(opening.json(), censored.json(), reArmed.json()), walk(store, FIXTURE_NOW_MS),
                "the resolution sorts before the opening of the episode that re-armed on it");
    }

    @Test
    void theSameTransitionDeliveredTwiceIsOneTransition() {
        VolPremiumSessionStore store = new VolPremiumSessionStore();
        Row opening = opening();
        Admission first = offerWarning(store, opening, 3);
        Admission again = offerWarning(store, opening, 7);
        assertTrue(again.admitted(), "a later offset of the same transition replaces it");
        assertEquals(first.position(), again.position());
        assertEquals(1, store.heldWarnings());
        assertEquals(Refusal.REPLAYED_OFFSET, offerWarning(store, opening, 5).refusal());
    }

    // ----- the rollout bridge: schemaVersion 1 (runbook "Rollout sequence", step 2) ------------------

    private static Refusal refusal(VolPremiumSessionStore store, String key, String json, long offset) {
        return store.acceptObservation("DATABENTO", key, 0, offset, json, FIXTURE_NOW_MS).refusal();
    }

    private static List<String> fieldNames(String json) throws Exception {
        List<String> names = new ArrayList<>();
        MAPPER.readTree(json).fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Test
    void aV1ObservationIsAdmittedOnItsOwnContractAndKeyAndNeitherVersionBorrowsTheOthersRules() throws Exception {
        VolPremiumSessionStore store = new VolPremiumSessionStore();
        Row v1 = VolPremiumFixtures.v1At(7141);
        Row v2 = canonicalReading();
        assertEquals("SPX|2026-08-27", v1.key(), "precondition: the v1 producer's key, one per session");
        long offset = 0;

        // KEY: each version's own rule, exactly.
        for (String wrong : new String[] {v2.key(), "spx|2026-08-27", "SPX|2026-08-27 ", "SPX|2026-08-28", "SPX", ""}) {
            assertEquals(Refusal.KEY_MISMATCH, refusal(store, wrong, v1.json(), offset++), "v1 under '" + wrong + "'");
        }
        assertEquals(Refusal.KEY_MISMATCH, refusal(store, null, v1.json(), offset++), "a keyless v1 record");
        assertEquals(Refusal.KEY_MISMATCH, refusal(store, v1.key(), v2.json(), offset++), "v2 under the v1 key");
        assertEquals(8L, store.refusals(Stream.OBSERVATION, Refusal.KEY_MISMATCH));

        // CONTRACT: each version's own constructor. A v2 record missing a v2-only field is refused although its
        // sixteen v1 fields are valid: v2 never falls back to v1's contract.
        assertEquals(Refusal.MALFORMED, refusal(store, v2.key(), VolPremiumFixtures.without(v2.json(), "trends"), offset++));
        String json = v1.json();
        String[][] brokenV1 = {
                {"an ordinal that disagrees with its own timestamp", with(json, "frameSeq", 7140)},
                {"a coverage outside [0,1]", with(json, "gridCoverage", 2)},
                {"a measurement epoch from another day",
                        with(json, "measurementEpochMs", VolPremiumFixtures.V1_EPOCH_MS - 86_400_000L)},
                {"a cadence outside the contract's bounds", with(json, "frameCadenceMs", 99)},
                {"a zero implied vol", with(json, "atmIvPct", 0)},
                {"a spread that is not atmIvPct - realisedVolPct", with(json, "impliedMinusRealisedPct", 999)},
                {"an unknown baseline mode", with(json, "baselineMode", "CALIBRATED")},
                {"trailing tokens", json + "{}"}};
        for (String[] c : brokenV1) {
            assertEquals(Refusal.MALFORMED, refusal(store, v1.key(), c[1], offset++), c[0] + " must be refused");
        }
        // Every v1 field must be PRESENT, and no primitive an explicit null — schemaVersion itself is the gate's.
        List<String> fields = fieldNames(json);
        assertEquals(16, fields.size(), "the v1 reading has 16 fields");
        for (String field : fields) {
            assertEquals("schemaVersion".equals(field) ? Refusal.SCHEMA_VERSION : Refusal.MALFORMED,
                    refusal(store, v1.key(), VolPremiumFixtures.without(json, field), offset++),
                    "a v1 reading missing " + field);
        }
        String[] primitives = {"schemaVersion", "eventTimeMs", "gridCoverage", "maxContiguousGapSlots",
                "returnsObserved", "measurementEpochMs", "frameSeq", "frameCadenceMs"};
        for (String field : primitives) {
            assertEquals("schemaVersion".equals(field) ? Refusal.SCHEMA_VERSION : Refusal.MALFORMED,
                    refusal(store, v1.key(), VolPremiumFixtures.withNull(json, field), offset++),
                    "an explicit null " + field);
        }
        // ABSENT is not null, even where null is valid. A warming v1 record's realised side is null; without the
        // field it would deserialise to that same null, pass the contract, and be forwarded verbatim WITHOUT the
        // field. For v1 this is the one hole FAIL_ON_MISSING_CREATOR_PROPERTIES alone closes: every other
        // missing field is also caught by FAIL_ON_NULL_FOR_PRIMITIVES or by the constructor.
        Row warming = VolPremiumFixtures.v1At(6840);
        assertTrue(warming.json().contains("\"realisedVolPct\":null"), "precondition: the realised side is null");
        for (String nullable : new String[] {"realisedVolPct", "impliedMinusRealisedPct"}) {
            assertEquals(Refusal.MALFORMED, refusal(store, warming.key(),
                    VolPremiumFixtures.without(warming.json(), nullable), offset++),
                    "a warming v1 reading missing its null " + nullable);
        }
        // The 64 KiB wire cap applies to v1 too, although its contract states none.
        assertEquals(Refusal.OVERSIZE, refusal(store, v1.key(),
                with(json, "pad", "x".repeat(IvRvReading.MAX_RECORD_BYTES)), offset++));
        assertEquals(0, store.heldObservations(), "nothing refused is held");
        assertEquals(0L, store.admittedObservations(1));

        // Under its own key it is admitted, filed by (frameSeq, measurementEpochMs), retained verbatim.
        Admission admitted = store.acceptObservation("DATABENTO", v1.key(), 0, offset++, json, FIXTURE_NOW_MS);
        assertTrue(admitted.admitted(), String.valueOf(admitted.refusal()));
        assertEquals(new Position("DATABENTO|SPX", SESSION, Position.OBSERVATIONS, 7141,
                VolPremiumFixtures.V1_EPOCH_MS, ""), admitted.position());
        assertEquals(List.of(json), store.snapshot("DATABENTO|SPX", FIXTURE_NOW_MS).observations());
        assertEquals(1L, store.admittedObservations(1));
        assertEquals(0L, store.admittedObservations(2));

        // A v1 body that also carries v2 fields (a stated transitional limit): v1's contract ignores unknown
        // fields, so it is judged by v1's rules alone — admitted under the v1 key, refused under a v2 key — and
        // relabelled v2 it meets v2's contract, which refuses it.
        String withV2Fields = edit(json, n -> n.putArray("trends"));
        assertTrue(store.acceptObservation("DATABENTO", v1.key(), 0, offset++, withV2Fields, FIXTURE_NOW_MS).admitted());
        assertEquals(Refusal.KEY_MISMATCH, refusal(store, v2.key(), withV2Fields, offset++));
        assertEquals(Refusal.MALFORMED, refusal(store, v2.key(), with(withV2Fields, "schemaVersion", 2), offset++));
        assertEquals(2L, store.admittedObservations(1));
    }

    @Test
    void theVersionIsReadFirstAndStrictlyAndAnUnknownOneIsRefusedBeforeAnyContractIsChosen() throws Exception {
        VolPremiumSessionStore store = new VolPremiumSessionStore();
        Row v1 = VolPremiumFixtures.v1At(7141);
        String json = v1.json();
        String version = "\"schemaVersion\":1,";
        String[][] versions = {
                {"version 0", rawReplace(json, version, "\"schemaVersion\":0,")},
                {"version 3, a future producer", rawReplace(json, version, "\"schemaVersion\":3,")},
                {"version -1", rawReplace(json, version, "\"schemaVersion\":-1,")},
                {"Integer.MAX_VALUE", rawReplace(json, version, "\"schemaVersion\":2147483647,")},
                {"2^32 + 1, which truncates to 1 as an int", rawReplace(json, version, "\"schemaVersion\":4294967297,")},
                {"1.0, the right number but not an integer", rawReplace(json, version, "\"schemaVersion\":1.0,")},
                {"1e0", rawReplace(json, version, "\"schemaVersion\":1e0,")},
                {"a quoted 1", rawReplace(json, version, "\"schemaVersion\":\"1\",")},
                {"true", rawReplace(json, version, "\"schemaVersion\":true,")},
                {"null", rawReplace(json, version, "\"schemaVersion\":null,")},
                {"an object", rawReplace(json, version, "\"schemaVersion\":{\"v\":1},")},
                {"an array", rawReplace(json, version, "\"schemaVersion\":[1],")},
                {"absent", VolPremiumFixtures.without(json, "schemaVersion")}};
        long offset = 0;
        for (String[] c : versions) {
            assertEquals(Refusal.SCHEMA_VERSION, refusal(store, v1.key(), c[1], offset++), c[0]);
        }
        assertEquals(versions.length, store.refusals(Stream.OBSERVATION, Refusal.SCHEMA_VERSION));
        // Not ONE parseable JSON object: MALFORMED — a second schemaVersion included, whichever comes first.
        String[][] malformed = {
                {"schemaVersion twice, 2 then 1", rawReplace(json, version, "\"schemaVersion\":2,\"schemaVersion\":1,")},
                {"schemaVersion twice, 1 then 2", rawReplace(json, version, "\"schemaVersion\":1,\"schemaVersion\":2,")},
                {"trailing tokens", json + " 1"},
                {"an array around the record", "[" + json + "]"},
                {"a bare number", "1"},
                {"a JSON null", "null"},
                {"truncated", "{\"schemaVersion\":1,"}};
        for (String[] c : malformed) {
            assertEquals(Refusal.MALFORMED, refusal(store, v1.key(), c[1], offset++), c[0]);
        }
        assertEquals(malformed.length, store.refusals(Stream.OBSERVATION, Refusal.MALFORMED));
        assertEquals(0, store.heldObservations());
        assertTrue(store.metricsText().contains("gateway_vol_premium_refused_total{stream=\"ivrv\",reason=\"SCHEMA_VERSION\"} "
                + versions.length + "\n"), "counted, so an unknown producer is never silent");
        // The gate itself, pinned.
        assertEquals(1, VolPremiumSessionStore.wireSchemaVersion(json));
        assertEquals(2, VolPremiumSessionStore.wireSchemaVersion(canonicalReading().json()));
        assertEquals(VolPremiumSessionStore.NO_SCHEMA_VERSION,
                VolPremiumSessionStore.wireSchemaVersion(rawReplace(json, version, "\"schemaVersion\":1.0,")));
    }

    @Test
    void aV1ObservationGetsEveryProtectionOfTheStrictReader() {
        // Gateway main read v1 leniently (the missing-field and null-primitive checks only). v1 is forwarded
        // VERBATIM now, like v2, so it gets the same strict reader: the bytes must say what the contract validated.
        VolPremiumSessionStore store = new VolPremiumSessionStore();
        Row v1 = VolPremiumFixtures.v1At(7141);
        String json = v1.json();
        long returns = longField(json, "returnsObserved");
        String[][] variants = {
                {"a fractional ordinal (filed as 7141, forwarded as 7141.5)", edit(json, n -> n.put("frameSeq", 7141.5))},
                {"an ordinal written as a float", edit(json, n -> n.put("frameSeq", 7141.0))},
                {"an ordinal written as a string", edit(json, n -> n.put("frameSeq", "7141"))},
                {"a count written as a string", edit(json, n -> n.put("returnsObserved", String.valueOf(returns)))},
                {"a string component written as a number", edit(json, n -> n.put("codeVersion", 7))},
                {"a string component written as a boolean", edit(json, n -> n.put("codeVersion", true))},
                // FAIL_ON_NUMBERS_FOR_ENUMS has no v1 counterpart: IvRvReadingV1 has no enum component. Its
                // baselineMode is a String, which the textual-coercion rule guards.
                {"the baseline mode written as a number", edit(json, n -> n.put("baselineMode", 0))},
                {"the ordinal twice, the first one different",
                        rawReplace(json, "\"frameSeq\":7141,", "\"frameSeq\":7140,\"frameSeq\":7141,")},
                {"trailing tokens", json + "{}"}};
        long offset = 0;
        for (String[] c : variants) {
            assertEquals(Refusal.MALFORMED, refusal(store, v1.key(), c[1], offset++), c[0] + " must be refused");
        }
        assertEquals(variants.length, store.refusals(Stream.OBSERVATION, Refusal.MALFORMED));
        assertEquals(0, store.heldObservations());
        assertTrue(walk(store, FIXTURE_NOW_MS).isEmpty(), "nothing refused is ever replayed");
        // The producer's own bytes pass: an integer literal in a double component is the same number, exactly.
        assertTrue(offer(store, v1, offset++).admitted());
        assertEquals(List.of(json), walk(store, FIXTURE_NOW_MS));
    }

    @Test
    void aV1RecordIsChargedByTheSameFormulaAndTheBudgetRefusesPastItAsForV2() {
        Row v1 = VolPremiumFixtures.v1At(7141);
        Row v2 = readingAt(7141);
        assertTrue(VolPremiumSessionStore.COMPACT_STRINGS, "the figures below assume compact strings");
        assertTrue(v1.json().chars().allMatch(c -> c <= 0xFF), "precondition: a Latin-1 record");
        // The formula, exactly: fixed overhead + String object + aligned array of one byte per Latin-1 char.
        long expected = VolPremiumSessionStore.RECORD_OVERHEAD_BYTES + VolPremiumSessionStore.STRING_OBJECT_BYTES
                + ((VolPremiumSessionStore.ARRAY_HEADER_BYTES + v1.json().length() + 7L) & ~7L);
        assertEquals(expected, VolPremiumSessionStore.charge(v1.json(), false));
        assertTrue(v1.json().length() < 1_024, "a genuine v1 record is under 1 KiB: " + v1.json().length());
        assertTrue(4 * VolPremiumSessionStore.charge(v1.json(), false) < VolPremiumSessionStore.charge(v2.json(), false),
                "a fraction of a v2 record's charge for the same window: " + expected + " vs "
                        + VolPremiumSessionStore.charge(v2.json(), false));

        // The session is charged exactly that, and the charge bounds what the JVM itself reports.
        Instrumentation jvm = net.bytebuddy.agent.ByteBuddyAgent.install();
        VolPremiumSessionStore store = new VolPremiumSessionStore();
        Position at = offer(store, v1, 0).position();
        assertEquals(VolPremiumSessionStore.SERIES_OVERHEAD_BYTES + expected, store.heldBytes());
        List<Object> objects = store.retainedObjectsForTest(at);
        assertEquals(4, objects.size());
        long fixed = jvm.getObjectSize(objects.get(0)) + jvm.getObjectSize(objects.get(1))
                + jvm.getObjectSize(objects.get(2));
        long string = jvm.getObjectSize(objects.get(3)) + jvm.getObjectSize(new byte[v1.json().length()]);
        assertTrue(VolPremiumSessionStore.RECORD_OVERHEAD_BYTES >= fixed, "fixed objects " + fixed);
        assertTrue(VolPremiumSessionStore.retainedStringBytes(v1.json()) >= string, "string " + string);

        // The refuse-past-budget policy, unchanged for v1: a budget of exactly ten v1 records holds ten, refuses
        // the eleventh, evicts nothing, and says the session is incomplete.
        List<Row> run = new ArrayList<>();
        for (long seq = 6840; seq < 6852; seq++) {
            run.add(VolPremiumFixtures.v1At(seq));
        }
        long budget = VolPremiumSessionStore.SERIES_OVERHEAD_BYTES;
        for (int i = 0; i < 10; i++) {
            budget += VolPremiumSessionStore.charge(run.get(i).json(), false);
        }
        VolPremiumSessionStore small = new VolPremiumSessionStore(budget, 2);
        PrintStream stdout = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            for (int i = 0; i < 10; i++) {
                assertTrue(offer(small, run.get(i), i).admitted(), "v1 row " + i + " fits the budget");
            }
            assertEquals(Refusal.SESSION_BUDGET, offer(small, run.get(10), 10).refusal());
            // A v2 point at an ordinal v1 holds is a NEW position (another epoch) and needs its own charge.
            assertEquals(Refusal.SESSION_BUDGET, offer(small, readingAt(6845), 11).refusal());
        } finally {
            System.setOut(stdout);
        }
        String log = captured.toString(StandardCharsets.UTF_8);
        assertEquals(1, log.split("ERROR vol-premium: session DATABENTO\\|SPX " + SESSION, -1).length - 1, log);
        assertEquals(json(run.subList(0, 10)), walk(small, FIXTURE_NOW_MS), "nothing held was evicted");
        assertEquals(budget, small.heldBytes());
        Snapshot snapshot = small.snapshot("DATABENTO|SPX", FIXTURE_NOW_MS);
        assertFalse(snapshot.complete());
        assertEquals(2L, snapshot.refusedForBudget());
    }

    @Test
    void aV1AndAV2ReadingOfOneOrdinalOnOneEpochAreOnePositionReplacedByOffsetAndChargedTheDifference() {
        // TRANSITIONAL LIMIT, pinned: two producers form two runs only because their epochs differ. Should both
        // accumulators ever begin on the same millisecond, the two readings of one ordinal are the SAME position,
        // and the later offset replaces the earlier: the within-version rule, charged by the size difference.
        VolPremiumSessionStore store = new VolPremiumSessionStore();
        Row v2 = readingAt(7141);
        long engineEpoch = longField(v2.json(), "measurementEpochMs");
        Row v1 = VolPremiumFixtures.v1Of(v2, engineEpoch);
        Admission first = offer(store, v1, 0);
        assertTrue(first.admitted());
        long overhead = VolPremiumSessionStore.SERIES_OVERHEAD_BYTES;
        assertEquals(overhead + VolPremiumSessionStore.charge(v1.json(), false), store.heldBytes());

        Admission second = offer(store, v2, 1);
        assertTrue(second.admitted());
        assertEquals(first.position(), second.position(), "one position");
        assertEquals(1, store.heldObservations());
        assertEquals(overhead + VolPremiumSessionStore.charge(v2.json(), false), store.heldBytes());
        assertEquals(List.of(v2.json()), walk(store, FIXTURE_NOW_MS));

        // ...and back (a rollback re-publishing that window): the v1 reading replaces the v2 one; the charge shrinks.
        assertTrue(offer(store, v1, 2).admitted());
        assertEquals(overhead + VolPremiumSessionStore.charge(v1.json(), false), store.heldBytes());
        assertEquals(List.of(v1.json()), walk(store, FIXTURE_NOW_MS));

        // The offset and event-time rules are the same across versions.
        assertEquals(Refusal.REPLAYED_OFFSET, offer(store, v2, 1).refusal(), "a lower offset is a replay");
        String v1Later = with(v1.json(), "eventTimeMs", longField(v1.json(), "eventTimeMs") + 1_000L);
        assertTrue(offer(store, new Row(v1.key(), v1Later), 3).admitted());
        assertEquals(Refusal.EVENT_TIME_REGRESSION, offer(store, v2, 4).refusal(),
                "a later offset with an earlier event time is a regression, whichever version carries it");
        assertEquals(3L, store.admittedObservations(1));
        assertEquals(1L, store.admittedObservations(2));
    }

    // ----- capacity and memory (r1 findings 5 and 6) ----------------------------------------------

    @Test
    void theBudgetFitsTheStatedFractionOfTheHeapOrTheGatewayRefusesToStart() {
        assertEquals(48L << 20, VolPremiumSessionStore.SERIES_BUDGET_BYTES);
        assertEquals(2, VolPremiumSessionStore.MAX_SYMBOLS);
        assertEquals(96L << 20, VolPremiumSessionStore.TOTAL_BUDGET_BYTES);
        // Production: -Xmx1536m (JAVA_TOOL_OPTIONS in every feed-gateway overlay). 1/8 of it is 192 MiB.
        assertEquals(192L << 20, VolPremiumSessionStore.requireBudgetFitsHeap(
                VolPremiumSessionStore.TOTAL_BUDGET_BYTES, 1536L << 20));
        long smallest = VolPremiumSessionStore.TOTAL_BUDGET_BYTES * VolPremiumSessionStore.HEAP_FRACTION_DENOMINATOR;
        VolPremiumSessionStore.requireBudgetFitsHeap(VolPremiumSessionStore.TOTAL_BUDGET_BYTES, smallest);
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> VolPremiumSessionStore.requireBudgetFitsHeap(VolPremiumSessionStore.TOTAL_BUDGET_BYTES, smallest - 1L));
        assertTrue(refused.getMessage().startsWith("VOL_PREMIUM_BUDGET_EXCEEDS_HEAP"), refused.getMessage());
        // The production store is built with exactly the declared budget (its constructor ran the check).
        assertEquals(VolPremiumSessionStore.SERIES_BUDGET_BYTES, new VolPremiumSessionStore().seriesBudgetBytes());
    }

    @Test
    void theChargeIsAnUpperBoundOfWhatTheJvmRetainsForARecord() {
        // Held against the sizes the JVM itself reports (java.lang.instrument), not against the formula.
        Instrumentation jvm = net.bytebuddy.agent.ByteBuddyAgent.install();
        VolPremiumSessionStore store = new VolPremiumSessionStore();
        Row reading = canonicalReading();
        Row warning = canonicalWarning();
        Position readingAt = offer(store, reading, 0).position();
        Position warningAt = offerWarning(store, warning, 0).position();
        for (Position held : List.of(readingAt, warningAt)) {
            boolean isWarning = held.phase() == Position.WARNINGS;
            List<Object> objects = store.retainedObjectsForTest(held);
            assertEquals(4, objects.size());
            String json = (String) objects.get(3);
            assertTrue(json.chars().allMatch(c -> c <= 0xFF), "precondition: a Latin-1 record");
            long fixed = jvm.getObjectSize(objects.get(0)) + jvm.getObjectSize(objects.get(1))
                    + jvm.getObjectSize(objects.get(2));
            if (isWarning) {
                String episodeId = ((Position) objects.get(2)).episodeId();
                fixed += jvm.getObjectSize(episodeId) + jvm.getObjectSize(new byte[episodeId.length()]);
            }
            long string = jvm.getObjectSize(json) + jvm.getObjectSize(new byte[json.length()]);
            long overhead = isWarning ? VolPremiumSessionStore.WARNING_OVERHEAD_BYTES
                    : VolPremiumSessionStore.RECORD_OVERHEAD_BYTES;
            assertTrue(overhead >= fixed, "fixed objects " + fixed + " > charged " + overhead);
            assertTrue(VolPremiumSessionStore.retainedStringBytes(json) >= string,
                    "string " + string + " > charged " + VolPremiumSessionStore.retainedStringBytes(json));
            assertTrue(VolPremiumSessionStore.charge(json, isWarning) >= fixed + string);
        }
        // The position's series key and session date are the SESSION's strings, not a copy per record —
        // which is what lets the fixed overhead leave them out.
        Position other = offer(store, readingAt(7142), 1).position();
        Position a = (Position) store.retainedObjectsForTest(readingAt).get(2);
        Position b = (Position) store.retainedObjectsForTest(other).get(2);
        assertSame(a.seriesKey(), b.seriesKey());
        assertSame(a.sessionDate(), b.sessionDate());
        // ...and the envelope arithmetic stated on SERIES_BUDGET_BYTES is this JVM's charge of the
        // engine's LARGEST records.
        assertTrue(VolPremiumSessionStore.COMPACT_STRINGS, "the documented figures assume compact strings");
        int largestReading = readings().stream().mapToInt(r -> r.json().length()).max().orElseThrow();
        int largestWarning = warnings().stream().mapToInt(r -> r.json().length()).max().orElseThrow();
        assertEquals(5_686, largestReading);
        assertEquals(1_052, largestWarning);
        assertEquals(6_000L, VolPremiumSessionStore.charge("x".repeat(largestReading), false));
        assertEquals(1_680L, VolPremiumSessionStore.charge("x".repeat(largestWarning), true));
    }

    @Test
    void theProductionBudgetHoldsAWholeReferenceSessionWithEveryEpochAndHeavyWarnings() {
        // The supported workload, admitted in full through the production store: a whole 09:30-16:00
        // session at the producer's 5 s cadence (4,680 observations), three restarts inside a cadence
        // window (each a SEPARATE point on the same ordinal), and 4,500 warning transitions — the
        // engine's 15 moved to 300 successive ordinals, so six types open on every one of 300 ordinals.
        List<Row> session = referenceSession();
        assertEquals(VolPremiumSessionStore.REFERENCE_OBSERVATIONS_PER_SESSION, session.size());
        long nowMs = longField(session.get(session.size() - 1).json(), "eventTimeMs") + 1_000L;
        VolPremiumSessionStore store = new VolPremiumSessionStore();
        long offset = 0;
        for (Row row : session) {
            Admission a = store.acceptObservation("DATABENTO", row.key(), 0, offset++, row.json(), nowMs);
            assertTrue(a.admitted(), row.key() + ": " + a.refusal());
        }
        for (int index : new int[] {1_000, 2_000, 3_000}) {
            // A restart inside a cadence window: the engine's first (warming) frame, whose trends have no
            // reference yet, moved to this ordinal on a measurement epoch that begins at its own instant.
            Row warming = VolPremiumFixtures.shiftedObservation(readings().get(0), index);
            assertEquals(session.get(index).key(), warming.key(), "precondition: the same ordinal");
            String restarted = with(warming.json(), "measurementEpochMs", longField(warming.json(), "eventTimeMs"));
            Admission a = store.acceptObservation("DATABENTO", warming.key(), 0, offset++, restarted, nowMs);
            assertTrue(a.admitted(), "a new epoch on ordinal " + warming.key() + ": " + a.refusal());
        }
        int transitions = 0;
        for (int shift = 0; shift < 300; shift++) {
            for (Row w : warnings()) {
                Row moved = shift == 0 ? w : shiftedWarning(w, shift);
                Admission a = store.acceptWarning("DATABENTO", moved.key(), 0, offset++, moved.json(), nowMs);
                assertTrue(a.admitted(), moved.key() + ": " + a.refusal());
                transitions++;
            }
        }
        assertEquals(4_683, store.heldObservations(), "every observation, every epoch");
        assertEquals(4_500, transitions);
        assertEquals(4_500, store.heldWarnings(), "every transition, every type");
        Snapshot snapshot = store.snapshot("DATABENTO|SPX", nowMs);
        assertTrue(snapshot.complete());
        assertEquals(0L, store.refusals(Stream.OBSERVATION, Refusal.SESSION_BUDGET));
        assertEquals(0L, store.refusals(Stream.WARNING, Refusal.SESSION_BUDGET));
        assertTrue(store.heldBytes() <= VolPremiumSessionStore.SERIES_BUDGET_BYTES);
        System.out.println("INFO vol-premium reference workload: " + store.heldBytes() + " of "
                + VolPremiumSessionStore.SERIES_BUDGET_BYTES + " bytes ("
                + (100L * store.heldBytes() / VolPremiumSessionStore.SERIES_BUDGET_BYTES) + "%)");
    }

    @Test
    void pastItsBudgetASessionFailsClosedLoudlyAndExplicitlyAndNothingHeldIsEvicted() {
        List<Row> rows = readings();
        long budget = VolPremiumSessionStore.SERIES_OVERHEAD_BYTES;
        for (int i = 0; i < 10; i++) {
            budget += VolPremiumSessionStore.charge(rows.get(i).json(), false);
        }
        VolPremiumSessionStore store = new VolPremiumSessionStore(budget, 2);
        PrintStream stdout = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            for (int i = 0; i < 10; i++) {
                assertTrue(offer(store, rows.get(i), i).admitted(), "row " + i + " fits the budget");
            }
            assertEquals(Refusal.SESSION_BUDGET, offer(store, rows.get(10), 10).refusal());
            assertEquals(Refusal.SESSION_BUDGET, offerWarning(store, warnings().get(0), 0).refusal(),
                    "warnings share the session's budget");
            assertEquals(Refusal.SESSION_BUDGET, offer(store, rows.get(11), 11).refusal());
        } finally {
            System.setOut(stdout);
        }
        // LOUD: one ERROR line for the session, however many records it refuses.
        String log = captured.toString(StandardCharsets.UTF_8);
        assertEquals(1, log.split("ERROR vol-premium: session DATABENTO\\|SPX " + SESSION, -1).length - 1, log);
        // NEVER truncated: what is held is exactly the first ten, unchanged.
        assertEquals(json(rows.subList(0, 10)), walk(store, FIXTURE_NOW_MS));
        assertEquals(budget, store.heldBytes());
        // EXPLICIT: the session says it is no longer whole, to a machine and to an operator.
        Snapshot snapshot = store.snapshot("DATABENTO|SPX", FIXTURE_NOW_MS);
        assertFalse(snapshot.complete());
        assertEquals(3L, snapshot.refusedForBudget());
        assertEquals(budget, snapshot.retainedBytes());
        assertEquals(budget, snapshot.budgetBytes());
        String metrics = store.metricsText();
        assertTrue(metrics.contains("gateway_vol_premium_refused_total{stream=\"ivrv\",reason=\"SESSION_BUDGET\"} 2\n"), metrics);
        assertTrue(metrics.contains("gateway_vol_premium_refused_total{stream=\"warning\",reason=\"SESSION_BUDGET\"} 1\n"), metrics);
        assertTrue(metrics.contains("gateway_vol_premium_sessions_over_budget 1\n"), metrics);

        // A replacement that does not grow the session is not growth: it still lands at the budget.
        Row fifth = rows.get(5);
        String replacement = with(fifth.json(), "eventTimeMs", longField(fifth.json(), "eventTimeMs") + 1_000L);
        assertEquals(fifth.json().length(), replacement.length(), "precondition: the same size");
        assertTrue(store.acceptObservation("DATABENTO", fifth.key(), 0, 20, replacement, FIXTURE_NOW_MS).admitted());
        // ...but one that would grow it past the budget is refused, and the held version stays.
        Row sixth = rows.get(6);
        String grown = with(sixth.json(), "pad", "x".repeat(64));
        assertEquals(Refusal.SESSION_BUDGET,
                store.acceptObservation("DATABENTO", sixth.key(), 0, 21, grown, FIXTURE_NOW_MS).refusal());
        List<String> expected = new ArrayList<>(json(rows.subList(0, 10)));
        expected.set(5, replacement);
        assertEquals(expected, walk(store, FIXTURE_NOW_MS));
    }

    @Test
    void aSeriesBeyondTheSymbolCapIsRefusedAndNeverDisplacesAHeldOne() {
        VolPremiumSessionStore store = new VolPremiumSessionStore(VolPremiumSessionStore.SERIES_BUDGET_BYTES, 1);
        Row spx = readings().get(0);
        assertTrue(offer(store, spx, 0).admitted());

        String ndx = with(spx.json(), "symbol", "NDX");
        Admission refused = store.acceptObservation("DATABENTO", "NDX|" + SESSION + "|6840", 0, 1, ndx,
                FIXTURE_NOW_MS);
        assertEquals(Refusal.SYMBOL_CAP, refused.refusal());
        assertEquals(List.of(spx.json()), walk(store, FIXTURE_NOW_MS), "the held series is untouched");
    }

    @Test
    void aSecondPartitionForTheSameSessionIsRefusedNeverInterleaved() {
        // Fail closed, never reorder: the topic is single-partition by construction, and one session's
        // records arriving from two partitions have no order a replay could deliver them in.
        VolPremiumSessionStore store = new VolPremiumSessionStore();
        assertTrue(offer(store, readings().get(0), 0).admitted());
        Row second = readings().get(1);
        Admission refused = store.acceptObservation("DATABENTO", second.key(), 1, 0, second.json(), FIXTURE_NOW_MS);
        assertEquals(Refusal.FOREIGN_PARTITION, refused.refusal());
        assertEquals(1, store.heldObservations());
    }

    @Test
    void theReplayOrderIsEachSeriesObservationsByOrdinalThenItsWarnings() {
        // Offered in the WORST order — warnings first, observations newest first — so the order that
        // comes out can only be the store's own.
        VolPremiumSessionStore store = new VolPremiumSessionStore();
        long offset = 0;
        for (Row w : warnings()) {
            assertTrue(offerWarning(store, w, offset++).admitted(), w.key());
        }
        List<Row> reversed = new ArrayList<>(readings());
        java.util.Collections.reverse(reversed);
        for (Row r : reversed) {
            assertTrue(offer(store, r, offset++).admitted(), r.key());
        }

        List<String> expected = new ArrayList<>(json(readings()));
        List<Row> byTransition = new ArrayList<>(warnings());
        byTransition.sort(Comparator.<Row>comparingLong(w -> longField(w.json(), "frameSeq"))
                .thenComparingLong(w -> longField(w.json(), "asOfMs"))
                .thenComparing(Row::key));
        expected.addAll(json(byTransition));
        assertEquals(expected, walk(store, FIXTURE_NOW_MS));
    }

    @Test
    void nextChangedHandsOverOnlyVersionsAdmittedAfterThePointAReaderWasInStepUpTo() {
        // The repair primitive the gateway's delivery is built on: behind a cursor, only what changed.
        VolPremiumSessionStore store = new VolPremiumSessionStore();
        List<Row> rows = readings();
        for (int i = 0; i < 10; i++) {
            assertTrue(offer(store, rows.get(i), i).admitted());
        }
        long inStep = store.admissionSeq();
        Position eighth = null;
        for (Item item = store.next(null, FIXTURE_NOW_MS); item != null; item = store.next(item.position(), FIXTURE_NOW_MS)) {
            if (item.json().equals(rows.get(8).json())) {
                eighth = item.position();
            }
        }
        assertEquals(8L + 6840L, eighth.frameSeq(), "precondition: the bound is the ninth point");
        String second = with(rows.get(2).json(), "eventTimeMs", longField(rows.get(2).json(), "eventTimeMs") + 1_000L);
        String seventh = with(rows.get(7).json(), "eventTimeMs", longField(rows.get(7).json(), "eventTimeMs") + 1_000L);
        String fourthRestarted = with(rows.get(4).json(), "measurementEpochMs", longField(rows.get(4).json(), "eventTimeMs"));
        assertTrue(store.acceptObservation("DATABENTO", rows.get(2).key(), 0, 20, second, FIXTURE_NOW_MS).admitted());
        assertTrue(store.acceptObservation("DATABENTO", rows.get(7).key(), 0, 21, seventh, FIXTURE_NOW_MS).admitted());
        Position restartedAt = store.acceptObservation("DATABENTO", rows.get(4).key(), 0, 22, fourthRestarted,
                FIXTURE_NOW_MS).position();
        assertTrue(offer(store, rows.get(10), 23).admitted(), "beyond the bound: never the repair's");

        List<String> changed = new ArrayList<>();
        Position at = null;
        for (Item item = store.nextChanged(null, eighth, p -> inStep, FIXTURE_NOW_MS); item != null;
             item = store.nextChanged(at, eighth, p -> inStep, FIXTURE_NOW_MS)) {
            changed.add(item.json());
            at = item.position();
        }
        assertEquals(List.of(second, fourthRestarted, seventh), changed, "in replay order, and nothing else");

        // A per-stretch threshold: in step up to the LATEST admission for everything up to the restart,
        // up to `inStep` after it — so only the seventh is still owed.
        long latest = store.admissionSeq();
        Position boundary = restartedAt;
        Item only = store.nextChanged(null, eighth, p -> p.compareTo(boundary) <= 0 ? latest : inStep, FIXTURE_NOW_MS);
        assertEquals(seventh, only.json());
        assertNull(store.nextChanged(only.position(), eighth, p -> p.compareTo(boundary) <= 0 ? latest : inStep,
                FIXTURE_NOW_MS));
        assertNotEquals(0L, latest);
    }

    @Test
    void aSessionIsCurrentFromItsMidnightUntilTheContractsAfterHoursAllowance() {
        // The contract's own rule (IvRvReading.requireInstantInSession), so the gateway holds a session
        // exactly as long as a record could still legitimately be filed under it.
        assertFalse(VolPremiumSessionStore.sessionCurrent(SESSION, SESSION_MIDNIGHT_MS - 1L));
        assertTrue(VolPremiumSessionStore.sessionCurrent(SESSION, SESSION_MIDNIGHT_MS));
        assertTrue(VolPremiumSessionStore.sessionCurrent(SESSION, SESSION_LAST_INSTANT_MS));
        assertFalse(VolPremiumSessionStore.sessionCurrent(SESSION, SESSION_LAST_INSTANT_MS + 1L));
    }

    @Test
    void anEndedSessionIsNeitherReplayedNorServedAndIsPurged() {
        VolPremiumSessionStore store = new VolPremiumSessionStore();
        assertTrue(offer(store, readings().get(0), 0).admitted());

        assertEquals(0, store.purge(SESSION_LAST_INSTANT_MS), "still current at its last instant");
        assertEquals(1, walk(store, SESSION_LAST_INSTANT_MS).size());

        // Ended but not yet purged: the readers apply the same rule themselves, so a purge that has not
        // run yet can never let a stale session through.
        assertTrue(walk(store, SESSION_LAST_INSTANT_MS + 1L).isEmpty());
        assertNull(store.snapshot("DATABENTO|SPX", SESSION_LAST_INSTANT_MS + 1L).sessionDate());

        assertEquals(1, store.purge(SESSION_LAST_INSTANT_MS + 1L));
        assertEquals(0, store.heldObservations());
        assertEquals(0L, store.heldBytes());
    }
}
