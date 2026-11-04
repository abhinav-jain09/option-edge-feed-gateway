package app.feedgateway;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/pattern-alerts}: the audit read of the server-side pattern break alerts (design section 6.4). Behind the same global authentication as the other {@code /api/} readers.
 * <ul>
 * <li>{@code ?date=YYYY-MM-DD[&profile=regular|estimate][&limit=500][&after=0]}: one session, every disposition, per-timeframe coverage; {@code limit} at most 2000.</li>
 * <li>{@code ?days=N[&profile=][&limit=200]}: the newest N (1..7) session dates, newest first, each event with a server-written {@code statusText}; {@code limit} at most 500.</li>
 * </ul>
 * Every parameter is validated by the audit and refused with {@code 400 {"error":"<field>"}}, never clamped. The relay flag off (or refused) is {@code 404}.
 */
@RestController
public class PatternAlertController {
    private final FeedGatewayService service;

    public PatternAlertController(FeedGatewayService service) {
        this.service = service;
    }

    @GetMapping(value = "/api/pattern-alerts", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> patternAlerts(@RequestParam(value = "date", required = false) String date,
                                                @RequestParam(value = "days", required = false) String days,
                                                @RequestParam(value = "profile", required = false) String profile,
                                                @RequestParam(value = "limit", required = false) String limit,
                                                @RequestParam(value = "after", required = false) String after) {
        PatternAlertAudit audit = service.patternAlertAudit();
        if (audit == null) {
            return respond(new PatternAlertAudit.Response(404, "{\"enabled\":false}"));
        }
        if (date != null && days != null || date == null && days == null) {
            return respond(new PatternAlertAudit.Response(400, "{\"error\":\"" + (date == null ? "date" : "params") + "\"}"));
        }
        if (days != null) {
            return respond(after != null ? new PatternAlertAudit.Response(400, "{\"error\":\"after\"}") : audit.byDays(profile, days, limit));
        }
        return respond(audit.byDate(profile, date, limit, after));
    }

    private static ResponseEntity<String> respond(PatternAlertAudit.Response r) {
        return ResponseEntity.status(r.status()).contentType(MediaType.APPLICATION_JSON).body(r.body());
    }
}
