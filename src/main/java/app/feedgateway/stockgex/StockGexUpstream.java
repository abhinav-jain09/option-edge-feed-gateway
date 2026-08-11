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

/**
 * The one place that talks to the standalone {@code stock-gex-service}. Same shape as the other HTTP
 * upstreams in this gateway ({@code HttpApprovalAuthority}, {@code OrchestratorReplayRunLister}): a
 * {@link HttpClient} built from configured timeouts, with a package-private constructor as the test seam.
 *
 * <p><b>It deliberately does NOT fail closed.</b> Every other upstream here collapses failure into a
 * single safe answer (DENY, empty list) because the caller only needs a yes/no. This one is a PROXY: the
 * stock-gex service answers with a small vocabulary of statuses the UI branches on —
 * {@code 422 OI_SNAPSHOT_UNAVAILABLE} (symbol outside the nightly OI index universe, i.e. "ask for a
 * different ticker"), {@code 400 BAD_SYMBOL}, {@code 429 MAX_SYMBOLS}, {@code 503 WIRE_CAPACITY} /
 * {@code BOARD_SUBSCRIBE_FAILED} / {@code SHUTTING_DOWN} / {@code SSE_CLIENT_LIMIT},
 * {@code 409 BOARD_GONE} (i.e. "retry"). Folding those into a 500 would destroy the only signal the page
 * has for telling "this ticker will never work" apart from "come back in a moment", so the status and
 * body are carried through verbatim and only a genuinely unreachable service becomes a gateway-authored
 * error.
 *
 * <p><b>Bodies are carried as BYTES, never as {@code String}.</b> Decoding the upstream body and letting
 * a message converter re-encode it puts two charset guesses in the path (the JDK's, then Spring's, whose
 * {@code StringHttpMessageConverter} default is ISO-8859-1 for anything it does not recognise as JSON),
 * and "verbatim" would then quietly mean "verbatim for ASCII". A {@code byte[]} has no charset to guess.
 *
 * <p>Timeouts are split by shape: the board is a request/response call with a whole-request budget; the
 * stream has a CONNECT budget only. An SSE response is long-lived by construction (heartbeats at ~10s,
 * sessions measured in hours), so a request-level deadline there would kill healthy streams on a timer.
 */
public final class StockGexUpstream {

    /** Cap on an upstream ERROR body we buffer — an error envelope is small; anything else is hostile. */
    private static final int MAX_ERROR_BYTES = 64 * 1024;
    /** Longest symbol we will put on the wire. Real tickers are <= 5-6 chars; this only stops abuse. */
    static final int MAX_SYMBOL_LENGTH = 32;

    /** A completed board call: the upstream's status, content type and body bytes, untouched. */
    public record BoardResponse(int status, String contentType, byte[] body) {}

    /**
     * An opened stream. When {@link #ok()}, {@link #body()} is the live upstream byte stream and the
     * caller OWNS it (closing it cancels the exchange and releases the upstream's SSE listener slot).
     * Otherwise {@code body} is null and {@link #errorBody()} carries the upstream's error envelope.
     */
    public record StreamResponse(int status, String contentType, InputStream body, byte[] errorBody) {
        public boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    /** The service could not be reached at all (connect refused, DNS, timeout) — distinct from any 4xx/5xx it authored. */
    public static final class UnavailableException extends RuntimeException {
        UnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final String baseUrl;
    private final Duration boardTimeout;
    private final HttpClient http;

