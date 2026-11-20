package app.feedgateway.whatif;

import app.feedgateway.readable.StrikeDeltaSource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * <b>C-125</b>: the per-strike call and put delta, carried BESIDE the map record and never inside
 * it.
 *
 * <p><b>Why beside.</b> {@link WhatIfController} serves the map record <b>verbatim</b>, byte for
 * byte, on the stated ground that reshaping it would create a second view of a contract that
 * already has one owner &mdash; its Avro schema. The two deltas are not on that record: the
 * gateway holds them from its own tap on {@code options.databento.gex.strike} (C-120). So they
 * travel as a separately named top-level block, {@code strikeDeltas}, and the record's own keys
 * are not touched, not reordered and not re-serialised.
 *
 * <p><b>How "byte for byte" is kept, and why this is a splice and not a Jackson round trip.</b>
 * Parsing the cached record and writing it back out would re-serialise every number and could
 * reorder every key: the bytes a reader got yesterday and the bytes it gets today would differ for
 * reasons that have nothing to do with the board. So the record's text is left exactly as it is and
 * the sibling is inserted before its final brace. The record's bytes are a PREFIX of what is
 * served, and {@code C125SiblingTest} asserts precisely that.
 *
 * <p><b>It says what it is.</b> The block names the topic the figures came from, the producer, and
 * the expiry they were read for &mdash; which is the BOARD's own expiry and not "the newest seen",
 * because the topic carries two expiries at once and the owner trades the 0DTE (the same C-26 rule
 * the map service holds for its board).
 *
 * <p><b>A strike with no delta carries the reason, never a null that reads as zero.</b> A zero
 * delta is a real reading; absence has to look different from it.
 */
final class StrikeDeltasSibling {

    /** The key the block travels under. The page reads this name. */
    static final String KEY = "strikeDeltas";

    /** The topic the figures come from, named on the block so a reader need not ask. */
    static final String SOURCE_TOPIC = "options.databento.gex.strike";

    /** The producer, named for the same reason. */
    static final String PRODUCER = "DATABENTO";

    private StrikeDeltasSibling() {
    }

    /**
     * Build the block for one board's expiry, or null where there is nothing to say.
     *
     * @param deltas       the gateway's store, or null where the tap is not wired
     * @param boardExpiry  the board's OWN expiry, in either spelling
     * @param strikeCents  the strikes the board carries, so the block holds exactly those and not
     *                     the whole chain: the page reads it per row, and a block carrying strikes
     *                     no row shows is bytes on the wire for nobody
     */
    static ObjectNode build(ObjectMapper mapper, StrikeDeltaSource deltas, String boardExpiry,
                            Iterable<Long> strikeCents) {
        if (deltas == null || strikeCents == null) {
            return null;
        }
        ObjectNode strikes = mapper.createObjectNode();
        for (Long cents : strikeCents) {
            if (cents == null) {
                continue;
            }
            StrikeDeltaSource.Deltas d = deltas.at(boardExpiry, cents);
            if (d == null) {
                continue;               // no record for this strike: the page prints a dash
            }
            ObjectNode one = mapper.createObjectNode();
            if (d.call() != null) {
                one.put("callDelta", d.call());
            }
            if (d.put() != null) {
                one.put("putDelta", d.put());
            }
            // The producer's own reason, and only where there is no figure for that side: a
            // reason beside a figure would read as a doubt about a figure the producer published.
            if (d.call() == null && d.reasonFor(true) != null) {
                one.put("callReason", d.reasonFor(true));
            }
            if (d.put() == null && d.reasonFor(false) != null) {
                one.put("putReason", d.reasonFor(false));
            }
            if (d.updatedAtMs() > 0L) {
                one.put("updatedAtMs", d.updatedAtMs());
            }
            if (!one.isEmpty()) {
                strikes.set(String.valueOf(cents), one);
            }
        }
        if (strikes.isEmpty()) {
            return null;                // nothing to carry: no block at all, not an empty one
        }
        ObjectNode block = mapper.createObjectNode();
        block.put("source", SOURCE_TOPIC);
        block.put("producer", PRODUCER);
        if (boardExpiry != null && !boardExpiry.isBlank()) {
            block.put("expiry", boardExpiry);
        }
        block.set("strikes", strikes);
        return block;
    }

    /**
     * Splice the block into the served body, leaving the record's bytes untouched.
     *
     * <p>Returns {@code recordJson} unchanged where there is no block, where the record does not
     * end in a brace, or where the block cannot be serialised: a body that is not the record is
     * worse than a body without the deltas.
     */
    static String attach(ObjectMapper mapper, String recordJson, ObjectNode block) {
        if (recordJson == null || block == null || block.isEmpty()) {
            return recordJson;
        }
        String trimmed = recordJson.stripTrailing();
        int close = trimmed.lastIndexOf('}');
        if (close < 0 || close != trimmed.length() - 1 || !trimmed.startsWith("{")) {
            return recordJson;          // not an object we can extend without reshaping it
        }
        String rendered;
        try {
            rendered = mapper.writeValueAsString(block);
        } catch (JsonProcessingException e) {
            return recordJson;
        }
        // "{...}" -> "{...,"strikeDeltas":{...}}". An EMPTY record object takes no comma.
        String body = trimmed.substring(0, close);
        boolean empty = body.trim().equals("{");
        return body + (empty ? "" : ",") + "\"" + KEY + "\":" + rendered + "}";
    }

    /** The strikes a cached map record carries, in the record's own order. */
    static java.util.List<Long> strikeCentsOf(JsonNode record) {
        java.util.List<Long> out = new java.util.ArrayList<>();
        if (record == null) {
            return out;
        }
        JsonNode strikes = record.path("strikes");
        if (!strikes.isArray()) {
            return out;
        }
        for (JsonNode s : strikes) {
            JsonNode cents = s.path("strikeCents");
            if (cents.isNumber()) {
                out.add(cents.longValue());
            }
        }
        return out;
    }
}
