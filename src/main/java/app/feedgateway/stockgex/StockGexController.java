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
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

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
 * to work today), {@code 400 BAD_SYMBOL}, {@code 409 BOARD_GONE}, and {@code 429/503 MAX_SYMBOLS /
 * WIRE_CAPACITY / BOARD_SUBSCRIBE_FAILED / SHUTTING_DOWN / SSE_CLIENT_LIMIT} (capacity — try again). The
 * page renders a different thing for each, so this proxy forwards the upstream status AND body
 * byte-for-byte. The only statuses this class authors are 401/403 (auth, before anything else happens),
 * 400 for a symbol too long to put on the wire, 503 when this gateway is already carrying as many live
 * streams as it will hold, and 502 when the service cannot be reached at all — deliberately 502 rather
 * than 503, so "the gateway could not reach stock-gex" stays distinguishable from the upstream's own 503
 * capacity answer.
 *
 * <p>Bearer auth reuses {@link LiquidityHistoryAuth}, exactly as {@code /api/seller-activity} and
 * {@code /api/gamma-migration} do — these are data endpoints and stay behind the same guard as every other
 * one. This gateway deliberately has no servlet-security filter chain (see the pom comment — adding one
 * would lock every endpoint behind a generated password), so "authenticated" here means calling the
 * shared verifier first, which both handlers do before touching the upstream.
 *
 * <p><b>Why there IS a stream cap here and no rate limiter.</b> No request-rate limiter: the upstream owns
 * the request-capacity contract and expresses it in the 429/503 codes above, and a second gateway-authored
 * 429 with a different body would be indistinguishable from {@code MAX_SYMBOLS} to the page. But
 * CONCURRENCY is a gateway-local resource that the upstream cannot see: every live stream pins one thread
 * of this JVM's MVC async pool for the life of the session. That pool is finite (see
 * {@link StockGexAsyncConfig}), and an async response that cannot get a thread does not fail — it QUEUES,
 * answering nothing at all, which with {@code spring.mvc.async.request-timeout=-1} means forever. So the
 * streams are capped below the pool size and the overflow is answered immediately with the upstream's own
 * {@code 503 SSE_CLIENT_LIMIT} vocabulary, which the page already renders. A refused stream is a page that
 * says so and falls back to polling; a queued stream is a page that hangs.
 */
@RestController
public class StockGexController {

    /**
     * Max CONCURRENT live streams this gateway will carry. Each holds one MVC async thread and one
     * upstream HTTP exchange for the life of the session, so this — not the upstream's own limit — is
     * what bounds this JVM. Kept comfortably below {@link StockGexAsyncConfig#MAX_ASYNC_THREADS} so the
     * other async endpoint on this gateway ({@code /api/seller-activity}) can always get a thread.
     */
    static final int MAX_CONCURRENT_STREAMS = 24;

    /** Never log an unreachable-upstream failure more than this often: an outage is per-request. */
    private static final long UNREACHABLE_LOG_INTERVAL_MS = 30_000L;

    private final StockGexUpstream upstream;
    private final LiquidityHistoryAuth auth;
    private final ObjectMapper mapper;
    /** Package-visible so a test can exhaust it. */
    final Semaphore streamSlots;
    private final AtomicLong lastUnreachableLogMs = new AtomicLong(0L);

    @org.springframework.beans.factory.annotation.Autowired
    public StockGexController(StockGexUpstream upstream, LiquidityHistoryAuth auth, ObjectMapper mapper) {
        this(upstream, auth, mapper, MAX_CONCURRENT_STREAMS);
    }

    /** Test seam: explicit concurrency limit. */
    StockGexController(StockGexUpstream upstream, LiquidityHistoryAuth auth, ObjectMapper mapper,
                       int maxConcurrentStreams) {
        this.upstream = upstream;
        this.auth = auth;
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
        this.streamSlots = new Semaphore(maxConcurrentStreams);
    }

