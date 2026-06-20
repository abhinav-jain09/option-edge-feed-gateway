package app.feedgateway;

import app.feedgateway.mtsession.AppSession;
import app.feedgateway.mtsession.SessionRoutingEngine;
import app.feedgateway.mtsession.approval.ApprovalAuthority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * P0 (approval enforcement, mid-session): approval is checked at ticket issuance, but an admin SUSPEND or an
 * approval EXPIRY must take effect on ALREADY-CONNECTED sessions too — not only at the next ticket. This
 * reaper periodically re-consults the {@link ApprovalAuthority} for every active AppSession and tears down
 * (cancels replay + closes every socket) any session that is no longer approved. Fail-closed: an authority
 * error denies, so a flaky approval platform revokes rather than silently keeps streaming.
 *
 * <p>Only wired when {@code gateway.auth.enabled=true} (the same condition as the rest of the secure mode).
 */
@Component
@ConditionalOnProperty(name = "gateway.auth.enabled", havingValue = "true")
public class ApprovalReaper {

    private static final Logger LOG = LoggerFactory.getLogger(ApprovalReaper.class);

    private final SessionRoutingEngine engine;
    private final ApprovalAuthority approvalAuthority;
    private final FeedGatewayService gatewayService;
    private final String issuer;

    public ApprovalReaper(SessionRoutingEngine engine, ApprovalAuthority approvalAuthority,
                          FeedGatewayService gatewayService, GatewaySettings settings) {
        this.engine = engine;
        this.approvalAuthority = approvalAuthority;
        this.gatewayService = gatewayService;
        this.issuer = settings.keycloakIssuer();
    }

    @Scheduled(
            initialDelayString = "${GATEWAY_APPROVAL_RECHECK_MS:60000}",
            fixedDelayString = "${GATEWAY_APPROVAL_RECHECK_MS:60000}")
    public void sweep() {
        recheckOnce(System.currentTimeMillis());
    }

    /** Re-evaluate every active session; tear down those no longer approved. Returns the count revoked. */
    int recheckOnce(long nowMs) {
        int revoked = 0;
        for (AppSession app : engine.activeAppSessions()) {
            ApprovalAuthority.ApprovalDecision decision;
            try {
                decision = approvalAuthority.decide(new ApprovalAuthority.ApprovalQuery(
                        issuer, app.userId(), app.entitlements(), null));
            } catch (RuntimeException e) {
                decision = ApprovalAuthority.ApprovalDecision.DENY; // fail-closed
            }
            if (decision == null || !decision.grantsAccess(nowMs)) {
                gatewayService.logout(app.id()); // cancel replay + close every socket of this session
                revoked++;
            }
        }
        if (revoked > 0) {
            LOG.info("Revoked {} session(s) no longer approved", revoked);
        }
        return revoked;
    }
}