    public StockGexUpstream(String baseUrl, Duration connectTimeout, Duration boardTimeout) {
        this(baseUrl, boardTimeout, HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                // HTTP/1.1 on purpose. The upstream is a Python ASGI service and the default HTTP/2
                // policy makes the JDK attempt an h2c upgrade on every cleartext request: extra
                // negotiation for zero benefit, and an upgrade a server may answer in ways that are
                // hard to reason about on a long-lived streaming response. 1.1 is what SSE was
                // designed on and what the upstream actually speaks.
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    /** Test seam: inject a stub {@link HttpClient}. */
    StockGexUpstream(String baseUrl, Duration boardTimeout, HttpClient http) {
        this.baseUrl = trimTrailingSlash(Objects.requireNonNull(baseUrl, "baseUrl"));
        this.boardTimeout = Objects.requireNonNull(boardTimeout, "boardTimeout");
        this.http = Objects.requireNonNull(http, "http");
        if (this.baseUrl.isBlank()) {
            throw new IllegalArgumentException("stock-gex base url is required");
        }
    }

    /**
     * {@code GET <base>/api/stock-gex/board?symbol=…}. The symbol is URL-encoded but NOT otherwise
     * normalised: the upstream owns the {@code BAD_SYMBOL} contract, and upper-casing or rewriting it
     * here would make the gateway a second, silently disagreeing judge of what a valid symbol is.
     */
    public BoardResponse board(String symbol) {
        HttpRequest req = HttpRequest.newBuilder(uri("/api/stock-gex/board", symbol))
                .timeout(boardTimeout)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<byte[]> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UnavailableException("interrupted while calling stock-gex-service", e);
        } catch (IOException transport) {
            throw new UnavailableException("stock-gex-service is unreachable", transport);
        } catch (IllegalArgumentException | SecurityException misconfigured) {
            // A base URL the JDK client refuses (no scheme, unsupported scheme, blocked by a security
            // manager) is a CONFIGURATION fault, not a request fault. Letting it escape would answer
            // the browser with a 500 and a stack trace; 502 with the ordinary envelope keeps the page
            // on the "cannot reach the board service" path it already renders.
            throw new UnavailableException("stock-gex-service address is not usable", misconfigured);
        }
        return new BoardResponse(resp.statusCode(), contentType(resp), resp.body());
    }

    /**
     * {@code GET <base>/api/stock-gex/stream?symbol=…}, forwarding {@code Last-Event-ID} when the client
     * sent one so the upstream can resume rather than resend a full snapshot.
     *
     * <p>A {@code Last-Event-ID} that is not plain printable ASCII, or is absurdly long, is DROPPED rather
     * than forwarded or rejected: the upstream's replay contract answers an unknown/absent id with a full
     * reset snapshot, so dropping degrades to "correct but more bytes" — whereas forwarding it raw would
     * throw on header validation and turn a resumable reconnect into an error page.
     */
    public StreamResponse stream(String symbol, String lastEventId) {
        // No .timeout(): see the class comment — a request deadline would kill a healthy long-lived stream.
        HttpRequest.Builder b = HttpRequest.newBuilder(uri("/api/stock-gex/stream", symbol))
                .header("Accept", "text/event-stream")
                .header("Cache-Control", "no-cache")
                .GET();
        String resume = sanitizeEventId(lastEventId);
        if (resume != null) {
            b.header("Last-Event-ID", resume);
        }
        HttpResponse<InputStream> resp;
        try {
            resp = http.send(b.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UnavailableException("interrupted while opening the stock-gex stream", e);
        } catch (IOException transport) {
            throw new UnavailableException("stock-gex-service is unreachable", transport);
        } catch (IllegalArgumentException | SecurityException misconfigured) {
            throw new UnavailableException("stock-gex-service address is not usable", misconfigured);
        }
        int status = resp.statusCode();
        String contentType = contentType(resp);
        if (status < 200 || status >= 300) {
            // Drain (bounded) and close so a rejected subscribe never leaves a socket open.
            return new StreamResponse(status, contentType, null, drain(resp.body()));
        }
        return new StreamResponse(status, contentType, resp.body(), null);
    }

    /**
     * Build the upstream URI, refusing anything that is not an absolute http(s) address.
     *
     * <p>A misconfigured {@code STOCK_GEX_BASE_URL} must not become a 500 with a stack trace on the
     * browser's side, and must not fail the whole gateway at boot either (see {@link StockGexConfig}):
     * it becomes the same {@code UnavailableException} an unreachable service produces, i.e. a 502 with
     * the ordinary JSON envelope. That is the honest answer — the board service cannot be reached —
     * and it is the state the page already knows how to render.
     */
    private URI uri(String path, String symbol) {
        String s = symbol == null ? "" : symbol.trim();
        String raw = baseUrl + path + "?symbol=" + URLEncoder.encode(s, StandardCharsets.UTF_8);
        URI uri;
        try {
            uri = new URI(raw);
        } catch (URISyntaxException malformed) {
            throw new UnavailableException("stock-gex base url is not a valid URI", malformed);
        }
        String scheme = uri.getScheme();
        if (!uri.isAbsolute() || uri.getHost() == null
                || scheme == null
                || !("http".equals(scheme.toLowerCase(Locale.ROOT))
                     || "https".equals(scheme.toLowerCase(Locale.ROOT)))) {
            throw new UnavailableException(
                    "stock-gex base url must be an absolute http(s) address", null);
        }
        return uri;
    }

    /** Printable-ASCII, bounded ids only; anything else is treated as "no id" (full snapshot). */
    static String sanitizeEventId(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || trimmed.length() > 256) {
            return null;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c < 0x21 || c > 0x7E) {
                return null;
            }
        }
        return trimmed;
    }

    private static byte[] drain(InputStream in) {
        if (in == null) {
            return new byte[0];
        }
        try (InputStream body = in) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while (buffer.size() < MAX_ERROR_BYTES && (n = body.read(chunk)) != -1) {
                buffer.write(chunk, 0, Math.min(n, MAX_ERROR_BYTES - buffer.size()));
            }
            return buffer.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    private static String contentType(HttpResponse<?> resp) {
        return resp.headers().firstValue("content-type").orElse(null);
    }

    private static String trimTrailingSlash(String s) {
        String t = s.trim();
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }
}
