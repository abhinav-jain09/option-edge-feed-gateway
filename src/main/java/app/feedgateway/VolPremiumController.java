package app.feedgateway;

import app.feedgateway.liquidityhistory.LiquidityHistoryAuth;
import com.optionsedge.contracts.volpremium.IvRvReading;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;

/**
 * {@code GET /api/vol-premium/ivrv?symbol=SPX} — the current vol-premium session for one symbol, as a
 * MACHINE reads it (Gate-1 §39 acceptance, VP-346: the evidence is consumable from the gateway, never
 * scraped from the UI).
 *
 * <p>Response, always a JSON object:
 * <pre>{"symbol":"SPX","sessionDate":"2026-08-27"|null,
 *  "observations":[&lt;IvRvReading v2&gt;,...],"warnings":[&lt;EarlyWarning v1&gt;,...]}</pre>
 * Observations in {@code (frameSeq, measurementEpochMs)} order and warnings in
 * {@code (frameSeq, episodeId)} order — the same order, the same records and the same bytes a
 * WebSocket replay delivers, because both read the one session store. Each record is the producer's
 * JSON VERBATIM: nothing is coalesced, reshaped or recomputed here. No session held for the symbol is
 * a 200 with {@code sessionDate:null} and two empty arrays, so a cold start reads as "nothing yet"
 * rather than as an error.
 *
 * <p>Auth is the posture of the gateway's other read-only data routes ({@code /api/gamma-fragility},
 * {@code /api/gamma-migration}, {@code /api/seller-activity}, {@code /api/liquidity-history}): bearer
 * authentication through {@link LiquidityHistoryAuth}, which verifies exactly as the WebSocket handshake
 * does — invalid or expired 401, valid but unapproved/unentitled 403 — and is open only when both
 * gateway auth switches are off, which it logs loudly at boot. That is the right posture rather than
 * the fail-closed one of {@code /api/pin-flow}: these records are already broadcast to every socket the
 * handshake admits, so the route is exactly as open as the socket and never more.
 *
 * <p>A whole session can be tens of megabytes, so the page is STREAMED record by record from a
 * snapshot of references — no copy of the response is ever built — and at most
 * {@link #MAX_CONCURRENT_RESPONSES} are written at once; the rest get 503 with Retry-After.
 */
@RestController
public class VolPremiumController {

    static final int MAX_CONCURRENT_RESPONSES = 4;

    /**
     * The contract's symbol bound, plus a character set that can be echoed into the response without
     * escaping. A symbol outside it cannot name a held series, so it is refused rather than looked up.
     */
    private static final Pattern SYMBOL =
            Pattern.compile("[A-Za-z0-9._-]{1," + IvRvReading.MAX_SYMBOL_CHARS + "}");

    private final FeedGatewayService service;
    private final LiquidityHistoryAuth auth;
    private final Semaphore permits = new Semaphore(MAX_CONCURRENT_RESPONSES);

    public VolPremiumController(FeedGatewayService service, LiquidityHistoryAuth auth) {
        this.service = service;
        this.auth = auth;
    }

    @GetMapping(value = "/api/vol-premium/ivrv", produces = MediaType.APPLICATION_JSON_VALUE)
    public void ivrv(@RequestParam(value = "symbol", defaultValue = "SPX") String symbol,
                     @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                     HttpServletResponse response) throws IOException {
        // Authentication FIRST: an unauthenticated caller learns nothing, not even whether the symbol
        // is well formed.
        LiquidityHistoryAuth.Result authResult = auth.authenticate(authorization);
        if (authResult.status() != 200) {
            response.setStatus(authResult.status());
            response.flushBuffer();
            return;
        }
        if (!SYMBOL.matcher(symbol).matches()) {
            write(response, 400, "{\"error\":\"symbol must be 1-" + IvRvReading.MAX_SYMBOL_CHARS
                    + " characters of [A-Za-z0-9._-]\"}");
            return;
        }
        if (!permits.tryAcquire()) {
            response.setHeader(HttpHeaders.RETRY_AFTER, "1");
            write(response, 503, "{\"error\":\"busy\"}");
            return;
        }
        try {
            VolPremiumSessionStore.Snapshot snapshot = service.volPremiumSession(symbol);
            response.setStatus(200);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            OutputStream out = response.getOutputStream();
            // symbol matched SYMBOL and sessionDate is the contract's yyyy-MM-dd: both are safe to echo.
            out.write(("{\"symbol\":\"" + symbol + "\",\"sessionDate\":"
                    + (snapshot.sessionDate() == null ? "null" : "\"" + snapshot.sessionDate() + "\"")
                    + ",\"observations\":[").getBytes(StandardCharsets.UTF_8));
            writeRecords(out, snapshot.observations());
            out.write("],\"warnings\":[".getBytes(StandardCharsets.UTF_8));
            writeRecords(out, snapshot.warnings());
            out.write("]}".getBytes(StandardCharsets.UTF_8));
            out.flush();
            response.flushBuffer();
        } finally {
            permits.release();
        }
    }

    /** Each record is contract-validated JSON admitted with no trailing tokens, so it is written as is. */
    private static void writeRecords(OutputStream out, List<String> records) throws IOException {
        for (int i = 0; i < records.size(); i++) {
            if (i > 0) {
                out.write(',');
            }
            out.write(records.get(i).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void write(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        response.flushBuffer();
    }
}
