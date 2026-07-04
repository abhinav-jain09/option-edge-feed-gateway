package app.feedgateway.mtsession.gateway;

import app.feedgateway.GatewaySettings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

/**
 * Joins the two CHAIN-LEVEL dealer-ledger topics — {@code dealer-ledger-profile} (U1–U9 book) and
 * {@code dealer-ledger-state} (session state machine) — into the single {@code dealer-ledger}
 * websocket envelope the option-chain UI consumes. Both source topics carry ONE record per
 * {@code (symbol, expiry)}; neither has a per-strike breakdown, so all book numbers are chain-level.
 *
 * <p>Pure and stateless: give it the latest profile and/or state JSON for one {@code (symbol,expiry)}
 * and it returns the envelope, or {@code null} when there is nothing to publish yet. The caller owns
 * caching/coalescing/broadcast — see {@code FeedGatewayService}.
 *
 * <p><b>Anchor strike.</b> The UI shows one pill on one strike. The anchor is resolved from
 * {@code state.defendedLevel} — which IS a strike value (the state machine compares it directly to
 * strikes) — and falls back to {@code profile.pinCandidateStrike} (also a strike). When an actionable
 * state has NEITHER anchor (a pure call-side ARMED whose session-high anchor is not in the contract),
 * the envelope carries the book with an EMPTY {@code strikes[]}: the UI renders no pill but the data
 * stays live. This is a deliberate refusal to fabricate an anchor; a dedicated backend anchor field
 * would let the call-side pill attach to a strike.
 *
 * <p><b>Action side.</b> CALL/PUT is NOT composed here as copy — it is read from which SELL zone in
 * {@code state.actions[]} is most permissive (CALL_SELL_ZONE ⇒ CALL, PUT_SELL_ZONE ⇒ PUT), i.e. the
 * backend's own decision output. Zone-permission copy is backend-frozen and passed through verbatim.
 */
public final class DealerLedgerJoiner {

    /** SessionState values that surface a pill; everything else renders nothing in the UI. */
    private static final List<String> ACTIONABLE_STATES = List.of("ARMED", "DEFENDED", "ARMING");

    /** Zone-permission strength, most-permissive first, for picking the dominant SELL zone. */
    private static final List<String> PERMISSION_RANK =
            List.of("ACTIVE", "STAGED", "CAUTION", "WAITING", "LIFTED", "NEUTRAL", "CLOSED", "BLOCKED", "FORBIDDEN");

    private DealerLedgerJoiner() {
    }

    /**
     * Build the {@code dealer-ledger} envelope for one {@code (symbol, expiry)}.
     *
     * @param profile  latest dealer-ledger-profile JSON, or null if none seen yet
     * @param state    latest dealer-ledger-state JSON, or null if none seen yet
     * @param source   the binding market-data source (e.g. "DATABENTO"), stamped for contractKey alignment
     * @param uiStaleMs freshness budget the UI may surface
     * @param stale    gateway-computed staleness (event time vs wall clock)
     * @return the envelope JSON, or null if neither input is present
     */
    public static ObjectNode join(ObjectMapper mapper, JsonNode profile, JsonNode state,
                                  String source, long uiStaleMs, boolean stale) {
        if ((profile == null || profile.isMissingNode() || profile.isNull())
                && (state == null || state.isMissingNode() || state.isNull())) {
            return null;
        }
        JsonNode p = (profile == null || profile.isNull()) ? mapper.missingNode() : profile;
        JsonNode s = (state == null || state.isNull()) ? mapper.missingNode() : state;

        String symbol = firstText(s.get("symbol"), p.get("symbol")).toUpperCase();
        String expiry = GatewaySettings.normalizeExpiry(firstText(s.get("expiry"), p.get("expiry")));
        long asOf = Math.max(longOr(s.get("asOfEventTimeMs"), 0L), longOr(p.get("asOfEventTimeMs"), 0L));

        ObjectNode envelope = mapper.createObjectNode();
        envelope.put("type", "dealer-ledger");
        envelope.put("symbol", symbol);
        envelope.put("expiry", expiry);
        envelope.put("marketDataSource", source == null ? "" : source);
        envelope.put("asOfEventTimeMs", asOf);
        envelope.put("uiStaleMs", uiStaleMs);
        envelope.put("stale", stale);

        envelope.set("book", buildBook(mapper, p, s));

        ArrayNode strikes = mapper.createArrayNode();
        ObjectNode entry = buildStrikeEntry(mapper, p, s, asOf, stale);
        if (entry != null) {
            strikes.add(entry);
        }
        envelope.set("strikes", strikes);
        return envelope;
    }

