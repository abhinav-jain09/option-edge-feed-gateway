package app.feedgateway.stockgex;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The one place that talks to the standalone {@code stock-gex-service}. Same shape as the other HTTP
 * upstreams in this gateway ({@code HttpApprovalAuthority}, {@code OrchestratorReplayRunLister}): a
 * {@link HttpClient} built from configured timeouts, with a package-private constructor as the test seam.
 *
 * <p><b>It deliberately does NOT fail closed.</b> Every other upstream here collapses failure into a
 * single safe answer (DENY, empty list) because the caller only needs a yes/no. This one is a PROXY: the
 * stock-gex service answers with a small vocabulary of statuses the UI branches on —
 * {@code 422 OI_SNAPSHOT_UNAVAILABLE} (symbol outside the nightly OI index universe, i.e. "ask for a
 * different ticker"), {@code 400 BAD_SYMBOL}, {@code 429 MAX_SYMBOLS}, {@code 409 BOARD_GONE},
 * {@code 503 WIRE_CAPACITY} / {@code BOARD_SUBSCRIBE_FAILED} / {@code SHUTTING_DOWN} /
 * {@code SSE_CLIENT_LIMIT} (i.e. "retry"). Folding those into a 500 would destroy the only signal the
 * page has for telling "this ticker will never work" apart from "come back in a moment", so the status
 * and body are carried through verbatim and only a genuinely unreachable service becomes a
 * gateway-authored error.
 *
 * <p><b>Bodies are carried as BYTES, never as {@code String}.</b> Decoding the upstream body and letting
 * a message converter re-encode it puts two charset guesses in the path, and "verbatim" would then
 * quietly mean "verbatim for ASCII". A {@code byte[]} has no charset to guess.
 *
 * <p><b>Nothing is silently truncated.</b> Both the board body and a rejected-subscribe error body are
 * read under a documented cap, and EXCEEDING that cap is a protocol failure that becomes a
 * gateway-authored {@code 502 UPSTREAM_PROTOCOL_ERROR} — never the upstream's own status carrying a body
 * that is a prefix of what it sent. A half-delivered body wearing the upstream's status is worse than an
 * honest error: the page would parse a truncated envelope and report whatever fell out of it.
 *
 * <p><b>Every wait THIS CLASS PERFORMS is bounded, and the bounds are shaped by what they guard</b>
 * (each verified against the JDK client rather than assumed; the write back to the browser is
 * bounded by Tomcat's connector timeout, which is not this class's to enforce):
 * <ul>
 *   <li>CONNECT — {@code HttpClient.connectTimeout}. Bounds TCP establishment only.</li>
 *   <li>HANDSHAKE — {@code HttpRequest.timeout} on BOTH calls. With {@code BodyHandlers.ofInputStream}
 *       the JDK stops enforcing this the moment {@code send()} returns, i.e. once response HEADERS have
 *       arrived; it therefore bounds "the socket was accepted but no headers ever came" without putting
 *       any lifetime limit on a healthy stream.</li>
 *   <li>BODY — a watchdog that CLOSES the response stream when it goes idle. The JDK's stream read
 *       blocks forever on a silent peer, and closing it from another thread is what unblocks it. This is
 *       the only bound that catches "headers arrived, then the upstream went quiet", which no request
 *       timeout can see and which no heartbeat can report — a heartbeat proves liveness only while the
 *       upstream is still producing them.</li>
 * </ul>
 */
public final class StockGexUpstream implements AutoCloseable {

    /** Cap on an upstream ERROR body we buffer. An error envelope is small; anything else is a fault. */
    static final int MAX_ERROR_BYTES = 64 * 1024;
    /**
     * Cap on a BOARD body, sized against AGGREGATE exposure rather than against one request.
     *
     * <p>A full board is a few hundred strikes of small JSON objects — on the order of 100 KiB at the
     * top end. The earlier 4 MiB cap gave forty times that headroom, but the number that matters is
     * not per-request: this endpoint is served synchronously on Tomcat worker threads, and
     * {@code ByteArrayOutputStream.toByteArray()} briefly doubles the buffer for its final copy. At
     * Tomcat's default 200 threads, 4 MiB per request is ~1.6 GiB of transient allocation in the
     * worst case, which is a heap event rather than a bounded read. 1 MiB keeps ten times the
     * headroom over the largest realistic board while capping the aggregate at ~400 MiB, and
     * anything larger is a producer fault that should be reported as one, not buffered.
     */
    static final int MAX_BOARD_BYTES = 1024 * 1024;
    /** Longest symbol we will put on the wire. Real tickers are <= 5-6 chars; this only stops abuse. */
    static final int MAX_SYMBOL_LENGTH = 32;

    /** Watchdog threads. See the constructor: one is a shared-fate failure mode, not an economy. */
    static final int WATCHDOG_THREADS = 4;

    /** Gateway-authored failure codes. Distinct on purpose — they mean different things to an operator. */
    static final String CODE_UNAVAILABLE = "UPSTREAM_UNAVAILABLE";
    static final String CODE_PROTOCOL = "UPSTREAM_PROTOCOL_ERROR";

    /** A completed board call: the upstream's status, content type, Retry-After and body bytes, untouched. */
    public record BoardResponse(int status, String contentType, String retryAfter, byte[] body) {}

    /**
     * An opened stream. When {@link #live()}, {@link #body()} is the live upstream byte stream and the
     * caller OWNS it (closing it cancels the exchange and releases the upstream's SSE listener slot).
     * Otherwise {@code body} is null and {@link #errorBody()} carries the upstream's response body.
     */
    public record StreamResponse(int status, String contentType, String retryAfter,
                                 InputStream body, byte[] errorBody) {
        /**
         * ONLY 200 is a live event stream. Every other status — including another 2xx such as 204 or
         * 206 — is passed through as a status+body response. Treating "any 2xx" as a stream would let a
         * 204 be presented to the browser as a live SSE response that never emits an event, and would
         * rewrite the upstream's status to 200 on the way past.
         */
        public boolean live() {
            return status == 200;
        }
    }

    /**
     * The service could not be reached, or answered something that is not a usable response — distinct
     * from any 4xx/5xx it deliberately authored. {@link #code()} is what the browser is told.
     */
    public static final class UnavailableException extends RuntimeException {
        private final String code;

        UnavailableException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    private final String baseUrl;
    private final Duration boardTimeout;
    private final HttpClient http;
    /** Shared by the board read deadline and (via {@link #timers()}) the controller's idle watchdogs. */
    private final ScheduledExecutorService timers;
    private final boolean ownsTimers;

    public StockGexUpstream(String baseUrl, Duration connectTimeout, Duration boardTimeout) {
        this(baseUrl, boardTimeout, HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                // HTTP/1.1 on purpose. The upstream is a Python ASGI service and the default HTTP/2
                // policy makes the JDK attempt an h2c upgrade on every cleartext request: extra
                // negotiation for zero benefit on a protocol SSE was designed for.
                .version(HttpClient.Version.HTTP_1_1)
                // A redirect is not part of this contract. Following one would let the upstream point
                // this gateway at an arbitrary host; passing it through would leak an internal Location.
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(), null);
    }

    /** Test seam: inject a stub {@link HttpClient}. */
    StockGexUpstream(String baseUrl, Duration boardTimeout, HttpClient http) {
        this(baseUrl, boardTimeout, http, null);
    }

    /** Test seam: inject a stub client AND a deterministic scheduler. */
    StockGexUpstream(String baseUrl, Duration boardTimeout, HttpClient http, ScheduledExecutorService timers) {
        // NOT validated here, deliberately. A bad address must not stop this gateway from booting and
        // serving market data (see StockGexConfig); it becomes a per-request 502 instead, which is a
        // state the page renders. Boot-time validation would trade a broken board for a broken gateway.
        this.baseUrl = trimTrailingSlash(baseUrl == null ? "" : baseUrl);
        this.boardTimeout = Objects.requireNonNull(boardTimeout, "boardTimeout");
        this.http = Objects.requireNonNull(http, "http");
        this.ownsTimers = timers == null;
        // MORE THAN ONE THREAD, deliberately. Every watchdog task calls InputStream.close(), and
        // close() is not contractually non-blocking — a single stuck close on one thread would
        // disable every other body deadline and every other stream's idle check at once, which is
        // precisely when those deadlines matter. A small pool bounds that blast radius.
        this.timers = timers != null ? timers
                : Executors.newScheduledThreadPool(WATCHDOG_THREADS, new java.util.concurrent.ThreadFactory() {
                    private final java.util.concurrent.atomic.AtomicInteger n =
                            new java.util.concurrent.atomic.AtomicInteger();

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "stock-gex-watchdog-" + n.incrementAndGet());
                        t.setDaemon(true);
                        return t;
                    }
                });
    }

    /** The shared watchdog scheduler; the controller arms its per-stream idle timers on it. */
    ScheduledExecutorService timers() {
        return timers;
    }

    @Override
    public void close() {
        if (ownsTimers) {
            timers.shutdownNow();
        }
    }

    /**
     * {@code GET <base>/api/stock-gex/board?symbol=…}. The symbol is URL-encoded and NOT otherwise
     * touched — not trimmed, not upper-cased: the upstream owns the {@code BAD_SYMBOL} contract, and
     * rewriting the value here would make the gateway a second, silently disagreeing judge of what was
     * asked for. What the length bound measures is exactly what goes on the wire.
     */
    public BoardResponse board(String symbol) {
        long startedNanos = System.nanoTime();
        HttpResponse<InputStream> resp;
        try {
            // Request construction is INSIDE the mapped block: newBuilder/uri/header/build can all throw
            // IllegalArgumentException for a bad address or header, and an escape here is a 500 with a
            // stack trace on the browser's side.
            HttpRequest req = HttpRequest.newBuilder(uri("/api/stock-gex/board", symbol))
                    .timeout(boardTimeout)
                    .header("Accept", "application/json")
                    // Ask for no CONTENT coding. This is only a request, though — see
                    // requireIdentityEncoding: what arrives still has to be checked, because a body
                    // forwarded compressed without its Content-Encoding header is garbage that still
                    // claims to be JSON.
                    .header("Accept-Encoding", "identity")
                    .GET()
                    .build();
            resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable("interrupted while calling stock-gex-service", e);
        } catch (IOException transport) {
            throw unavailable("stock-gex-service is unreachable", transport);
        } catch (IllegalArgumentException | SecurityException misconfigured) {
            throw unavailable("stock-gex-service address is not usable", misconfigured);
        }
        requireIdentityEncoding(resp, "board");
        // send() has returned, so the JDK's request timeout no longer applies (verified). A body that
        // arrives byte-by-byte, or never, would otherwise hold this request thread forever — and the
        // deadline passed here is what REMAINS of the configured budget, not a fresh copy of it.
        // GatewaySettings calls this a whole-request budget; starting the clock again after headers
        // would have made a 5s setting mean up to 10s.
        byte[] body = readBounded(resp.body(), MAX_BOARD_BYTES, remaining(startedNanos), "board");
        return new BoardResponse(resp.statusCode(), header(resp, "content-type"),
                header(resp, "retry-after"), body);
    }

    /**
     * {@code GET <base>/api/stock-gex/stream?symbol=…}, forwarding {@code Last-Event-ID} when the client
     * sent a usable one so the upstream can resume rather than resend a full snapshot.
     *
     * <p>The id is VALIDATED, never rewritten. It is an opaque token the upstream issued and keys its
     * replay buffer on, so normalising it — even trimming whitespace — risks resuming from an id the
     * upstream never minted. Anything that is not exactly a bounded run of printable ASCII is DROPPED,
     * which the upstream answers with a full reset snapshot: correct, just more bytes.
     */
    public StreamResponse stream(String symbol, String lastEventId) {
        long startedNanos = System.nanoTime();
        HttpResponse<InputStream> resp;
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(uri("/api/stock-gex/stream", symbol))
                    // Bounds the HANDSHAKE only: with ofInputStream the JDK stops enforcing this once
                    // send() returns with headers, so a healthy hours-long stream is never on a clock.
                    // Without it, an upstream that accepts the socket and never answers pins a Tomcat
                    // request thread indefinitely.
                    .timeout(boardTimeout)
                    .header("Accept", "text/event-stream")
                    .header("Accept-Encoding", "identity")
                    .header("Cache-Control", "no-cache")
                    .GET();
            String resume = sanitizeEventId(lastEventId);
            if (resume != null) {
                b.header("Last-Event-ID", resume);
            }
            resp = http.send(b.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable("interrupted while opening the stock-gex stream", e);
        } catch (IOException transport) {
            throw unavailable("stock-gex-service is unreachable", transport);
        } catch (IllegalArgumentException | SecurityException misconfigured) {
            throw unavailable("stock-gex-service address is not usable", misconfigured);
        }
        requireIdentityEncoding(resp, "stream");
        int status = resp.statusCode();
        String contentType = header(resp, "content-type");
        String retryAfter = header(resp, "retry-after");
        if (status != 200) {
            // Not a stream: read the body under the error cap and close, so a rejected subscribe never
            // leaves a socket open. Overflow or a read fault is a protocol failure, not a body.
            byte[] body = readBounded(resp.body(), MAX_ERROR_BYTES, remaining(startedNanos), "stream error");
            return new StreamResponse(status, contentType, retryAfter, null, body);
        }
        return new StreamResponse(status, contentType, retryAfter, resp.body(), null);
    }

    /**
     * Refuse a body that arrived under a content coding this proxy does not decode.
     *
     * <p>{@code Accept-Encoding: identity} is a REQUEST header — a wish, not a guarantee. If an
     * upstream or an intermediary compresses anyway, forwarding those bytes while dropping the
     * {@code Content-Encoding} header hands the browser a gzip stream labelled as JSON: it is not
     * corrupt in a way anything reports, it is simply unreadable. Since the whole contract here is
     * byte-for-byte passthrough, the honest answer is to refuse rather than to half-decode.
     */
    private static void requireIdentityEncoding(HttpResponse<?> resp, String what) {
        String encoding = header(resp, "content-encoding");
        if (encoding == null || encoding.isBlank()
                || "identity".equalsIgnoreCase(encoding.trim())) {
            return;
        }
        try {
            if (resp.body() instanceof InputStream stream) {
                stream.close();
            }
        } catch (IOException ignored) {
            // abandoning the exchange either way
        }
        throw new UnavailableException(CODE_PROTOCOL,
                "stock-gex " + what + " arrived with content-encoding '" + encoding.trim()
                + "', which this proxy does not decode", null);
    }

    /**
     * Read a whole body under a byte cap and a wall-clock deadline, or fail loudly.
     *
     * <p>The deadline is enforced by CLOSING the stream from the watchdog thread — the only thing that
     * unblocks a read parked on a silent peer. Exceeding the byte cap is deliberately NOT a truncation:
     * a prefix of a body, delivered under the upstream's own status, is a corrupt answer wearing a
     * trustworthy label.
     */
    private byte[] readBounded(InputStream in, int maxBytes, Duration deadline, String what) {
        if (in == null) {
            return new byte[0];
        }
        ScheduledFuture<?> alarm = timers.schedule(() -> closeQuietly(in),
                Math.max(1L, deadline.toMillis()), TimeUnit.MILLISECONDS);
        try (InputStream body = in) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = body.read(chunk)) != -1) {
                if (buffer.size() + n > maxBytes) {
                    throw new UnavailableException(CODE_PROTOCOL,
                            "stock-gex " + what + " body exceeded " + maxBytes + " bytes", null);
                }
                buffer.write(chunk, 0, n);
            }
            return buffer.toByteArray();
        } catch (IOException failed) {
            // Includes the watchdog's close. Either way we do NOT have the upstream's body, so we must
            // not answer with the upstream's status and a body we invented (an empty one).
            throw new UnavailableException(CODE_PROTOCOL,
                    "stock-gex " + what + " body could not be read", failed);
        } finally {
            alarm.cancel(false);
        }
    }

    /**
     * Build the upstream URI, refusing anything that is not an absolute http(s) address.
     *
     * <p>A misconfigured {@code STOCK_GEX_BASE_URL} must not become a 500 with a stack trace on the
     * browser's side, and must not fail the whole gateway at boot either: it becomes the same
     * {@link UnavailableException} an unreachable service produces, i.e. a 502 with the ordinary JSON
     * envelope, which is the state the page already knows how to render.
     */
    private URI uri(String path, String symbol) {
        String s = symbol == null ? "" : symbol;
        String raw = baseUrl + path + "?symbol=" + URLEncoder.encode(s, StandardCharsets.UTF_8);
        URI uri;
        try {
            uri = new URI(raw);
        } catch (URISyntaxException malformed) {
            throw unavailable("stock-gex base url is not a valid URI", malformed);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!uri.isAbsolute() || uri.getHost() == null
                || !("http".equals(scheme) || "https".equals(scheme))) {
            throw unavailable("stock-gex base url must be an absolute http(s) address", null);
        }
        return uri;
    }

    /**
     * Printable-ASCII, bounded ids only, taken EXACTLY as sent. Space is outside 0x21..0x7E, so an id
     * with surrounding whitespace is dropped rather than trimmed into a different token.
     */
    static String sanitizeEventId(String raw) {
        if (raw == null || raw.isEmpty() || raw.length() > 256) {
            return null;
        }
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c < 0x21 || c > 0x7E) {
                return null;
            }
        }
        return raw;
    }

    /** What is LEFT of the configured budget, floored so a body read always gets a moment to finish. */
    private Duration remaining(long startedNanos) {
        long spentMs = (System.nanoTime() - startedNanos) / 1_000_000L;
        long leftMs = boardTimeout.toMillis() - spentMs;
        return Duration.ofMillis(Math.max(250L, leftMs));
    }

    private static UnavailableException unavailable(String message, Throwable cause) {
        return new UnavailableException(CODE_UNAVAILABLE, message, cause);
    }

    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (IOException ignored) {
            // Best effort: the point of the close is to unblock a parked read, and it already has.
        }
    }

    private static String header(HttpResponse<?> resp, String name) {
        return resp.headers().firstValue(name).orElse(null);
    }

    private static String trimTrailingSlash(String s) {
        String t = s.trim();
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }
}
