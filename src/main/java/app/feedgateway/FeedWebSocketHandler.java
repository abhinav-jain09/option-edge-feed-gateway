package app.feedgateway;

import app.feedgateway.mtsession.SessionRoutingEngine;
import app.feedgateway.mtsession.gateway.TicketHandshakeInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class FeedWebSocketHandler extends TextWebSocketHandler implements SubProtocolCapable {
    private static final Logger log = LoggerFactory.getLogger(FeedWebSocketHandler.class);

    /** Where an open socket came from: enough to tell WHICH browser/tab holds a slot, not who the user is. */
    record Origin(String remote, String userAgent, String origin, long openedAtMs) {
        String describe(long nowMs) {
            return "remote=" + remote + " origin=" + origin + " ua=\"" + userAgent + "\" openFor=" + ((nowMs - openedAtMs) / 1000) + "s";
        }
    }

    private final Map<String, Origin> origins = new ConcurrentHashMap<>();

    private static String header(WebSocketSession session, String name) {
        try {
            String v = session.getHandshakeHeaders().getFirst(name);
            if (v == null) {
                return "-";
            }
            String clean = v.replace("\"", "'").replace("\n", " ");
            return clean.length() > 120 ? clean.substring(0, 120) : clean;
        } catch (RuntimeException e) {
            return "-";
        }
    }

    private static Origin originOf(WebSocketSession session) {
        String forwarded = header(session, "X-Forwarded-For");
        String remote = !"-".equals(forwarded) ? forwarded
                : session.getRemoteAddress() == null ? "-" : session.getRemoteAddress().toString();
        return new Origin(remote, header(session, "User-Agent"), header(session, "Origin"), System.currentTimeMillis());
    }
    private final FeedGatewayService gatewayService;
    private final ObjectProvider<SessionRoutingEngine> engineProvider;

    public FeedWebSocketHandler(FeedGatewayService gatewayService,
                                ObjectProvider<SessionRoutingEngine> engineProvider) {
        this.gatewayService = gatewayService;
        this.engineProvider = engineProvider;
    }

    /**
     * Advertises the {@code oc.bearer} subprotocol so the negotiated handshake echoes it back when the
     * browser connects with {@code new WebSocket(url, ['oc.bearer', accessToken])} (see
     * {@link WsJwtHandshakeInterceptor}). Without this the server would reject the offered subprotocol.
     */
    @Override
    public List<String> getSubProtocols() {
        return List.of(WsJwtHandshakeInterceptor.BEARER_SUBPROTOCOL);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // LH-O-6: the owner tag of the authenticated subject, computed once; a socket without a subject gets none and so receives no user-line record
        Object subject = session.getAttributes() == null ? null : session.getAttributes().get("sub");
        if (subject instanceof String text && !text.isBlank()) {
            session.getAttributes().put(FeedGatewayService.LINE_HEALTH_OWNER_TAG_ATTR, LineHealthOwnerTag.of(text));
        }
        SessionRoutingEngine engine = engineProvider.getIfAvailable();
        // Auth on (the session router bean exists ⟺ gateway.auth.enabled=true): the handshake has
        // already redeemed the single-use ticket and bound an AppSession id. This socket MUST attach
        // to that session — if it cannot, REJECT it. A redeemed ticket must never leave an
        // unattached live client (no AppSession to route or tear it down), so addClient is skipped.
        if (engine != null) {
            Object attr = session.getAttributes().get(TicketHandshakeInterceptor.ATTR_APP_SESSION_ID);
            if (!(attr instanceof String id) || id.isBlank()) {
                reject(session, "no session bound");
                return;
            }
            origins.put(session.getId(), originOf(session));
            try {
                // attachSocket validates all limits before mutating, so a throw leaves no partial state.
                engine.attachSocket(id, session.getId());
                log.info("socket attached account={} sockets={}/{} {}", id,
                        engine.appSession(id).map(a -> a.socketIds().size()).orElse(-1),
                        engine.maxSocketsPerAppSession(), origins.get(session.getId()).describe(System.currentTimeMillis()));
            } catch (RuntimeException attachFailed) {
                // AppSession expired between ticket mint and connect, or the per-AppSession socket
                // cap was reached. Close the socket; never register it as a live client.
                // Names the cause: a page shows this text, and "attach rejected" read as the whole feed being down.
                if (attachFailed instanceof app.feedgateway.mtsession.CapacityException) {
                    int limit = engine.maxSocketsPerAppSession();
                    Origin refused = origins.remove(session.getId());
                    long now = System.currentTimeMillis();
                    // WHO holds the slots: the one thing the refusal alone never said.
                    String holders = engine.appSession(id).map(a -> a.socketIds().stream()
                                    .map(sid -> sid + " " + (origins.containsKey(sid) ? origins.get(sid).describe(now) : "origin unknown"))
                                    .collect(Collectors.joining(" | "))).orElse("-");
                    log.warn("socket REFUSED account={} limit={} refused: {} held by: {}", id, limit,
                            refused == null ? "-" : refused.describe(now), holders);
                    gatewayService.recordSocketAttachRefused();
                    reject(session, "connection limit reached for this account (" + limit + " of " + limit + " in use)");
                } else {
                    origins.remove(session.getId());
                    reject(session, "attach rejected");
                }
                return;
            }
        }
        gatewayService.addClient(session);
    }

    /** Best-effort close of a socket we refuse to admit, so no unattached client lingers. */
    private static void reject(WebSocketSession session, String reason) {
        try {
            session.close(CloseStatus.POLICY_VIOLATION.withReason(reason));
        } catch (Exception ignored) {
            // best-effort; the socket is already being torn down
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        gatewayService.handleFootprintBasicMessage(session, message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        origins.remove(session.getId());
        detach(session);
        gatewayService.removeClient(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        origins.remove(session.getId());
        detach(session);
        gatewayService.removeClient(session);
    }

    private void detach(WebSocketSession session) {
        SessionRoutingEngine engine = engineProvider.getIfAvailable();
        if (engine != null) {
            engine.detachSocket(session.getId());
        }
    }
}