    /** The chain-level dealer book (profile U-metrics + state anchor/actions/quality), UI field names. */
    private static ObjectNode buildBook(ObjectMapper mapper, JsonNode p, JsonNode s) {
        ObjectNode book = mapper.createObjectNode();
        putNumber(book, "netDealerDeltaUsd", p.get("dealerNetDelta"));
        putNumber(book, "netDealerGammaUsd", p.get("netDealerGamma"));
        putText(book, "regime", p.get("regime"));
        putNumber(book, "storedUnwindUsd", p.get("storedUnwindNotional"));
        putNumber(book, "spentUnwindPct", p.get("spentUnwindPct"));
        putNumber(book, "flipLevel", p.get("nearestFlip"));
        putNumber(book, "pinCandidate", p.get("pinCandidateStrike"));
        putNumber(book, "defendedLevel", s.get("defendedLevel"));
        // Quality: profile is authoritative for greeks/inventory; state owns spot quality (and MISSING).
        putText(book, "inventoryConfidence", firstNode(p.get("inventoryConfidence"), s.get("inventoryConfidence")));
        putText(book, "greeksQuality", firstNode(p.get("greeksQuality"), s.get("greeksQuality")));
        putText(book, "spotQuality", firstNode(s.get("spotQuality"), p.get("spotQuality")));
        // Zone permissions verbatim — decision-support states, never orders. Copy is backend-frozen.
        ArrayNode actions = mapper.createArrayNode();
        JsonNode src = s.get("actions");
        if (src != null && src.isArray()) {
            for (JsonNode a : src) {
                ObjectNode out = mapper.createObjectNode();
                putText(out, "zone", a.get("zone"));
                putText(out, "state", a.get("state"));
                putText(out, "reasonCode", a.get("reasonCode"));
                actions.add(out);
            }
        }
        book.set("actions", actions);
        return book;
    }

    /**
     * The single per-strike signal entry, or null when the state is non-actionable or no anchor strike
     * can be resolved (we never fabricate an anchor).
     */
    private static ObjectNode buildStrikeEntry(ObjectMapper mapper, JsonNode p, JsonNode s,
                                               long asOf, boolean stale) {
        String state = text(s.get("state")).toUpperCase();
        if (!ACTIONABLE_STATES.contains(state)) {
            return null;
        }
        double anchor = firstFinite(s.get("defendedLevel"), p.get("pinCandidateStrike"));
        if (!Double.isFinite(anchor)) {
            return null; // no strike to pin the pill to — emit book only
        }
        ObjectNode entry = mapper.createObjectNode();
        entry.put("strike", anchor);
        entry.put("state", state);
        String[] sideAndPermission = dominantSell(s.get("actions"));
        if (sideAndPermission[0] != null) {
            entry.put("action", sideAndPermission[0]);
        }
        if (sideAndPermission[1] != null) {
            entry.put("permission", sideAndPermission[1]);
        }
        entry.put("reason", latestTraceReason(s.get("decisionTrace")));
        putText(entry, "triggerId", s.get("triggerId"));
        entry.put("sinceMs", longOr(s.get("stateSinceMs"), 0L));
        entry.put("asOfEventTimeMs", asOf);
        entry.put("stale", stale);
        entry.set("trace", buildTrace(mapper, s.get("decisionTrace")));
        return entry;
    }

