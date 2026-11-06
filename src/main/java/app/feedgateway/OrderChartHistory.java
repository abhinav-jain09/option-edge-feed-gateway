package app.feedgateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.List;

/** Small authenticated-socket pages over the already-admitted chart views. No extra Kafka reader. */
final class OrderChartHistory {
    static final String REQUEST="order-chart-history", RESPONSE="order-chart-history", HELLO="order-chart-history-hello";
    static final int MAX_REQUEST_BYTES=1024, MAX_PAGE_RECORDS=16;
    private static final ZoneId ET=ZoneId.of("America/New_York");
    interface FootprintSource { FootprintViews.BarsPage read(String tf,long to,long after,int limit,String date); }
    interface AuctionSource { FeedGatewayService.EsAuctionMinutesPage read(String date,String after,int limit); }
    private OrderChartHistory() { }
    static String reply(ObjectMapper mapper,FootprintViews footprint,AuctionSource auction,JsonNode request,int capacity,int recordBudget) {
        return replyFrom(mapper,footprint==null?null:footprint::barsPage,auction,request,capacity,recordBudget);
    }
    static String replyFrom(ObjectMapper mapper,FootprintSource footprint,AuctionSource auction,JsonNode request,int capacity,int recordBudget) {
        ObjectNode out=mapper.createObjectNode();out.put("schemaVersion",1);
        if(request==null||!request.isObject())return out.put("error","bad_request").toString();
        String id=request.path("requestId").asText("");
        if(!request.path("requestId").isTextual()||id.isEmpty()||id.length()>64)return out.put("error","bad_request").toString();
        out.put("requestId",id);
        String resource=request.path("resource").asText(""),date=request.path("sessionDate").asText("");
        out.put("resource",resource);out.put("sessionDate",date);
        var parsed=FootprintViews.parseCanonicalDate(date);
        if((!request.path("schemaVersion").isIntegralNumber()||!request.path("schemaVersion").canConvertToInt()||request.path("schemaVersion").intValue()!=1)||parsed==null||capacity<4096||recordBudget<1)return out.put("error","bad_request").toString();
        JsonNode asked=request.path("limit");if(!asked.isMissingNode()&&(!asked.isIntegralNumber()||!asked.canConvertToInt()||asked.intValue()<1||asked.intValue()>MAX_PAGE_RECORDS))return out.put("error","bad_request").toString();
        int limit=asked.isMissingNode()?MAX_PAGE_RECORDS:asked.intValue();
        List<String> records;
        if(resource.equals("auction")) {
            if(auction==null)return out.put("error","unavailable").toString();
            JsonNode after=request.path("after");
            if(!after.isTextual()||!after.asText().isEmpty()&&FeedGatewayService.esAuctionMinuteIndex(after.asText())<0)return out.put("error","bad_cursor").toString();
            var page=auction.read(date,after.asText(),limit);records=page.minutes();
            if(!date.equals(page.tradeDate()))return out.put("error","session_mismatch").toString();
            if(page.nextCursor()==null)out.putNull("nextCursor");else out.put("nextCursor",page.nextCursor());
        } else if(resource.equals("footprint")) {
            if(footprint==null)return out.put("error","unavailable").toString();
            String tf=request.path("timeframe").asText("");JsonNode after=request.path("after"),to=request.path("toMs");
            long start=parsed.minusDays(1).atTime(18,0).atZone(ET).toInstant().toEpochMilli(),end=parsed.atTime(16,0).atZone(ET).toInstant().toEpochMilli();
            if(!List.of("1m","5m","15m").contains(tf)||!after.isIntegralNumber()||!after.canConvertToLong()||!to.isIntegralNumber()||!to.canConvertToLong()
                    ||after.longValue()<start-1||after.longValue()>=end||to.longValue()<start-1||to.longValue()>=end||after.longValue()>to.longValue())return out.put("error","bad_cursor").toString();
            var page=footprint.read(tf,to.longValue(),after.longValue(),limit,date);
            if(page==null)return out.put("error","loading").toString();
            if(page.sessionMismatch())return out.put("error","session_mismatch").toString();
            records=page.records();out.put("timeframe",tf);out.put("toMs",to.longValue());
            if(page.nextCursor()==null)out.putNull("nextCursor");else out.put("nextCursor",page.nextCursor());
        } else return out.put("error","bad_resource").toString();
        try {
            var rows=out.putArray("records");int bytes=1024;
            for(String record:records){int recordBytes=record.getBytes(StandardCharsets.UTF_8).length;if(recordBytes>recordBudget)return error(out,"record_budget");bytes+=recordBytes;if(bytes>capacity)return smaller(mapper,footprint,auction,request,capacity,recordBudget,limit,out);JsonNode row=mapper.readTree(record);if(!row.isObject())return error(out,"invalid_history");rows.add(row);}
            out.put("done",out.path("nextCursor").isNull());String response=out.toString();
            return response.getBytes(StandardCharsets.UTF_8).length<=capacity?response:smaller(mapper,footprint,auction,request,capacity,recordBudget,limit,out);
        }catch(Exception invalid){return error(out,"invalid_history");}
    }
    private static String smaller(ObjectMapper mapper,FootprintSource footprint,AuctionSource auction,JsonNode request,int capacity,int budget,int limit,ObjectNode out) {
        if(limit<=1)return error(out,"record_budget");
        ObjectNode reduced=request.deepCopy();reduced.put("limit",Math.max(1,limit/2));return replyFrom(mapper,footprint,auction,reduced,capacity,budget);
    }
    static String refusal(ObjectMapper mapper,JsonNode request,String code) {
        ObjectNode out=mapper.createObjectNode().put("schemaVersion",1);
        if(request==null||!request.path("requestId").isTextual()||request.path("requestId").asText().isEmpty()||request.path("requestId").asText().length()>64)return out.put("error","bad_request").toString();
        out.put("requestId",request.path("requestId").asText());
        String resource=request.path("resource").asText(""),date=request.path("sessionDate").asText("");
        if(List.of("auction","footprint").contains(resource))out.put("resource",resource);
        if(FootprintViews.parseCanonicalDate(date)!=null)out.put("sessionDate",date);
        return out.put("error",code).toString();
    }
    private static String error(ObjectNode out,String error){out.remove(List.of("records","nextCursor","done"));return out.put("error",error).toString();}
}
