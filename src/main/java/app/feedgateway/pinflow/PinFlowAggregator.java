package app.feedgateway.pinflow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The §5.1 deterministic aggregation, extracted as a PURE function over in-memory rows so it can be
 * unit-tested without a live database. {@link PinFlowStore} fetches the rows via JDBC and hands them
 * here; every rule below is authoritative — DO NOT reinvent (§5.1).
 *
 * <p>Rules implemented:
 * <ol>
 *   <li>Frame grid = sorted distinct minutes present across BOTH tables (gaps preserved, no synthetic grid).</li>
 *   <li>Duplicate {@code (minute,strike)} → take the MAX cumulative value (defensive).</li>
 *   <li>{@code cc}/{@code cp} = per-minute delta of the cumulative notional, clamped {@code >= 0}, ÷1000 → {@code $K}
 *       (round half-up), with a PER-STRIKE baseline: a strike's FIRST observation seeds the baseline and emits
 *       {@code 0}; a previously-seen strike absent this minute carries its baseline forward (delta 0); a never-seen
 *       absent strike stays 0.</li>
 *   <li>{@code sp} = avg(spot) per minute over band rows, rounded int; null/absent → carry-forward previous;
 *       before the first spot → 0.</li>
 *   <li>{@code lead} = per minute argmax net_gex over the widened gamma band, regardless of sign, tie-break
 *       lowest strike; carry-forward the previous lead ONLY when a minute has zero gamma rows at all (0 before first).</li>
 * </ol>
 */
public final class PinFlowAggregator {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    private PinFlowAggregator() {
    }

    /** A row of {@code pin_strike_minute} (already band-filtered, already floored to the minute). */
    public record StrikeMinuteRow(long minuteEpoch, int strike, BigDecimal callSellNotional,
                                  BigDecimal putSellNotional, BigDecimal spot, String tradeDate) {
    }

    /**
     * Cumulative-counter identity. The counters RESET per trade_date, and one session window legitimately
     * spans two trade_dates (ES runs 18:00→17:00 ET while trade_date rolls at midnight). Keying the
     * baseline by strike alone let the two sessions' counters collide: de-duplicating (minute,strike) via
     * MAX picked the OLD session's high cumulative over the NEW session's fresh low one, so the old
     * session's entire total was re-emitted as a single positive delta — measured $3.25M against a true
     * $1.65M at ES 7600. Track each (tradeDate,strike) counter independently and sum their deltas.
     */
    private record TdStrike(String tradeDate, int strike) {
    }

    /** A row of {@code pin_self_gex_minute} (already widened-band-filtered, floored to the minute). */
    public record GexMinuteRow(long minuteEpoch, int strike, BigDecimal netGex) {
    }