    /** Map DecisionTrace records to the UI trace shape (observedValue→observed, requiredThreshold→threshold). */
    private static ArrayNode buildTrace(ObjectMapper mapper, JsonNode decisionTrace) {
        ArrayNode trace = mapper.createArrayNode();
        if (decisionTrace == null || !decisionTrace.isArray()) {
            return trace;
        }
        for (JsonNode t : decisionTrace) {
            ObjectNode row = mapper.createObjectNode();
            putText(row, "condition", t.get("condition"));
            putNumber(row, "observed", t.get("observedValue"));
            putNumber(row, "threshold", t.get("requiredThreshold"));
            row.put("passed", t.path("passed").asBoolean(false));
            putText(row, "reason", t.get("reason"));
            trace.add(row);
        }
        return trace;
    }

    /**
     * Pick the dominant SELL zone from actions[]: the SELL zone (CALL_SELL_ZONE / PUT_SELL_ZONE) with
     * the most-permissive state. Returns {side, permission} where side is "CALL"|"PUT"|null.
     */
    private static String[] dominantSell(JsonNode actions) {
        String bestSide = null;
        String bestPermission = null;
        int bestRank = Integer.MAX_VALUE;
        if (actions != null && actions.isArray()) {
            for (JsonNode a : actions) {
                String zone = text(a.get("zone")).toUpperCase();
                String side = zone.equals("CALL_SELL_ZONE") ? "CALL" : zone.equals("PUT_SELL_ZONE") ? "PUT" : null;
                if (side == null) {
                    continue;
                }
                String permission = text(a.get("state")).toUpperCase();
                int rank = PERMISSION_RANK.indexOf(permission);
                if (rank < 0) {
                    rank = PERMISSION_RANK.size();
                }
                if (rank < bestRank) {
                    bestRank = rank;
                    bestSide = side;
                    bestPermission = permission.isEmpty() ? null : permission;
                }
            }
        }
        return new String[]{bestSide, bestPermission};
    }

    private static String latestTraceReason(JsonNode decisionTrace) {
        if (decisionTrace != null && decisionTrace.isArray() && decisionTrace.size() > 0) {
            return text(decisionTrace.get(decisionTrace.size() - 1).get("reason"));
        }
        return "";
    }

    // ---- small JSON helpers (fail-safe: absent/non-numeric never throws) ----

    private static void putNumber(ObjectNode node, String field, JsonNode value) {
        if (value != null && value.isNumber()) {
            node.put(field, value.asDouble());
        } else if (value != null && value.isTextual()) {
            try {
                node.put(field, Double.parseDouble(value.asText().trim()));
            } catch (NumberFormatException ignored) {
                // leave unset → UI renders '—'
            }
        }
        // absent/null → leave unset so the UI shows '—' rather than a fake 0
    }

    private static void putText(ObjectNode node, String field, JsonNode value) {
        if (value != null && !value.isNull() && !value.isMissingNode()) {
            node.put(field, value.asText());
        }
    }

    private static String text(JsonNode node) {
        return (node == null || node.isNull() || node.isMissingNode()) ? "" : node.asText("");
    }

    private static String firstText(JsonNode a, JsonNode b) {
        String first = text(a);
        return first.isEmpty() ? text(b) : first;
    }

    private static JsonNode firstNode(JsonNode a, JsonNode b) {
        return (a != null && !a.isNull() && !a.isMissingNode()) ? a : b;
    }

    private static long longOr(JsonNode node, long fallback) {
        return (node != null && node.isNumber()) ? node.asLong() : fallback;
    }

    private static double firstFinite(JsonNode a, JsonNode b) {
        double first = numberOrNaN(a);
        return Double.isFinite(first) ? first : numberOrNaN(b);
    }

    private static double numberOrNaN(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return Double.NaN;
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        if (node.isTextual()) {
            try {
                return Double.parseDouble(node.asText().trim());
            } catch (NumberFormatException ignored) {
                return Double.NaN;
            }
        }
        return Double.NaN;
    }
}
