package app.feedgateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** Joins existing closed-bar indicator evidence to active SPX levels; creates no indicator verdict. */
final class LevelSignalView {
    static final String SOURCE = "market-structure+indicator-service";
    static final long SIGNAL_TTL = 20_000, STRUCTURE_TTL = 180_000;
    static final int MAX_LEVELS = 128;
    record Signal(String source, String name, String text) { }
    record Level(String levelId, double price, String role, String state, long barStartMs,
                 long barEndMs, String relation, List<Signal> signals) { }
    record Reading(int schemaVersion, String source, String sessionDate, String timeframe,
                   long generatedAtMs, long validUntilMs, long structureAsOfMs, String status,
                   String reason, List<Level> levels, long contextAsOfMs, List<Signal> context) { }
    private record Snapshot(JsonNode root, JsonNode frame, JsonNode bar, long start, long end, long validUntil) { }

    static boolean validRequest(String date, String tf) {
        if (date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}") || !List.of("1m", "5m", "15m").contains(tf)) return false;
        try {return LocalDate.parse(date).toString().equals(date);} catch (RuntimeException e) {return false;}
    }
    static Reading read(ObjectMapper mapper, String structureJson, String spxJson, String esJson,
                        String date, String tf, long now) {
        Reading absent = new Reading(1, SOURCE, date, tf, now, now, 0, "UNAVAILABLE", "Fresh structure unavailable", List.of(), 0, List.of());
        if (!validRequest(date, tf)) return absent;
        try {
            JsonNode structure = parse(mapper, structureJson);
            long asOf = integer(structure.path("asOfMs"));
            if (!structure.isObject() || integer(structure.path("schemaVersion")) != 1
                    || !"market-structure".equals(text(structure, "service")) || !"OK".equals(text(structure, "state"))
                    || !date.equals(text(structure, "sessionDate")) || asOf <= 0 || asOf > now || now - asOf > STRUCTURE_TTL
                    || !structure.path("levels").isArray() || structure.path("levels").size() > MAX_LEVELS) return absent;
            Snapshot spx = snapshot(mapper, spxJson, "SPX", date, tf, now);
            Snapshot es = snapshot(mapper, esJson, "ES", date, tf, now);
            List<Level> out = new ArrayList<>(); Set<String> seen = new HashSet<>();
            for (JsonNode level : structure.path("levels")) {
                String id = text(level, "levelId"), role = text(level, "role");
                if (!List.of("ACTIVE", "BROKEN").contains(text(level, "state")) || id.isBlank() || id.length() > 256
                        || !seen.add(id) || !price(level.path("price")) || integer(level.path("sinceMs")) <= 0) return absent;
                if (!"ACTIVE".equals(text(level, "state")) || !List.of("SUPPORT", "RESISTANCE").contains(role)) continue;
                double p = level.path("price").doubleValue();
                if (spx == null) {out.add(new Level(id,p,role,"UNAVAILABLE",0,0,"Indicator evidence unavailable",List.of())); continue;}
                if (integer(level.path("sinceMs")) > spx.start) {
                    out.add(new Level(id,p,role,"AWAITING_FULL_BAR",spx.start,spx.end,"Level activated after this bar began; waiting for next full bar",List.of())); continue;
                }
                boolean tested = spx.bar.path("low").doubleValue() <= p && p <= spx.bar.path("high").doubleValue();
                if (!tested) {out.add(new Level(id,p,role,"NOT_TESTED",spx.start,spx.end,"No test in latest closed SPX bar",List.of())); continue;}
                List<Signal> signals = new ArrayList<>();
                evidence(signals, spx, role, "SPX");
                // ES evidence is market context for the SAME closed interval, never an SPX price conversion.
                if (es != null && es.start == spx.start && es.end == spx.end) evidence(signals, es, role, "ES");
                JsonNode bias = spx.root.path("bias").path(tf);
                String verdict = text(bias,"bias");
                if (List.of("BULLISH","BEARISH","NEUTRAL").contains(verdict)
                        && text(bias,"evaluatedBarId").equals(text(spx.bar,"barId"))
                        && stamp(bias.path("evaluatedAt")) == spx.end) {
                    signals.add(new Signal("SPX","BIAS", verdict.charAt(0) + verdict.substring(1).toLowerCase(Locale.ROOT) + " bias"));
                }
                if (signals.size() > 12) return absent;
                String side = spx.bar.path("close").doubleValue() > p ? "above" : spx.bar.path("close").doubleValue() < p ? "below" : "at";
                out.add(new Level(id,p,role,"TESTED",spx.start,spx.end,"Closed " + side + " " + role.toLowerCase(Locale.ROOT),List.copyOf(signals)));
            }
            long until = Math.min(asOf + STRUCTURE_TTL, spx == null ? now + 5_000 : spx.validUntil);
            if (es != null) until = Math.min(until, es.validUntil);
            return new Reading(1,SOURCE,date,tf,now,until,asOf,spx == null ? "PARTIAL" : "READY",
                    spx == null ? "Fresh complete SPX indicator bar unavailable" : "Closed-bar evidence; ES labels are futures context",List.copyOf(out),
                    spx == null ? 0 : stamp(spx.root.path("asOfEventTime")), context(spx));
        } catch (RuntimeException e) {return absent;}
    }
    private static List<Signal> context(Snapshot spx) {
        if (spx == null) return List.of();
        List<Signal> context=new ArrayList<>();
        String aligned=text(spx.root,"alignedDirection");
        if (List.of("BULLISH","BEARISH").contains(aligned)) context.add(new Signal("SPX","ALIGNMENT",
                aligned.equals("BULLISH") ? "Bullish timeframe alignment" : "Bearish timeframe alignment"));
        JsonNode confluence=spx.root.path("confluence");
        List<String> counts=List.of("bullish","bearish","neutral","unreliable");
        if (counts.stream().allMatch(k->integer(confluence.path(k))>=0 && integer(confluence.path(k))<=6)
                && counts.stream().mapToLong(k->integer(confluence.path(k))).sum()==6)
            context.add(new Signal("SPX","CONFLUENCE","TF: " + confluence.path("bullish").intValue() + " bull / "
                    + confluence.path("bearish").intValue() + " bear / " + confluence.path("neutral").intValue()
                    + " neutral / " + confluence.path("unreliable").intValue() + " unreliable"));
        return List.copyOf(context);
    }
    private static void evidence(List<Signal> out, Snapshot snapshot, String role, String source) {
        JsonNode exhaustion = snapshot.frame.path("exhaustion");
        if (!"READY".equals(text(exhaustion,"state"))) return;
        boolean buying = "RESISTANCE".equals(role); String direction = buying ? "BUYING" : "SELLING";
        String side = buying ? "Buying" : "Selling";
        String grade = text(exhaustion,buying ? "buying" : "selling");
        JsonNode entries = exhaustion.path("evidence");
        List<String> names = List.of("RSI_DIVERGENCE","AGGRESSOR_SHARE","REJECTION_CLOSE","VOLUME_CLIMAX");
        if (!List.of("NONE","DETECTED","EXTREME").contains(grade) || !entries.isArray() || entries.size() != 8) return;
        for (int i=0;i<8;i++) {
            JsonNode entry=entries.get(i);
            if (!(i<4 ? "BUYING" : "SELLING").equals(text(entry,"direction"))
                    || !names.get(i%4).equals(text(entry,"name")) || !entry.path("fired").isBoolean()) return;
        }
        if (List.of("DETECTED","EXTREME").contains(grade)) out.add(new Signal(source,"EXHAUSTION",
                (grade.equals("EXTREME") ? "Extreme " + side.toLowerCase(Locale.ROOT) : side) + " exhaustion"));
        Set<String> emitted = new HashSet<>();
        for (JsonNode entry : entries) {
            String name = text(entry,"name");
            if (!direction.equals(text(entry,"direction")) || !"READY".equals(text(entry,"state"))
                    || !entry.path("fired").isBoolean() || !entry.path("fired").booleanValue() || !emitted.add(name)) continue;
            String label = switch(name) {
                case "RSI_DIVERGENCE" -> "RSI divergence";
                case "REJECTION_CLOSE" -> "Rejection close";
                case "AGGRESSOR_SHARE" -> buying ? "Buy aggression below 50%" : "Sell aggression below 50%";
                case "VOLUME_CLIMAX" -> "Volume climax";
                default -> null;
            };
            // Native SPX has no trade volume. Keep ES price/RSI evidence distinct from SPX evidence.
            if (label != null && (source.equals("ES") || !List.of("AGGRESSOR_SHARE","VOLUME_CLIMAX").contains(name)))
                out.add(new Signal(source,name,label));
        }
    }
    private static Snapshot snapshot(ObjectMapper mapper, String json, String symbol, String date, String tf, long now) {
        JsonNode root = parse(mapper,json);
        long published = stamp(root.path("publishedAt")), asOf = stamp(root.path("asOfEventTime"));
        if (integer(root.path("schemaVersion")) != 1 || !symbol.equals(text(root,"symbol")) || !date.equals(text(root,"tradeDate"))
                || !"RUNNING".equals(text(root,"serviceState")) || !"OPEN".equals(text(root,"marketState"))
                || !age(root.path("lastTickAgeMs")) || !age(root.path("ingestSilenceMs"))
                || published <= 0 || published > now || now-published > SIGNAL_TTL || asOf <= 0 || asOf > published || now-asOf > SIGNAL_TTL) return null;
        JsonNode frame = root.path("timeframes").path(tf), bar = frame.path("lastFinalizedBar");
        long start = stamp(bar.path("barStart")), end = stamp(bar.path("barEnd"));
        long duration = Integer.parseInt(tf.substring(0,tf.length()-1)) * 60_000L;
        if (!bar.path("final").isBoolean() || !bar.path("final").booleanValue() || !"COMPLETE".equals(text(bar,"status"))
                || !tf.equals(text(bar,"timeframe")) || text(bar,"barId").isBlank() || text(bar,"barId").length() > 512
                || start <= 0 || start % duration != 0 || end != start+duration || end > asOf || asOf-end >= duration || now-end > duration+SIGNAL_TTL
                || !Instant.ofEpochMilli(start).atZone(ZoneId.of("America/New_York")).toLocalDate().toString().equals(date)
                || !List.of("open","high","low","close").stream().allMatch(k->price(bar.path(k)))
                || bar.path("high").doubleValue() < Math.max(bar.path("open").doubleValue(),bar.path("close").doubleValue())
                || bar.path("low").doubleValue() > Math.min(bar.path("open").doubleValue(),bar.path("close").doubleValue())) return null;
        return new Snapshot(root,frame,bar,start,end,Math.min(published,asOf)+SIGNAL_TTL);
    }
    private static JsonNode parse(ObjectMapper mapper, String json) {
        if (json == null || json.length() > 262_144) return mapper.createObjectNode();
        try {JsonNode n=mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(json);return n == null ? mapper.createObjectNode() : n;}
        catch (Exception e) {return mapper.createObjectNode();}
    }
    private static String text(JsonNode n,String key) {return n.path(key).isTextual() ? n.path(key).textValue() : "";}
    private static long integer(JsonNode n) {return n.isIntegralNumber() && n.canConvertToLong() ? n.longValue() : -1;}
    private static boolean price(JsonNode n) {return n.isNumber() && Double.isFinite(n.doubleValue()) && n.doubleValue()>0 && n.doubleValue()<1_000_000;}
    private static boolean age(JsonNode n) {return n.isNumber() && Double.isFinite(n.doubleValue()) && n.doubleValue()>=0 && n.doubleValue()<=SIGNAL_TTL;}
    private static long stamp(JsonNode n) {try {return n.isTextual() ? Instant.parse(n.textValue()).toEpochMilli() : -1;} catch(RuntimeException e) {return -1;}}
}
