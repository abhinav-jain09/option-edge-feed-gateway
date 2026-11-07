package app.feedgateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

/** Immutable admission identity for the fixed four-resource chart stream. No calendar-wide RTH gate. */
record OrderChartSubscription(String subscriptionId, Scope scope, Cursor resume) {
    static final int MAX_REQUEST_BYTES=2048;
    static final Set<String> RESOURCES=Set.of("delta-flow","path","vwap","signals");
    record Scope(String environment,String sessionDate,String timeframe) {
        Scope {
            if(environment==null||!environment.matches("[a-z][a-z0-9_-]{0,31}")||!date(sessionDate)
                    ||timeframe==null||!Set.of("1m","5m","15m").contains(timeframe))throw new IllegalArgumentException("chart scope");
        }
        String hash() {
            // Every field has an ASCII grammar excluding newline; fixed resource identity is versioned.
            String canonical="order-chart-scope-v1\n"+environment+"\nSPX\n"+sessionDate+"\n"+timeframe+"\ndelta-flow,path,vwap,signals";
            try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));}
            catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
        }
    }
    record Cursor(String epoch,long revision) {
        Cursor {if(epoch==null||!epoch.matches("[a-f0-9]{32}")||revision<0||revision>OrderChartWireCodec.MAX_REVISION)throw new IllegalArgumentException("chart cursor");}
    }
    OrderChartSubscription {
        if(subscriptionId==null||!subscriptionId.matches("[A-Za-z0-9_-]{1,64}")||scope==null)throw new IllegalArgumentException("chart subscription");
    }
    /** Bound the actual UTF-8 wire request before JSON parsing or constructing retained state. */
    static OrderChartSubscription parse(ObjectMapper mapper,String payload,String environment) {
        if(payload==null||payload.length()>MAX_REQUEST_BYTES||payload.getBytes(StandardCharsets.UTF_8).length>MAX_REQUEST_BYTES)throw new IllegalArgumentException("chart request capacity");
        try {
            JsonNode n=mapper.reader().with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(payload);fields(n,Set.of("schemaVersion","subscriptionId","scope","resume"));
            if(!n.path("schemaVersion").isIntegralNumber()||!n.path("schemaVersion").canConvertToInt()||n.path("schemaVersion").intValue()!=1)throw new IllegalArgumentException("chart version");
            JsonNode s=n.path("scope");fields(s,Set.of("environment","symbol","sessionDate","timeframe","resources"));
            String env=text(s,"environment");if(!env.equals(environment)||!"SPX".equals(text(s,"symbol")))throw new IllegalArgumentException("chart authority");
            Scope scope=new Scope(env,text(s,"sessionDate"),text(s,"timeframe"));
            JsonNode resources=s.path("resources");Set<String> seen=new HashSet<>();
            if(!resources.isArray()||resources.size()!=RESOURCES.size())throw new IllegalArgumentException("chart resources");
            for(JsonNode r:resources)if(!r.isTextual()||!RESOURCES.contains(r.textValue())||!seen.add(r.textValue()))throw new IllegalArgumentException("chart resources");
            Cursor resume=null;
            if(n.has("resume")) {
                JsonNode c=n.path("resume");fields(c,Set.of("scopeHash","epoch","revision"));
                if(!scope.hash().equals(text(c,"scopeHash"))||!c.path("revision").isIntegralNumber()||!c.path("revision").canConvertToLong())throw new IllegalArgumentException("chart cursor scope");
                resume=new Cursor(text(c,"epoch"),c.path("revision").longValue());
            }
            return new OrderChartSubscription(text(n,"subscriptionId"),scope,resume);
        }catch(java.io.IOException invalid){throw new IllegalArgumentException("chart request JSON",invalid);}
    }
    private static void fields(JsonNode n,Set<String> allowed) {
        if(n==null||!n.isObject())throw new IllegalArgumentException("chart request object");
        n.fieldNames().forEachRemaining(k->{if(!allowed.contains(k))throw new IllegalArgumentException("chart request field");});
    }
    private static String text(JsonNode n,String key) {if(!n.path(key).isTextual())throw new IllegalArgumentException("chart request text");return n.path(key).textValue();}
    private static boolean date(String value) {
        if(value==null||!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))return false;
        try{return LocalDate.parse(value).toString().equals(value);}catch(java.time.DateTimeException invalid){return false;}
    }
}
