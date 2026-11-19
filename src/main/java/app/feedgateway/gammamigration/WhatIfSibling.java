package app.feedgateway.gammamigration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Locale;

/**
 * <b>D-72</b>: the what-if board, added to {@code /api/gamma-migration} as an <b>additive sibling
 * object</b> named {@code whatIf}, beside the gamma fields. No existing field is touched and there
 * is no second endpoint.
 *
 * <p><b>Why a sibling and not four keys on each gamma strike row.</b>
 * {@link GammaMigrationController} serves the cached gamma record verbatim and says in writing why:
 * reshaping it there would create a second, silently-drifting view of a contract that already has
 * one owner (its Avro schema). Threading four keys <i>into</i> those rows is exactly that reshape
 * — it would make the gateway re-author the gamma record. A sibling object leaves every byte of
 * the gamma record as its owner wrote it and puts the what-if figures under a name of their own,
 * which is also why the page can render unchanged when the sibling is absent.
 *
 * <p>A second endpoint was the other candidate and was refused: it would make the page join two
 * polls, and the owner's rule is that the page computes nothing.
 *
 * <p><b>Three conditions from D-71, each of them a rule and not a nicety:</b>
 *
 * <ul>
 *   <li><b>Same chain or nothing.</b> A what-if row is taken only when its {@code symbol} and
 *       {@code expiry} match the gamma rows'. Two boards of different expiries carry the same
 *       strike numbers, so a mismatched join would print one expiry's bands against another's
 *       gamma and look entirely plausible.</li>
 *   <li><b>The cycle time travels with it</b>, so the page can say LIVE or STALE. Without it a
 *       stale board is indistinguishable from a current one, and the figures look no different.</li>
 *   <li><b>Absent, not null and not zero.</b> When there is no map record the {@code whatIf} key
 *       is not present at all, and the board renders exactly as it does today. A null would make
 *       the page decide what null means; a zero would be a reading.</li>
 * </ul>
 */
public final class WhatIfSibling {

    /** The key the sibling rides under, beside the gamma fields and never inside them. */
    public static final String KEY = "whatIf";

    private WhatIfSibling() {
    }

    /**
     * The gamma record with the what-if sibling added, or the gamma record <b>unchanged</b>.
     *
     * @param gammaJson  the cached gamma-migration record, exactly as its owner published it
     * @param whatIfJson the cached what-if map record for the same chain, or {@code null}
     * @param symbol     the chain the caller asked for, already defaulted by the controller
     * @param expiry     likewise
     * @return a JSON document to serve. When there is nothing to add, this is the SAME string that
     *         came in — not a re-serialisation of it, so a chain with no map record cannot be
     *         changed even by Jackson's own formatting.
     */
    public static String merge(ObjectMapper mapper, String gammaJson, String whatIfJson,
                               String symbol, String expiry) {
        if (gammaJson == null || gammaJson.isBlank()) {
            return gammaJson;
        }
        if (whatIfJson == null || whatIfJson.isBlank()) {
            return gammaJson;                       // absent, not null: the key is simply not there
        }
        ObjectNode sibling;
        ObjectNode gamma;
        try {
            JsonNode whatIf = mapper.readTree(whatIfJson);
            JsonNode parsedGamma = mapper.readTree(gammaJson);
            if (!whatIf.isObject() || !parsedGamma.isObject()) {
                return gammaJson;
            }
            sibling = siblingFor(mapper, whatIf, symbol, expiry);
            if (sibling == null) {
                return gammaJson;
            }
            gamma = (ObjectNode) parsedGamma;
        } catch (Exception e) {
            // A cache entry this gateway cannot parse is not a reason to fail the page. The gamma
            // record is served as it stands, which is precisely what happened before D-72.
            return gammaJson;
        }
        gamma.set(KEY, sibling);
        try {
            return mapper.writeValueAsString(gamma);
        } catch (Exception e) {
            return gammaJson;
        }
    }