    /**
     * @param strikeRows band-filtered {@code pin_strike_minute} rows ({@code [lo,hi]}).
     * @param gexRows    widened-band {@code pin_self_gex_minute} rows ({@code [max(100,lo-100), min(100000,hi+100)]}).
     * @param zone       session timezone for the {@code HH:mm} labels (§5.1 rule 1).
     */
    public static PinFlowResponse aggregate(List<StrikeMinuteRow> strikeRows, List<GexMinuteRow> gexRows,
                                            ZoneId zone) {
        // ---- Rule 1: frame grid = sorted distinct minutes present across BOTH tables. ----
        TreeSet<Long> minuteSet = new TreeSet<>();
        for (StrikeMinuteRow r : strikeRows) {
            minuteSet.add(r.minuteEpoch());
        }
        for (GexMinuteRow r : gexRows) {
            minuteSet.add(r.minuteEpoch());
        }
        List<Long> frames = new ArrayList<>(minuteSet);

        // ---- Present strikes in [lo,hi] (ascending). Rows are pre-filtered to the band by the store. ----
        TreeSet<Integer> strikeSet = new TreeSet<>();
        for (StrikeMinuteRow r : strikeRows) {
            strikeSet.add(r.strike());
        }
        List<Integer> strikes = new ArrayList<>(strikeSet);
        Map<Integer, Integer> strikeIndex = new HashMap<>();
        for (int i = 0; i < strikes.size(); i++) {
            strikeIndex.put(strikes.get(i), i);
        }

        // ---- Index rows by minute for O(1) lookup, de-duplicating (minute,strike) via MAX (rule 2). ----
        // callCum[minute][strike] and putCum[minute][strike] = max cumulative for that cell.
        Map<Long, Map<TdStrike, BigDecimal>> callCum = new HashMap<>();
        Map<Long, Map<TdStrike, BigDecimal>> putCum = new HashMap<>();
        Map<Long, List<BigDecimal>> spotByMinute = new HashMap<>();
        for (StrikeMinuteRow r : strikeRows) {
            TdStrike key = new TdStrike(r.tradeDate() == null ? "" : r.tradeDate(), r.strike());
            callCum.computeIfAbsent(r.minuteEpoch(), m -> new HashMap<>())
                    .merge(key, nz(r.callSellNotional()), PinFlowAggregator::max);
            putCum.computeIfAbsent(r.minuteEpoch(), m -> new HashMap<>())
                    .merge(key, nz(r.putSellNotional()), PinFlowAggregator::max);
            if (r.spot() != null) {
                spotByMinute.computeIfAbsent(r.minuteEpoch(), m -> new ArrayList<>()).add(r.spot());
            }
        }

        // net_gex[minute][strike] = max over duplicates (rule 2), for the widened band.
        Map<Long, Map<Integer, BigDecimal>> gexByMinute = new HashMap<>();
        for (GexMinuteRow r : gexRows) {
            if (r.netGex() == null) {
                continue;
            }
            gexByMinute.computeIfAbsent(r.minuteEpoch(), m -> new HashMap<>())
                    .merge(r.strike(), r.netGex(), PinFlowAggregator::max);
        }

        // ---- Per-strike cumulative baseline (rule 5): seen[strike] = last cumulative we emitted a delta against. ----
        Map<TdStrike, BigDecimal> callBaseline = new HashMap<>();
        Map<TdStrike, BigDecimal> putBaseline = new HashMap<>();

        List<String> t = new ArrayList<>(frames.size());
        List<Integer> sp = new ArrayList<>(frames.size());
        List<List<Integer>> cc = new ArrayList<>(frames.size());
        List<List<Integer>> cp = new ArrayList<>(frames.size());
        List<Integer> lead = new ArrayList<>(frames.size());

        int prevSpot = 0;               // rule 6: before the first spot → 0
        Integer prevLead = null;        // rule 7: carry-forward only across zero-gamma minutes; 0 before first

        for (long minute : frames) {
            LocalDateTime local = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(minute), zone);
            t.add(local.format(HHMM));

            // ---- Rule 6: spot avg / carry-forward. ----
            List<BigDecimal> spots = spotByMinute.get(minute);
            if (spots != null && !spots.isEmpty()) {
                BigDecimal sum = BigDecimal.ZERO;
                for (BigDecimal s : spots) {
                    sum = sum.add(s);
                }
                prevSpot = sum.divide(BigDecimal.valueOf(spots.size()), 0, RoundingMode.HALF_UP).intValue();
            }
            // else: carry-forward (prevSpot unchanged; 0 before the first spot).
            sp.add(prevSpot);

            // ---- Rule 5: per-strike CALL/PUT sold delta with per-strike baseline. ----
            Map<TdStrike, BigDecimal> callThis = callCum.getOrDefault(minute, Map.of());
            Map<TdStrike, BigDecimal> putThis = putCum.getOrDefault(minute, Map.of());
            // Sum per strike ACROSS trade_dates: each session's counter advances on its own baseline, so a
            // rollover inside the window contributes only its own real increment, never a re-count.
            // Sum the RAW deltas first and round ONCE per strike: rounding each trade_date's delta to $K
            // before summing inflates split flow (two $500 deltas would round to 1K + 1K = 2K, not 1K).
            Map<Integer, BigDecimal> ccRaw = new HashMap<>();
            Map<Integer, BigDecimal> cpRaw = new HashMap<>();
            for (Map.Entry<TdStrike, BigDecimal> e : callThis.entrySet()) {
                ccRaw.merge(e.getKey().strike(), deltaRaw(e.getKey(), e.getValue(), callBaseline), BigDecimal::add);
            }
            for (Map.Entry<TdStrike, BigDecimal> e : putThis.entrySet()) {
                cpRaw.merge(e.getKey().strike(), deltaRaw(e.getKey(), e.getValue(), putBaseline), BigDecimal::add);
            }
            List<Integer> ccRow = new ArrayList<>(strikes.size());
            List<Integer> cpRow = new ArrayList<>(strikes.size());
            for (Integer strike : strikes) {
                ccRow.add(toK(ccRaw.get(strike)));
                cpRow.add(toK(cpRaw.get(strike)));
            }
            cc.add(ccRow);
            cp.add(cpRow);

            // ---- Rule 7: gamma leader argmax over widened band, regardless of sign, tie-break lowest. ----
            Map<Integer, BigDecimal> gexThis = gexByMinute.get(minute);
            if (gexThis == null || gexThis.isEmpty()) {
                lead.add(prevLead == null ? 0 : prevLead); // zero gamma rows → carry-forward (0 before first)
            } else {
                Integer bestStrike = null;
                BigDecimal bestGex = null;
                for (Map.Entry<Integer, BigDecimal> e : gexThis.entrySet()) {
                    int strike = e.getKey();
                    BigDecimal gex = e.getValue();
                    if (bestGex == null || gex.compareTo(bestGex) > 0
                            || (gex.compareTo(bestGex) == 0 && strike < bestStrike)) {
                        bestGex = gex;
                        bestStrike = strike;
                    }
                }
                prevLead = bestStrike;
                lead.add(bestStrike);
            }
        }

