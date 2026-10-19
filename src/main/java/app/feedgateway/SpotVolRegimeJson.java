package app.feedgateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.optionsedge.contracts.spotvolregime.SpotVolRegimeSnapshot;

/**
 * The browser JSON of {@code options.spx.spot-vol-regime.current}. The topic is Avro only (decoded by
 * {@code SpotVolRegimeAvro.SnapshotDeserializer}); the browser contract is unchanged, so a decoded snapshot is re-emitted
 * by the SAME plain Jackson writer the producer used for the JSON era's value: the record's field names and order, explicit
 * nulls, doubles as {@code Double.toString} prints them.
 */
final class SpotVolRegimeJson {
    /** Deliberately a bare mapper: the JSON-era producer wrote {@code new ObjectMapper().writeValueAsString(snapshot)}. */
    private static final ObjectMapper WRITER = new ObjectMapper();

    private SpotVolRegimeJson() {}

    /** The JSON text of a decoded snapshot; {@code null} for a tombstone, a loud failure for anything that is not the typed snapshot. */
    static String render(Object value) {
        if (value == null) return null;
        if (!(value instanceof SpotVolRegimeSnapshot snapshot))
            throw new IllegalStateException("spot-vol-regime: value is not a SpotVolRegimeSnapshot (" + value.getClass().getName() + ")");
        try {
            return WRITER.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("spot-vol-regime: could not render the snapshot as JSON", e);
        }
    }
}
