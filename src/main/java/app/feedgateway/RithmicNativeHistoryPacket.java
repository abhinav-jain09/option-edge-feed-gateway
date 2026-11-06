package app.feedgateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.util.List;

/** Bounded metadata/byte copying only: source bodies are never expanded on a request thread. */
final class RithmicNativeHistoryPacket {
    static final int MAX_PACKET=524288,MAX_HEADER=16384,MIN_PACKET=32768;
    private static final ObjectMapper JSON=new ObjectMapper();
    record Gap(long startMs,long endMs,RithmicDepthJson.GapReason gapReason){
        Gap{if(startMs<0||endMs<=startMs||endMs>RithmicNativeHistoryRequest.MAX_SAFE||gapReason==null)throw new IllegalArgumentException("Invalid source interval");}
    }
    record Snapshot(List<RithmicNativeHistoryColumn> columns,String sourceState,Long coveredThroughMs,
            Long availableFromMs,Long sourceAvailableFromMs,boolean limited,List<String> limitedReasons,
            String cacheIncarnation,long lateRevision,List<Gap> sourceGapIntervals){
        Snapshot{
            columns=List.copyOf(columns);limitedReasons=List.copyOf(limitedReasons);sourceGapIntervals=List.copyOf(sourceGapIntervals);
            if(columns.size()>33||!List.of("READY","LOADING","UNAVAILABLE").contains(sourceState)
                    ||cacheIncarnation==null||!cacheIncarnation.matches("[\\x21-\\x7e]{1,64}")||lateRevision<0||lateRevision>RithmicNativeHistoryRequest.MAX_SAFE
                    ||limitedReasons.size()>16||limitedReasons.stream().anyMatch(s->s==null||s.length()>64)
                    ||!limited&&!limitedReasons.isEmpty()||sourceGapIntervals.size()>2048)throw new IllegalArgumentException("Invalid snapshot metadata");
            for(Long n:new Long[]{coveredThroughMs,availableFromMs,sourceAvailableFromMs})if(n!=null&&(n<0||n>RithmicNativeHistoryRequest.MAX_SAFE))throw new IllegalArgumentException("Invalid availability");
            long end=-1;for(Gap g:sourceGapIntervals){if(g.startMs()<end)throw new IllegalArgumentException("Unordered intervals");end=g.endMs();}
        }
    }
    record Reply(byte[] binary,String error){boolean accepted(){return binary!=null;}}
    private RithmicNativeHistoryPacket(){}