        return new PinFlowResponse(t, sp, strikes, cc, cp, lead);
    }

    /**
     * Per-strike delta in $K: {@code (thisMinute - baseline)} clamped {@code >= 0}, ÷1000 round-half-up.
     * First observation seeds the baseline and emits 0; a strike absent this minute keeps its baseline
     * (delta 0); a never-seen absent strike stays 0.
     */
    private static BigDecimal deltaRaw(TdStrike key, BigDecimal current, Map<TdStrike, BigDecimal> baseline) {
        if (current == null) {
            return BigDecimal.ZERO; // absent this minute: carry-forward baseline unchanged, delta 0
        }
        BigDecimal prev = baseline.get(key);
        if (prev == null) {
            baseline.put(key, current); // FIRST observation: seed, emit 0 (never the whole cumulative)
            return BigDecimal.ZERO;
        }
        if (current.compareTo(prev) < 0) {
            // A DECREASE within one (tradeDate,strike) counter is impossible in-order — it means a stale
            // or replayed row. Emit 0 and KEEP the high-water baseline: lowering it would let the
            // subsequent in-order row re-emit flow already counted (1_628_000 -> stale 1_000_000 ->
            // replay 1_628_000 would otherwise emit a second +628_000).
            return BigDecimal.ZERO;
        }
        baseline.put(key, current);
        return current.subtract(prev);
    }

    /** Raw notional → $K, round half-up. Applied ONCE, after cross-trade_date summation. */
    private static int toK(BigDecimal raw) {
        if (raw == null || raw.signum() <= 0) {
            return 0;
        }
        return raw.divide(THOUSAND, 0, RoundingMode.HALF_UP).intValue();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal max(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) >= 0 ? a : b;
    }
}
