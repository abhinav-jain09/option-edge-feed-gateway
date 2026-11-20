package app.feedgateway.readable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The newest reading of each VWAP line whose figures were actually present.
 *
 * <p><b>Why this exists.</b> At 06:01 Amsterdam on 2026-11-20 the owner's VWAP cells read
 * {@code vwapes|SOURCE_HAS_NO_VOLUME} where seven minutes earlier they had read
 * {@code 7184.26(LATCHED)}. The figures had not become wrong — they had become <i>unavailable</i>,
 * and the line answered by showing nothing at all. A reader cannot act on that: it looks the same
 * as a line that never had a figure, and it throws away the last thing the service did know.
 *
 * <p>So the requirement, in the reviewer's words: <b>the line never loses the last known
 * figures</b>. Where the source stops publishing a figure, the line prints the last known one
 * <i>with its own state and the time it was read</i>, and beside it the <b>new</b> state — so the
 * reader sees both what was last true and that it is no longer being refreshed. An absence is
 * reported, never substituted for the figure and never silently shown as current.
 *
 * <p>Kept <b>per line</b> ({@code vwapes}, {@code vwaprth}), because the anchors go absent
 * independently: the RTH anchor is {@code WARMING_UP} every morning while the session anchor
 * already carries a figure, and one line's warm-up must not put the other line's figure on screen.
 *
 * <p>A figure is "present" when it has a VALUE. A figure with a state and no value is exactly the
 * absence this class remembers around; {@link VwapRead.Figure#complete()} is a different question
 * (whether the value is final), and a LATCHED or INCOMPLETE value is still a value the owner read.
 */
public final class VwapHeld {

    /** One line's last present reading, and when it was read. */
    public record Snapshot(VwapRead.Anchor anchor, long atMs) {
    }

    private final Map<String, Snapshot> byLabel = new LinkedHashMap<>();

    /**
     * Keep this anchor as the line's last known reading, if it carries a figure.
     *
     * <p>Not keyed by time: a snapshot arrives every five seconds and the newest present reading
     * is the one to hold, so a later present reading always replaces an earlier one. An anchor
     * with no value leaves the held reading untouched — that is the whole point.
     */
    public void observe(String label, VwapRead.Anchor anchor, long atMs) {
        if (label == null || anchor == null) {
            return;
        }
        VwapRead.Figure vwap = anchor.vwap();
        if (vwap == null || vwap.value() == null) {
            return;
        }
        byLabel.put(label, new Snapshot(anchor, atMs));
    }

    /** That line's last present reading, or null if it has never had one. */
    public Snapshot of(String label) {
        return label == null ? null : byLabel.get(label);
    }

    /** How many lines have a held reading. Printed by the metrics, so "never held" is visible. */
    public int held() {
        return byLabel.size();
    }
}
