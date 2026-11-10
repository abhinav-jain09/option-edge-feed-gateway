package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.feedgateway.mtsession.ConcurrencyLimits;
import app.feedgateway.mtsession.MarketDataSource;
import app.feedgateway.mtsession.Selection;
import app.feedgateway.mtsession.SessionRoutingEngine;
import app.feedgateway.mtsession.StrikeWindow;
import app.feedgateway.mtsession.SubscriptionManager;
import app.feedgateway.mtsession.gateway.TicketHandshakeInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * The per-account socket limit on a REAL wire: a real server on a real port, the real
 * {@link FeedWebSocketHandler}, {@link FeedGatewayService} and {@link SessionRoutingEngine} (limit 4), and five
 * real WebSocket clients (the JDK's). Only the sign-in is replaced: the handshake binds every socket to the same
 * AppSession, as the bearer interceptor does for one account.
 *
 * <p>Owner rule (2026-11-10): four connections work; the fifth is refused with a message that says the count was
 * exceeded; no new connection is opened; the four that were already open keep working.
 */
@SpringBootTest(
        classes = {SocketLimitOverTheWireTest.Wire.class, SocketLimitOverTheWireTest.Registration.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SocketLimitOverTheWireTest {

    @Configuration
    @EnableAutoConfiguration(exclude = {
            SecurityAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class,
            OAuth2ResourceServerAutoConfiguration.class})
    static class Wire {
        static final Selection SPX = new Selection(MarketDataSource.DATABENTO, "SPX", "20260617", StrikeWindow.ALL);

        @Bean
        SessionRoutingEngine engine() {
            SessionRoutingEngine e = new SessionRoutingEngine(new ConcurrencyLimits(1, 4, 100), new SubscriptionManager());
            e.registerAppSession("app:u1", "u1", SPX, Set.of());
            return e;
        }

        @Bean
        FeedGatewayService gateway(SessionRoutingEngine engine) {
            return new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), engine);
        }

        @Bean
        FeedWebSocketHandler handler(FeedGatewayService gateway, org.springframework.beans.factory.ObjectProvider<SessionRoutingEngine> engine) {
            return new FeedWebSocketHandler(gateway, engine);
        }
    }

    /** The registration, in its own configuration so the handler bean it needs is not under construction. */
    @Configuration
    @EnableWebSocket
    static class Registration implements WebSocketConfigurer {
        private final FeedWebSocketHandler handler;

        Registration(FeedWebSocketHandler handler) {
            this.handler = handler;
        }

        @Override
        public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
            registry.addHandler(handler, "/ws/events").setAllowedOrigins("*").addInterceptors(new HandshakeInterceptor() {
                @Override
                public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                               WebSocketHandler wsHandler, Map<String, Object> attributes) {
                    attributes.put(TicketHandshakeInterceptor.ATTR_APP_SESSION_ID, "app:u1");
                    return true;
                }

                @Override
                public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                           WebSocketHandler wsHandler, Exception exception) {
                }
            });
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    FeedGatewayService gateway;

    @Autowired
    SessionRoutingEngine engine;

    /** One real client: every text frame, the close code and reason, and whether it ever opened. */
    private static final class Client implements WebSocket.Listener {
        final List<String> frames = new CopyOnWriteArrayList<>();
        final AtomicReference<String> closed = new AtomicReference<>();
        final CountDownLatch closeLatch = new CountDownLatch(1);
        final CountDownLatch firstFrame = new CountDownLatch(1);
        volatile WebSocket socket;

        @Override
        public void onOpen(WebSocket webSocket) {
            socket = webSocket;
            webSocket.request(Long.MAX_VALUE);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            frames.add(data.toString());
            firstFrame.countDown();
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closed.set(statusCode + " " + reason);
            closeLatch.countDown();
            return null;
        }

        boolean sawFrameContaining(String text) {
            return frames.stream().anyMatch(f -> f.contains(text));
        }
    }

    private Client connect(HttpClient http) throws Exception {
        Client c = new Client();
        http.newWebSocketBuilder().buildAsync(URI.create("ws://localhost:" + port + "/ws/events"), c).get(10, TimeUnit.SECONDS);
        return c;
    }

    private static void eventually(java.util.function.BooleanSupplier condition, String what) throws Exception {
        long end = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < end) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("timed out waiting for: " + what);
    }

    @Test
    void fourConnectionsWorkTheFifthIsRefusedWithTheCountAndTheOldFourKeepWorking() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        List<Client> old = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            Client c = connect(http);
            assertTrue(c.firstFrame.await(10, TimeUnit.SECONDS), "connection " + i + " got no frame: it did not work");
            old.add(c);
        }
        eventually(() -> engine.socketIdsOf("app:u1").size() == 4, "four sockets attached");

        // the fifth
        Client fifth = connect(http);

        // 1. a clear message that the count was exceeded, as a real close frame
        assertTrue(fifth.closeLatch.await(10, TimeUnit.SECONDS), "the fifth connection was not closed");
        assertEquals("1008 connection limit reached for this account (4 of 4 in use)", fifth.closed.get());
        // 2. no new connection was opened: the refused socket received no data and is no client
        assertTrue(fifth.frames.isEmpty(), "the refused connection must receive nothing: " + fifth.frames);
        assertEquals(4, engine.socketIdsOf("app:u1").size());
        // 3. the old ones still work: still open, and every one receives a frame sent AFTER the refusal
        for (Client c : old) {
            assertFalse(c.closeLatch.getCount() == 0, "an old connection was closed: " + c.closed.get());
        }
        gateway.streamRithmicDepth("{\"after\":\"the-refusal\"}");
        for (Client c : old) {
            eventually(() -> c.sawFrameContaining("the-refusal"), "an old connection received the frame sent after the refusal");
        }
        assertTrue(fifth.frames.isEmpty(), "the refused connection received the later frame");

        // a freed slot is usable again
        old.get(0).socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(10, TimeUnit.SECONDS);
        eventually(() -> engine.socketIdsOf("app:u1").size() == 3, "a closed connection frees its slot");
        Client sixth = connect(http);
        assertTrue(sixth.firstFrame.await(10, TimeUnit.SECONDS), "a slot freed by a closed tab must be usable");

        String metrics = gateway.metrics();
        assertTrue(metrics.contains("options_edge_gateway_socket_attach_refused_total 1"), metrics);
        assertTrue(metrics.contains("options_edge_gateway_app_session_sockets_max 4"), metrics);

        for (Client c : old) {
            if (c.socket != null) {
                c.socket.abort();
            }
        }
        sixth.socket.abort();
    }
}