    static Reply build(RithmicNativeHistoryRequest.Request request,Snapshot snapshot,int capacity){
        try{
            capacity=Math.min(capacity,MAX_PACKET);
            if(capacity<MIN_PACKET)return new Reply(null,"capacity");
            if(snapshot.sourceState().equals("UNAVAILABLE"))return new Reply(null,"unavailable");
            long previous=request.afterMs();
            for(var c:snapshot.columns()){
                if(c.emittedAtMs()<=previous||c.emittedAtMs()<request.fromMs()||c.emittedAtMs()>request.toMs()
                        ||c.namespace()!=null&&!c.namespace().equals(request.namespace())||c.book()&&c.namespace()==null)return new Reply(null,"invalid_source");
                previous=c.emittedAtMs();
            }
            int selected=0,payload=0;byte[] header=header(request,snapshot,0);
            if(header.length>MAX_HEADER||header.length+8>capacity)return new Reply(null,"capacity");
            if(snapshot.sourceState().equals("READY"))for(var c:snapshot.columns()){
                if(selected==32)break;
                byte[] candidate=header(request,snapshot,selected+1);
                if(candidate.length>MAX_HEADER||8L+candidate.length+payload+c.nativeBytes()>capacity)break;
                selected++;payload+=c.nativeBytes();header=candidate;
            }
            if(selected==0&&!snapshot.columns().isEmpty()&&snapshot.sourceState().equals("READY"))return new Reply(null,"capacity");
            ByteBuffer packet=ByteBuffer.allocate(8+header.length+payload);packet.put(new byte[]{82,76,70,49});packet.putInt(header.length);packet.put(header);
            for(int i=0;i<selected;i++)snapshot.columns().get(i).appendNative(packet);
            return new Reply(packet.array(),null);
        }catch(Exception invalid){return new Reply(null,"invalid_source");}
    }
    private static byte[] header(RithmicNativeHistoryRequest.Request request,Snapshot snapshot,int count)throws Exception{
        ObjectNode value=headerValue(request,snapshot,count,true);
        byte[] bytes=JSON.writeValueAsBytes(value);
        if(bytes.length>MAX_HEADER){value=headerValue(request,snapshot,count,false);bytes=JSON.writeValueAsBytes(value);}
        return bytes;
    }
    private static ObjectNode headerValue(RithmicNativeHistoryRequest.Request request,Snapshot snapshot,int count,boolean intervals){
        var h=JSON.createObjectNode();h.put("type",RithmicDepthHistory.RESPONSE);h.put("format",RithmicNativeHistoryRequest.FORMAT);h.put("coverage","COMPLETE_INPUT_SNAPSHOT");h.put("truncated",false);
        var n=h.putObject("namespace");n.put("environment",request.namespace().environment());n.put("exchange",request.namespace().exchange());n.put("contract",request.namespace().contract());
        h.put("requestId",request.requestId());h.put("fromMs",request.fromMs());h.put("toMs",request.toMs());h.put("pageFromExclusiveMs",request.afterMs());
        long cursor=count==0?request.afterMs():snapshot.columns().get(count-1).emittedAtMs();h.put("afterMs",cursor);
        boolean ready=snapshot.sourceState().equals("READY"),done=ready&&count==snapshot.columns().size()&&snapshot.coveredThroughMs()!=null&&snapshot.coveredThroughMs()>=request.toMs();
        h.put("done",done);h.put("ready",ready);h.put("sourceState",snapshot.sourceState());h.put("waitingForSource",ready&&count==0&&!done);
        h.put("coveredThroughMs",snapshot.coveredThroughMs());h.put("availableFromMs",snapshot.availableFromMs());h.put("sourceAvailableFromMs",snapshot.sourceAvailableFromMs());
        h.put("limited",snapshot.limited()||!intervals);var reasons=h.putArray("limitedReasons");snapshot.limitedReasons().forEach(reasons::add);if(!intervals&&snapshot.limitedReasons().size()<16&&!snapshot.limitedReasons().contains("HEADER_INTERVAL_LIMIT"))reasons.add("HEADER_INTERVAL_LIMIT");
        h.put("coverageUncertain",!intervals);h.put("sampleMs",1000);h.put("capturePolicy","FIRST_PER_BUCKET_WITH_STATE_BOUNDARIES");h.put("cacheIncarnation",snapshot.cacheIncarnation());h.put("lateRevision",snapshot.lateRevision());
        var columns=h.putArray("columns");for(int i=0;i<count;i++){
            var c=snapshot.columns().get(i);var item=columns.addObject();item.put("emittedAtMs",c.emittedAtMs());item.put("kind",c.book()?"BOOK":"SOURCE_GAP");item.put("compressedBytes",c.nativeBytes());
            if(c.breakBeforeMs()!=null)item.put("breakBeforeMs",c.breakBeforeMs());if(!c.book())item.put("gapReason",c.gapReason().name());
        }
        if(intervals){var gaps=h.putArray("sourceGapIntervals");for(var g:snapshot.sourceGapIntervals())if(g.endMs()>request.fromMs()&&g.startMs()<=request.toMs()){var item=gaps.addObject();item.put("startMs",g.startMs());item.put("endMs",g.endMs());item.put("gapReason",g.gapReason().name());}}
        return h;
    }
}
