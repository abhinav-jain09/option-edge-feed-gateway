package app.feedgateway.mtsession.approval;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.feedgateway.mtsession.approval.ApprovalAuthority.ApprovalDecision;
import app.feedgateway.mtsession.approval.ApprovalAuthority.ApprovalQuery;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Approval must be authoritative and FAIL CLOSED. These pin: the role-claim source approves only an
 * admin-granted role; the HTTP platform approves only a 2xx APPROVED record and denies on every other
 * status / transport failure / suspended / expired record.
 */
class ApprovalAuthorityTest {

    private static final long NOW = 1_000_000L;

    private static ApprovalQuery q(Set<String> roles) {
        return new ApprovalQuery("https://kc/realms/optionsedge", "u1", roles, null);
    }

    @Test
    void denyAllNeverApproves() {
        assertFalse(new ApprovalAuthority.DenyAll().decide(q(Set.of("oe-approved"))).grantsAccess(NOW));
    }

    @Test
    void roleClaimApprovesOnlyTheAdminGrantedRole() {
        RoleClaimApprovalAuthority authz = new RoleClaimApprovalAuthority("oe-approved");
        assertTrue(authz.decide(q(Set.of("user", "oe-approved"))).grantsAccess(NOW), "approved role grants access");
        assertFalse(authz.decide(q(Set.of("user"))).grantsAccess(NOW), "a self-registered user without the role is denied");
        assertFalse(authz.decide(q(Set.of())).grantsAccess(NOW));
    }

    @Test
    void roleClaimRespectsTokenExpiry() {
        RoleClaimApprovalAuthority authz = new RoleClaimApprovalAuthority("oe-approved");
        ApprovalQuery expired = new ApprovalQuery("iss", "u1", Set.of("oe-approved"), Instant.ofEpochMilli(NOW - 1));
        assertFalse(authz.decide(expired).grantsAccess(NOW), "an expired token cannot be approved");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static HttpClient http(int status, String body) throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(status);
        when(resp.body()).thenReturn(body);
        when(client.send(any(HttpRequest.class), any())).thenReturn(resp);
        return client;
    }

    private static HttpApprovalAuthority httpAuthority(HttpClient client) {
        return new HttpApprovalAuthority("https://config-control", null, Duration.ofSeconds(2), client);
    }

    @Test
    void httpApprovedRecordGrantsAccess() throws Exception {
        var authz = httpAuthority(http(200, "{\"state\":\"APPROVED\"}"));
        assertTrue(authz.decide(q(Set.of("user"))).grantsAccess(NOW));
    }

    @Test
    void httpSuspendedRecordIsDenied() throws Exception {
        var authz = httpAuthority(http(200, "{\"state\":\"SUSPENDED\"}"));
        assertFalse(authz.decide(q(Set.of("user"))).grantsAccess(NOW));
    }

    @Test
    void httpExpiredApprovalIsDenied() throws Exception {
        var authz = httpAuthority(http(200, "{\"state\":\"APPROVED\",\"expiresAtMs\":" + (NOW - 1) + "}"));
        assertFalse(authz.decide(q(Set.of("user"))).grantsAccess(NOW), "a past expiry denies");
    }

    @Test
    void httpNotFoundAndForbiddenAreDenied() throws Exception {
        assertFalse(httpAuthority(http(404, "")).decide(q(Set.of("user"))).grantsAccess(NOW));
        assertFalse(httpAuthority(http(403, "{}")).decide(q(Set.of("user"))).grantsAccess(NOW));
    }

    @Test
    void httpTransportFailureFailsClosed() throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(HttpRequest.class), any())).thenThrow(new IOException("platform down"));
        assertFalse(httpAuthority(client).decide(q(Set.of("user"))).grantsAccess(NOW));
    }

    @Test
    void httpMalformedBodyIsDenied() throws Exception {
        assertFalse(httpAuthority(http(200, "not json")).decide(q(Set.of("user"))).grantsAccess(NOW));
    }
}
