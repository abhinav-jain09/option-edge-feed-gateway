package app.feedgateway.stockgex;

import app.feedgateway.liquidityhistory.LiquidityHistoryAuth;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The web UI's door to the standalone {@code stock-gex-service}:
 *
 * <ul>
 *   <li>{@code GET /api/stock-gex/board?symbol=TSLA} — the current stock GEX board, one request/response.</li>
 *   <li>{@code GET /api/stock-gex/stream?symbol=TSLA} — the live {@code text/event-stream} for that board.</li>
 * </ul>
 *
 * <p><b>Status codes are a contract, not noise.</b> The upstream distinguishes
 * {@code 422 OI_SNAPSHOT_UNAVAILABLE} (this ticker is outside the nightly OI index universe — never going
 * to work today), {@code 400 BAD_SYMBOL}, and {@code 429/503 MAX_SYMBOLS / WIRE_CAPACITY /
 * BOARD_SUBSCRIBE_FAILED / SHUTTING_DOWN} (capacity — try again). The page renders a different thing for
 * each, so this proxy forwards the upstream status AND body byte-for-byte. The only statuses this class
 * authors are 401/403 (auth, before anything else happens), 400 for a symbol too long to put on the wire,
 * and 502 when the service cannot be reached at all — deliberately 502 rather than 503, so "the gateway
 * could not reach stock-gex" stays distinguishable from the upstream's own 503 capacity answer.
 *
 * <p>Bearer auth reuses {@link LiquidityHistoryAuth}, exactly as {@code /api/seller-activity} and
 * {@code /api/gamma-migration} do — these are data endpoints and stay behind the same guard as every other
 * one. This gateway deliberately has no servlet-security filter chain (see the pom comment — adding one
 * would lock every endpoint behind a generated password), so "authenticated" here means calling the
 * shared verifier first, which both handlers do before touching the upstream.
 *
 * <p>No rate limiter and no concurrency semaphore, unlike the cache-backed reads next door, and that is a
 * decision rather than an omission: the upstream already owns the capacity contract and expresses it in
 * the 429/503 codes above, and a second gateway-authored 429 with a different body would be
 * indistinguishable from {@code MAX_SYMBOLS} to the page. Concurrency is bounded transitively — every live
 * stream here holds exactly one upstream SSE listener, and the upstream caps those.
 */
@RestController
public class StockGexController {

    private final StockGexUpstream upstream;
    private final LiquidityHistoryAuth auth;
    private final ObjectMapper mapper;

    public StockGexController(StockGexUpstream upstream, LiquidityHistoryAuth auth, ObjectMapper mapper) {
        this.upstream = upstream;
        this.auth = auth;
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
    }