    /**
     * No {@code produces} condition on purpose (same reason as the stream handler): the body is an
     * upstream passthrough whose content type is whatever the upstream chose, so pinning one media type
     * here would both misdescribe the endpoint and 406 a client whose {@code Accept} did not name it.
     */
    @GetMapping("/api/stock-gex/board")
    public ResponseEntity<byte[]> board(
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
            logUnreachable("board", unreachable);
            return json(HttpStatus.BAD_GATEWAY, unreachableError());
        }
        return ResponseEntity.status(response.status())
                .contentType(mediaType(response.contentType(), MediaType.APPLICATION_JSON))
                .body(response.body() == null ? new byte[0] : response.body());
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
        // Refuse BEFORE opening anything upstream. Answering now with a status the page already
        // understands is strictly better than letting the request queue on an exhausted async pool,
        // where it would answer nothing at all.
        if (!streamSlots.tryAcquire()) {
            byte[] body = bodyOf(error("SSE_CLIENT_LIMIT",
                    "this gateway is already carrying its maximum number of live stock-gex streams"));
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.RETRY_AFTER, "5")
                    .body(out -> out.write(body));
        }
        boolean streamOwnsPermit = false;
        try {
            StockGexUpstream.StreamResponse opened;
            try {
                opened = upstream.stream(symbol, lastEventId);
            } catch (StockGexUpstream.UnavailableException unreachable) {
                logUnreachable("stream", unreachable);
                return streamJson(HttpStatus.BAD_GATEWAY, unreachableError());
            }
            if (!opened.ok()) {
                // A rejected subscribe (422/429/503/…) is an ordinary error response, not a stream.
                byte[] body = opened.errorBody() == null ? new byte[0] : opened.errorBody();
                return ResponseEntity.status(opened.status())
                        .contentType(mediaType(opened.contentType(), MediaType.APPLICATION_JSON))
                        .body(out -> out.write(body));
            }
            StreamingResponseBody pump = pump(opened.body(), streamSlots);
            // From here the permit belongs to the stream body, which releases it when the pump ends.
            streamOwnsPermit = true;
            return ResponseEntity.ok()
                    .contentType(mediaType(opened.contentType(), MediaType.TEXT_EVENT_STREAM))
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform")
                    // Any buffering proxy in front of this (nginx/ingress) would hold events back until
                    // its buffer filled, which for a 200-byte-per-second stream reads exactly like a
                    // dead feed.
                    .header("X-Accel-Buffering", "no")
                    .body(pump);
        } finally {
            if (!streamOwnsPermit) {
                streamSlots.release();
            }
        }
    }

    /**
     * Copy upstream bytes to the client verbatim, flushing each chunk, then release the stream slot.
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
     *
     * <p>The permit is released in a {@code finally} that wraps EVERY exit, including a
     * {@link RuntimeException} thrown by the servlet output stream: a leaked permit is a slot this
     * gateway never gets back, so the release must not depend on the failure mode being the expected one.
     */
    private static StreamingResponseBody pump(InputStream live, Semaphore slots) {
        return out -> {
            try {
                try (InputStream in = live) {
                    byte[] chunk = new byte[8192];
                    int n;
                    while ((n = in.read(chunk)) != -1) {
                        if (n > 0) {
                            out.write(chunk, 0, n);
                            out.flush();
                        }
                    }
                } catch (IOException | UncheckedIOException clientOrUpstreamGone) {
                    // Normal termination for SSE: the client navigated away, or the upstream ended the
                    // stream. Nothing to report — the close above already released the upstream listener.
                }
            } finally {
                slots.release();
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
     * One line per outage window, not one per request. A proxy that logs nothing gives an operator no
     * way to tell "the board page is broken" from "the board service is down", but a 502 is per-request
     * and a down upstream would otherwise write a line for every poll of every open tab.
     */
    private void logUnreachable(String endpoint, RuntimeException cause) {
        long now = System.currentTimeMillis();
        long previous = lastUnreachableLogMs.get();
        if (now - previous < UNREACHABLE_LOG_INTERVAL_MS
                || !lastUnreachableLogMs.compareAndSet(previous, now)) {
            return;
        }
        Throwable root = cause.getCause() == null ? cause : cause.getCause();
        // Message only, no stack trace: this is an expected operational state, not a defect.
        System.out.println("stock-gex " + endpoint + " 502: upstream unreachable ("
                + root.getClass().getSimpleName() + ": " + root.getMessage() + ")");
    }

    /**
     * Build the gateway's own error envelope with Jackson rather than string concatenation, and never
     * include the upstream exception: a stack trace or a raw connect error names internal hosts and ports
     * to whoever opened the page.
     *
     * <p>The SHAPE matches what the stock-gex service itself emits — {@code error} carries the CODE and
     * {@code detail} the human sentence — because the page has exactly one error reader and a
     * gateway-authored envelope in a different shape would be read as an unrecognised failure. {@code
     * code} is emitted as well, redundantly, so a reader written against either convention finds the
     * token rather than a sentence.
     */
    private String error(String code, String message) {
        ObjectNode node = mapper.createObjectNode();
        node.put("error", code);
        node.put("code", code);
        node.put("detail", message);
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException impossible) {
            // Cannot happen for a flat node of three strings, but an unparseable body must never be the
            // failure mode of an error path — the client would then fail to read WHY it failed.
            return "{\"error\":\"" + code + "\",\"code\":\"" + code
                    + "\",\"detail\":\"stock-gex request failed\"}";
        }
    }

    private static byte[] bodyOf(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static ResponseEntity<byte[]> json(HttpStatus status, String body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(bodyOf(body));
    }

    private static ResponseEntity<StreamingResponseBody> streamJson(HttpStatus status, String body) {
        byte[] bytes = bodyOf(body);
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
