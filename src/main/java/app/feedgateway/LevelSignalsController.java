package app.feedgateway;

import app.feedgateway.liquidityhistory.LiquidityHistoryAuth;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public final class LevelSignalsController {
    private final FeedGatewayService service;
    private final LiquidityHistoryAuth auth;
    private final java.util.concurrent.Semaphore reads = new java.util.concurrent.Semaphore(4);
    @Autowired
    public LevelSignalsController(FeedGatewayService service, ObjectProvider<LiquidityHistoryAuth> provider) {
        this(service,provider.getIfAvailable());
    }
    LevelSignalsController(FeedGatewayService service,LiquidityHistoryAuth auth) {this.service=service;this.auth=auth;}
    @GetMapping(value="/api/context-tape/level-signals",produces="application/json")
    public ResponseEntity<?> reading(@RequestParam("sessionDate") String date, @RequestParam("tf") String tf,
            @RequestHeader(value="Authorization",required=false) String authorization) {
        if (auth==null || !auth.enforcing()) return ResponseEntity.status(401).build();
        var result=auth.authenticate(authorization);
        if(result.status()!=200) return ResponseEntity.status(result.status()).build();
        if(!LevelSignalView.validRequest(date,tf)) return ResponseEntity.badRequest().body(java.util.Map.of("error","Invalid sessionDate or timeframe"));
        if (!reads.tryAcquire()) return ResponseEntity.status(503).header("Retry-After","1").build();
        try {return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.levelSignals(date,tf));}
        finally {reads.release();}
    }
}
