package app.feedgateway.readable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * <b>C-66's {@code /footprint}</b>: buyer-initiated and seller-initiated volume at each published
 * price level, which is what the owner asked for to see support and resistance.
 *
 * <p><b>Read by the record's OWN column names, never by position.</b> {@code
 * options.spx.gamma-zone.profile} is plain JSON and carries its mapping with it:
 * {@code "columns": ["priceCents","sellerInit","buyerInit","unknown"]} with {@code levels} as arrays
 * in that order. Note what that order actually is — <b>seller before buyer</b>. Reading the second
 * number as "buyers" because buyers are named first in every sentence about a footprint would swap
 * every level on the page and nothing would look wrong. So the index of each field is taken from
 * {@code columns} at read time, and a record whose columns this class does not recognise is refused
 * rather than guessed at.
 *
 * <p><b>The prices are ES cents</b> ({@code 716025} is ES 7160.25). C-69 governs what may be done
 * about that: the SPX-equivalent is a display value using the platform's own published basis, and
 * only where that basis is {@code ANCHORED}. Where it is not, the ES price is printed and the SPX
 * column is empty — the level is never dropped and never translated silently.
 *
 * <p><b>No level is summarised or merged by us</b> (C-65a). Within the window every published level
 * gets its own line; if that exceeds a page it is paged, never thinned.
 */
public final class FootprintByPrice {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** The columns this class knows how to read. A record without them is refused. */
    static final String PRICE = "priceCents";
    static final String SELLER = "sellerInit";
    static final String BUYER = "buyerInit";
    static final String UNKNOWN = "unknown";

    /** One published level. Prices in points; volumes as the producer published them. */
    public record Level(double esPrice, Double spxPrice, long buyers, long sellers, long unknown) {

        /** Total traded at this level, which is what the far-levels list is ranked by. */
        public long total() {
            return buyers + sellers + unknown;
        }
    }

    private FootprintByPrice() {
    }

    /**
     * Read the profile.
     *
     * @param json  the profile record
     * @param basis the platform's own basis, for the display column only; may be null
     * @return the levels, oldest-to-newest price order as published, or an empty list where the
     *         record carries nothing this class may read
     */
    public static List<Level> levels(String json, BasisRead basis) {
        JsonNode root = parse(json);
        if (root == null) {
            return List.of();
        }
        JsonNode cols = root.path("columns");
        JsonNode rows = root.path("levels");
        if (!cols.isArray() || !rows.isArray()) {
            return List.of();
        }
        int iPrice = -1;
        int iSeller = -1;
        int iBuyer = -1;
        int iUnknown = -1;
        for (int i = 0; i < cols.size(); i++) {
            String name = cols.get(i).asText();
            if (PRICE.equals(name)) {
                iPrice = i;
            } else if (SELLER.equals(name)) {
                iSeller = i;
            } else if (BUYER.equals(name)) {
                iBuyer = i;
            } else if (UNKNOWN.equals(name)) {
                iUnknown = i;
            }
        }
        if (iPrice < 0 || iSeller < 0 || iBuyer < 0) {
            // The one thing worse than no footprint is a footprint with buyers and sellers the
            // wrong way round, which is what positional reading of an unrecognised record gives.
            return List.of();
        }
        List<Level> out = new ArrayList<>(rows.size());
        for (JsonNode row : rows) {
            if (!row.isArray() || row.size() <= Math.max(iPrice, Math.max(iSeller, iBuyer))) {
                continue;
            }
            double es = row.get(iPrice).asLong() / 100.0;
            long sellers = row.get(iSeller).asLong();
            long buyers = row.get(iBuyer).asLong();
            long unknown = iUnknown >= 0 && row.size() > iUnknown ? row.get(iUnknown).asLong() : 0L;
            out.add(new Level(es, basis == null ? null : basis.spxEquivalentOf(es),
                    buyers, sellers, unknown));
        }
        return out;
    }

    /**
     * The levels within {@code radiusPoints} of spot, in SPX terms where the basis allows and in ES
     * terms otherwise. Nothing inside the window is merged or dropped.
     *
     * @param spxSpot spot in SPX points
     */
    public static List<Level> near(List<Level> all, double spxSpot, double radiusPoints,
            BasisRead basis) {
        List<Level> out = new ArrayList<>();
        for (Level l : all) {
            Double px = l.spxPrice();
            if (px == null) {
                // Without a usable basis the window cannot be judged in SPX terms. Comparing an ES
                // price against an SPX spot would silently shift the window by the basis, so every
                // level is kept and the caller pages them rather than applying a wrong window.
                out.add(l);
                continue;
            }
            if (Math.abs(px - spxSpot) <= radiusPoints) {
                out.add(l);
            }
        }
        return out;
    }

    /** C-66's compact header for this table. */
    public static String header() {
        return "px|espx|buy|sell|unk|total";
    }

    /** One compact line. The SPX price leads where it exists, else the cell is empty. */
    public static String line(Level l) {
        return (l.spxPrice() == null ? "" : money(l.spxPrice()))
                + "|" + money(l.esPrice())
                + "|" + l.buyers()
                + "|" + l.sellers()
                + "|" + (l.unknown() == 0 ? "" : String.valueOf(l.unknown()))
                + "|" + l.total();
    }

    private static String money(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            return root == null || !root.isObject() ? null : root;
        } catch (Exception e) {
            return null;
        }
    }
}
