package app.feedgateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.optionsedge.contracts.greekmoveauth.GreekMoveAuthAvro;
import com.optionsedge.contracts.greekmoveauth.MoveAuthenticityVerdict;
import org.apache.avro.generic.GenericRecord;

/**
 * The browser JSON of {@code options.spx.greek-move-auth.current}. The topic is Avro only (the contracts
 * {@code MoveAuthenticityVerdict} record); the browser contract is unchanged, so a decoded record is re-emitted through
 * the SAME plain Jackson mapper the JSON-era service used to write the topic: identical field names, order, nulls and
 * number formatting.
 */
final class GreekMoveAuthJson {
    private static final ObjectMapper JSON = new ObjectMapper();

    private GreekMoveAuthJson() {}

    /** The JSON text of a decoded verdict record; {@code null} for a tombstone, a loud failure for anything else. */
    static String render(Object value) {
        if (value == null) return null;
        if (!(value instanceof GenericRecord record) || !GreekMoveAuthAvro.SUBJECT.equals(record.getSchema().getFullName()))
            throw new IllegalStateException("greek-move-auth: value is not a " + GreekMoveAuthAvro.SUBJECT + " Avro record ("
                    + (value instanceof GenericRecord r ? r.getSchema().getFullName() : value.getClass().getName()) + ")");
        MoveAuthenticityVerdict verdict = GreekMoveAuthAvro.fromRecord(record);
        try {
            return JSON.writeValueAsString(verdict);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("greek-move-auth: could not render the verdict as JSON", e);
        }
    }
}
