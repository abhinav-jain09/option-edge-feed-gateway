package app.feedgateway.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.feedgateway.GatewaySettings;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

class ReplayOwnershipAndResolverTest {

    @Test
    void ownershipFailsClosedForUnknownSessionOrPrincipal() {
        ReplaySessionOwnership o = new ReplaySessionOwnership();
        o.bind("s1", "iss|alice");
        assertTrue(o.owns("s1", "iss|alice"));
        assertFalse(o.owns("s1", "iss|bob"), "different principal is not the owner");
        assertFalse(o.owns("unknown", "iss|alice"), "unknown session has no owner");
        assertFalse(o.owns("s1", null));
        o.unbind("s1");
        assertFalse(o.owns("s1", "iss|alice"), "after unbind there is no owner");
    }

    @Test
    void bindingsTrackTheRunPerSession() {
        ReplaySessionBindings b = new ReplaySessionBindings();
        assertEquals(Optional.empty(), b.runFor("s1"));
        b.bind("s1", "r-1");
        assertEquals(Optional.of("r-1"), b.runFor("s1"));
        b.clear("s1");
        assertEquals(Optional.empty(), b.runFor("s1"));
    }

    @Test
    void jwtResolverReturnsSubForValidTokenAndEmptyOtherwise() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("iss|alice")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .claim("scope", "replay").build();
        ReplayPrincipalResolver resolver = new JwtReplayPrincipalResolver(new GatewaySettings(),
                token -> "valid".equals(token) ? jwt : raise());

        assertEquals(Optional.of("iss|alice"), resolver.principal("Bearer valid"));
        assertEquals(Optional.empty(), resolver.principal(null), "no header → empty");
        assertEquals(Optional.empty(), resolver.principal("Bearer bad"), "invalid token → empty (fail closed)");
        assertEquals(Optional.empty(), resolver.principal("NotBearer x"), "wrong scheme → empty");
    }

    private static Jwt raise() {
        throw new JwtException("bad");
    }
}