    /**
     * The sibling itself, or {@code null} when this what-if record does not belong to this chain.
     *
     * <p>The chain check is on the record's OWN {@code symbol} and {@code expiry} fields, which
     * exist because of D-73. Before D-73 the map record carried neither, and a gateway could only
     * have assumed the cache key was right — an assumption that is invisible when it is wrong.
     */
    static ObjectNode siblingFor(ObjectMapper mapper, JsonNode whatIf, String symbol,
                                 String expiry) {
        String recordSymbol = text(whatIf, "symbol");
        String recordExpiry = text(whatIf, "expiry");
        if (recordSymbol == null || recordExpiry == null
                || !recordSymbol.equalsIgnoreCase(norm(symbol))
                || !recordExpiry.equals(expiry == null ? null : expiry.trim())) {
            return null;
        }
        JsonNode cycle = child(child(whatIf, "fingerprint"), "valuationTime");
        if (cycle.isMissingNode() || cycle.isNull()) {
            // Without the cycle time the page cannot say LIVE or STALE, and D-71 requires that it
            // can. A board whose age is unknowable is withheld rather than shown as current.
            return null;
        }
        ObjectNode out = mapper.createObjectNode();
        out.put("cycleTime", cycle.asLong());
        out.put("symbol", recordSymbol);
        out.put("expiry", recordExpiry);
        ObjectNode strikes = mapper.createObjectNode();
        for (JsonNode row : whatIf.path("strikes")) {
            ObjectNode four = fourValues(mapper, row);
            if (four != null) {
                strikes.set(Long.toString(child(row, "strikeCents").asLong()), four);
            }
        }
        out.set("strikes", strikes);
        return out;
    }

    /**
     * The four values the owner's screen shows per strike: <b>Reading, Strength, Decay to close
     * and Band</b>.
     *
     * <p>A row missing any of them is <b>left out</b> rather than published with a hole, the same
     * discipline D-74(b) applies one service upstream: a strike that cannot be described is named
     * by its absence, not by a zero that reads like a measurement. The board still renders every
     * other strike.
     */
    static ObjectNode fourValues(ObjectMapper mapper, JsonNode row) {
        JsonNode board = child(row, "board");
        JsonNode band = child(row, "deltaBand");
        JsonNode decay = child(row, "decayToExpiry");
        String reading = text(board, "holdOrAccelerate");
        JsonNode strength = child(board, "strength");
        JsonNode lower = child(band, "lowerEdge");
        JsonNode upper = child(band, "upperEdge");
        JsonNode magnitude = child(decay, "magnitude");
        if (reading == null || !strength.isNumber() || !lower.isNumber() || !upper.isNumber()
                || !magnitude.isNumber()) {
            return null;
        }
        ObjectNode out = mapper.createObjectNode();
        out.put("reading", reading);
        out.put("strength", strength.asDouble());
        ObjectNode decayOut = mapper.createObjectNode();
        decayOut.put("magnitude", magnitude.asDouble());
        String direction = text(decay, "direction");
        if (direction != null) {
            decayOut.put("direction", direction);
        }
        out.set("decayToClose", decayOut);
        ObjectNode bandOut = mapper.createObjectNode();
        bandOut.put("lowerEdge", lower.asDouble());
        bandOut.put("upperEdge", upper.asDouble());
        out.set("band", bandOut);
        return out;
    }

    /**
     * A child node, with an Avro union envelope removed if there is one.
     *
     * <p>Avro's own JSON encoding writes a nullable record as {@code {"DeltaBand": {...}}} and a
     * nullable double as {@code {"double": 1.0}} — the branch's type name wrapping the value.
     * This gateway's {@link app.feedgateway.AvroJson} walks the {@code GenericRecord}'s fields
     * directly and does NOT produce those envelopes, so in production this method finds the plain
     * shape and does nothing.
     *
     * <p>It is here because the failure mode of guessing wrong is <b>silent</b>: every lookup
     * would miss, every strike would be dropped as "missing one of the four", and the board would
     * render four empty columns with no error anywhere. That is indistinguishable from "the map
     * service has not published yet", which is a state the page is built to show. One cheap
     * unwrap costs nothing and removes a whole class of quiet emptiness — including the case
     * where something upstream changes renderer.
     *
     * <p>It unwraps only a single-field object whose key is an Avro type name: either a record
     * name (initial capital) or one of the primitive branch names. A real field named
     * {@code lowerEdge} is lower-case and is never mistaken for an envelope.
     */
    static JsonNode unwrap(JsonNode node) {
        if (node == null || !node.isObject() || node.size() != 1) {
            return node;
        }
        String only = node.fieldNames().next();
        boolean typeName = Character.isUpperCase(only.charAt(0))
                || "double".equals(only) || "long".equals(only) || "int".equals(only)
                || "float".equals(only) || "string".equals(only) || "boolean".equals(only)
                || "bytes".equals(only);
        return typeName ? node.get(only) : node;
    }

    /** A child by name, unwrapped. Never null: an absent child reads as a missing node. */
    private static JsonNode child(JsonNode parent, String field) {
        return unwrap(parent == null ? null : parent.path(field));
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node == null ? null : unwrap(node.get(field));
        if (v == null || v.isNull() || v.isMissingNode()) {
            return null;
        }
        String s = v.isTextual() ? v.textValue() : v.asText();
        return s == null || s.isBlank() ? null : s;
    }

    private static String norm(String symbol) {
        return symbol == null ? null : symbol.trim().toUpperCase(Locale.ROOT);
    }
}
