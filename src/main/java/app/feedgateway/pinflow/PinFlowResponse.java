package app.feedgateway.pinflow;

import java.util.List;

/**
 * The §5 data contract for {@code GET /api/pin-flow}. All list fields are aligned to the same
 * frame grid ({@code t}) — {@code t}, {@code sp}, {@code lead} share its length, and each row of
 * {@code cc}/{@code cp} corresponds to one frame (outer index) × strike (inner index, aligned to
 * {@code k}).
 *
 * @param t    per-minute ET labels {@code HH:mm} for the frames actually present (§5.1 rule 3).
 * @param sp   avg spot per minute, int, carry-forward on null (§5.1 rule 6).
 * @param k    strikes in {@code [lo,hi]} ascending, present only (§5.1 rule 8).
 * @param cc   per-frame per-strike CALL sold delta in {@code $K} int (§5.1 rule 5).
 * @param cp   per-frame per-strike PUT sold delta in {@code $K} int (§5.1 rule 5).
 * @param lead per-frame gamma-leader strike, argmax net_gex (§5.1 rule 7).
 */
public record PinFlowResponse(
        List<String> t,
        List<Integer> sp,
        List<Integer> k,
        List<List<Integer>> cc,
        List<List<Integer>> cp,
        List<Integer> lead) {
}
