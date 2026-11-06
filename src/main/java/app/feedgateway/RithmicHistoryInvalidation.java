package app.feedgateway;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Structured coalescing keeps a lower affected boundary when a newer revision replaces a control. */
record RithmicHistoryInvalidation(RithmicNativeHistoryRequest.Scope namespace,String cacheIncarnation,
        long lateRevision,long affectedFromMs) {
    private static final ObjectMapper JSON=new ObjectMapper();
    RithmicHistoryInvalidation {
        if(namespace==null||cacheIncarnation==null||!cacheIncarnation.matches("[\\x21-\\x7e]{1,64}")
                ||lateRevision<0||lateRevision>RithmicNativeHistoryRequest.MAX_SAFE||affectedFromMs<0||affectedFromMs>RithmicNativeHistoryRequest.MAX_SAFE)throw new IllegalArgumentException("Invalid history invalidation");
    }
    String key(){return "rithmic-history-invalidated|"+namespace.environment()+"|"+namespace.exchange()+"|"+namespace.contract();}
    RithmicHistoryInvalidation merge(RithmicHistoryInvalidation next){
        if(!namespace.equals(next.namespace))throw new IllegalArgumentException("Foreign invalidation scope");
        return new RithmicHistoryInvalidation(namespace,next.cacheIncarnation,cacheIncarnation.equals(next.cacheIncarnation)?Math.max(lateRevision,next.lateRevision):next.lateRevision,Math.min(affectedFromMs,next.affectedFromMs));
    }
    String envelope(){
        var root=JSON.createObjectNode();root.put("type","rithmic-history-invalidated");var data=root.putObject("data");data.put("format",RithmicNativeHistoryRequest.FORMAT);var n=data.putObject("namespace");n.put("environment",namespace.environment());n.put("exchange",namespace.exchange());n.put("contract",namespace.contract());data.put("cacheIncarnation",cacheIncarnation);data.put("lateRevision",lateRevision);data.put("affectedFromMs",affectedFromMs);
        try{return JSON.writeValueAsString(root);}catch(Exception invalid){throw new IllegalStateException("Cannot encode bounded invalidation");}
    }
}
