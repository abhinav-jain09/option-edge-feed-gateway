package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/** {@code GET /api/pattern-alerts}: the flag, the exclusive date / days forms and the audit's own validation (design 6.4). */
class PatternAlertControllerTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private static PatternAlertController controller(boolean on) {
        GatewaySettings settings = spy(new GatewaySettings());
        doReturn(on).when(settings).patternAlertsEnabled();
        return new PatternAlertController(new FeedGatewayService(settings, JSON, new HpsfGatewayViewMapper(), null));
    }

    @Test
    void theFlagOffAnswers404WithoutTouchingKafka() {
        ResponseEntity<String> r = controller(false).patternAlerts("2026-11-03", null, null, null, null);
        assertEquals(404, r.getStatusCode().value());
        assertEquals("{\"enabled\":false}", r.getBody());
    }

    @Test
    void exactlyOneOfDateAndDaysIsRequired() {
        PatternAlertController c = controller(true);
        assertEquals("{\"error\":\"date\"}", c.patternAlerts(null, null, null, null, null).getBody());
        assertEquals(400, c.patternAlerts(null, null, null, null, null).getStatusCode().value());
        assertEquals("{\"error\":\"params\"}", c.patternAlerts("2026-11-03", "7", null, null, null).getBody());
        assertEquals("{\"error\":\"after\"}", c.patternAlerts(null, "7", null, null, "3").getBody(), "after belongs to the date form");
    }

    @Test
    void theAuditsValidationReachesTheCallerAs400WithTheFieldName() {
        PatternAlertController c = controller(true);
        assertEquals("{\"error\":\"date\"}", c.patternAlerts("2026-13-01", null, null, null, null).getBody());
        assertEquals("{\"error\":\"limit\"}", c.patternAlerts("2026-11-03", null, null, "2001", null).getBody());
        assertEquals("{\"error\":\"days\"}", c.patternAlerts(null, "8", null, null, null).getBody());
        assertEquals("{\"error\":\"profile\"}", c.patternAlerts(null, "7", "x", null, null).getBody());
        assertTrue(c.patternAlerts("2026-11-03", null, null, "abc", null).getHeaders().getContentType().toString().startsWith("application/json"));
    }
}