    @GetMapping(value = "/api/stock-gex/board", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> board(
            @RequestParam(value = "symbol", required = false) String symbol,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        LiquidityHistoryAuth.Result authResult = auth.authenticate(authorization);
        if (authResult.status() != 200) {
            return ResponseEntity.status(authResult.status()).build();
        }
        if (tooLong(symbol)) {
            return json(HttpStatus.BAD_REQUEST, error("BAD_SYMBOL", "symbol is too long"));
        }
        StockGexUpstream.BoardResponse response;
        try {
            response = upstream.board(symbol);
        } catch (StockGexUpstream.UnavailableException unreachable) {
            return json(HttpStatus.BAD_GATEWAY, unreachableError());
        }
        return ResponseEntity.status(response.status())
                .contentType(mediaType(response.contentType(), MediaType.APPLICATION_JSON))
                .body(response.body() == null ? "" : response.body());
    }

    /**
     * No {@code produces} condition on purpose: this handler answers {@code text/event-stream} on success
     * and {@code application/json} on an upstream error passthrough, so declaring one media type would
     * both misdescribe the endpoint and 406 a client that asked only for the other.
     */
    @GetMapping("/api/stock-gex/stream")
    public ResponseEntity<StreamingResponseBody> stream(
            @RequestParam(value = "symbol", required = false) String symbol,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        LiquidityHistoryAuth.Result authResult = auth.authenticate(authorization);
        if (authResult.status() != 200) {
            return ResponseEntity.status(authResult.status()).build();
        }
        if (tooLong(symbol)) {
            return streamJson(HttpStatus.BAD_REQUEST, error("BAD_SYMBOL", "symbol is too long"));
        }
        StockGexUpstream.StreamResponse opened;
        try {
            opened = upstream.stream(symbol, lastEventId);
        } catch (StockGexUpstream.UnavailableException unreachable) {
            return streamJson(HttpStatus.BAD_GATEWAY, unreachableError());
        }
        if (!opened.ok()) {
            // A rejected subscribe (422/429/503/…) is an ordinary error response, not a stream.
            byte[] body = (opened.errorBody() == null ? "" : opened.errorBody())
                    .getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.status(opened.status())
                    .contentType(mediaType(opened.contentType(), MediaType.APPLICATION_JSON))
                    .body(out -> out.write(body));
        }
        InputStream live = opened.body();
        return ResponseEntity.ok()
                .contentType(mediaType(opened.contentType(), MediaType.TEXT_EVENT_STREAM))
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform")
                // Any buffering proxy in front of this (nginx/ingress) would hold events back until its
                // buffer filled, which for a 200-byte-per-second stream reads exactly like a dead feed.
                .header("X-Accel-Buffering", "no")
                .body(pump(live));
    }

    /**
     * Copy upstream bytes to the client verbatim, flushing each chunk.
     *
     * <p>Byte-level copying is the point: the upstream implements a strict replay/resume contract in its
     * {@code id:} lines, and the client's next {@code Last-Event-ID} must be an id the upstream itself
     * issued. Parsing and re-emitting events here would put a second author on that sequence — one
     * dropped or renumbered id and the upstream sees a gap or a future id and answers the reconnect with
     * a full reset snapshot. So nothing is parsed and nothing is buffered beyond one 8 KiB chunk.
     *
     * <p>The {@code try}-with-resources is how a client disconnect is propagated: when the client goes
     * away the write (or its flush) throws, the upstream stream is closed on the way out, and closing it
     * cancels the HTTP exchange — releasing the listener slot the upstream counts against its concurrent
     * SSE cap. A client that vanishes without an RST is detected on the next heartbeat (~10s) rather than
     * never, because the heartbeat is what gives us a write to fail on.
     */
    private static StreamingResponseBody pump(InputStream live) {
        return out -> {
            try (InputStream in = live) {
                byte[] chunk = new byte[8192];
                int n;
                while ((n = in.read(chunk)) != -1) {
                    if (n > 0) {
                        out.write(chunk, 0, n);
                        out.flush();
                    }
                }
            } catch (IOException clientOrUpstreamGone) {
                // Normal termination for SSE: the client navigated away, or the upstream ended the
                // stream. Nothing to report — the close above already released the upstream listener.
            }
        };
    }

    private boolean tooLong(String symbol) {
        return symbol != null && symbol.trim().length() > StockGexUpstream.MAX_SYMBOL_LENGTH;
    }

    private String unreachableError() {
        return error("UPSTREAM_UNAVAILABLE", "stock-gex-service is unreachable");
    }

    /**
     * Build the gateway's own error envelope with Jackson rather than string concatenation, and never
     * include the upstream exception: a stack trace or a raw connect error names internal hosts and ports
     * to whoever opened the page.
     */
    private String error(String code, String message) {
        ObjectNode node = mapper.createObjectNode();
        node.put("error", message);
        node.put("code", code);
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException impossible) {
            // Cannot happen for a flat node of two strings, but an unparseable body must never be the
            // failure mode of an error path — the client would then fail to read WHY it failed.
            return "{\"error\":\"stock-gex request failed\",\"code\":\"" + code + "\"}";
        }
    }

    private static ResponseEntity<String> json(HttpStatus status, String body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    private static ResponseEntity<StreamingResponseBody> streamJson(HttpStatus status, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(out -> out.write(bytes));
    }

    /** Honour the upstream's content type when it is a usable one, else the caller's expectation. */
    private static MediaType mediaType(String raw, MediaType fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return MediaType.parseMediaType(raw);
        } catch (org.springframework.http.InvalidMediaTypeException malformed) {
            return fallback;
        }
    }
}
