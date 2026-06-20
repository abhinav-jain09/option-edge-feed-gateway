package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * P0 (browser XSS / supply-chain): structural guards over the gateway's login + app pages. These assert the
 * sinks that allowed credential-stealing XSS are GONE and stay gone:
 * <ul>
 *   <li>no external/reflected data (OIDC error_description, token claims) is concatenated into innerHTML —
 *       it must reach the DOM only via textContent;</li>
 *   <li>a restrictive CSP confines scripts to 'self' (no inline JS, no 'unsafe-inline'/'unsafe-eval');</li>
 *   <li>React is self-hosted under /vendor with Subresource Integrity — never a third-party CDN;</li>
 *   <li>the replay window is converted timezone-aware, not with a hardcoded UTC offset.</li>
 * </ul>
 */
class StaticAssetSecurityTest {

    private static final Path STATIC = Path.of("src/main/resources/static");

    private static String read(String name) throws IOException {
        return Files.readString(STATIC.resolve(name));
    }

    private static String cspOf(String html) {
        var m = Pattern.compile("Content-Security-Policy\"\\s+content=\"([^\"]+)\"").matcher(html);
        assertTrue(m.find(), "page must declare a Content-Security-Policy");
        return m.group(1);
    }

    private void assertRestrictiveCsp(String page) throws IOException {
        String csp = cspOf(read(page));
        assertTrue(csp.contains("default-src 'none'"), page + " CSP must default-deny");
        assertTrue(csp.contains("script-src 'self'"), page + " CSP must restrict scripts to self");
        assertTrue(csp.contains("frame-ancestors 'none'"), page + " CSP must block framing");
        assertTrue(csp.contains("base-uri 'none'"), page + " CSP must lock base-uri");
        // The script-src directive must not weaken to inline/eval/CDN.
        String scriptSrc = csp.replaceAll(".*script-src([^;]*);.*", "$1");
        assertFalse(scriptSrc.contains("'unsafe-inline'"), page + " script-src must not allow unsafe-inline");
        assertFalse(scriptSrc.contains("'unsafe-eval'"), page + " script-src must not allow unsafe-eval");
        assertFalse(scriptSrc.contains("unpkg") || scriptSrc.contains("http"), page + " script-src must be self-only");
    }

    private void assertNoInlineScript(String page) throws IOException {
        String html = read(page);
        // Every <script ...> must carry a src= (no inline JS body).
        var m = Pattern.compile("<script(\\s[^>]*)?>").matcher(html);
        while (m.find()) {
            String tag = m.group();
            assertTrue(tag.contains("src="), page + " has an inline <script> (must be externalized): " + tag);
        }
    }

    @Test
    void loginPageHasRestrictiveCspAndNoInlineScriptOrCdn() throws IOException {
        assertRestrictiveCsp("index.html");
        assertNoInlineScript("index.html");
        assertFalse(read("index.html").contains("unpkg"), "login page must not load third-party scripts");
    }

    @Test
    void appPageHasRestrictiveCspAndNoInlineScriptOrCdn() throws IOException {
        assertRestrictiveCsp("app.html");
        assertNoInlineScript("app.html");
        assertFalse(read("app.html").contains("unpkg"), "app page must not load React from a CDN");
    }

    @Test
    void externalDataIsNeverConcatenatedIntoInnerHtml() throws IOException {
        // The XSS was: innerHTML = ... + error_description / given_name / preferred_username + ...
        // These tokens are unambiguous external data — they must never appear inside an innerHTML assignment
        // (the constant UI templates contain no such tokens; the greeting uses given_name via textContent).
        Pattern unsafe = Pattern.compile(
                "innerHTML\\s*=\\s*[^;]*(error_description|given_name|preferred_username)");
        for (String js : new String[]{"login.js", "app-boot.js"}) {
            assertFalse(unsafe.matcher(read(js)).find(),
                    js + " must not concatenate external data into innerHTML");
        }
        // app-boot builds its error box with DOM nodes — no innerHTML assignment at all.
        assertFalse(Pattern.compile("innerHTML\\s*=").matcher(read("app-boot.js")).find(),
                "app-boot.js must not assign innerHTML");
        // The reflected error and the token-claim greeting are rendered via textContent.
        assertTrue(read("login.js").contains("m.textContent = err"), "error_description must render via textContent");
        assertTrue(read("login.js").contains("greet\").textContent"), "token-claim greeting must render via textContent");
    }

    @Test
    void reactIsSelfHostedWithSubresourceIntegrity() throws IOException {
        String boot = read("app-boot.js");
        assertTrue(boot.contains("/vendor/react.production.min.js"), "React must be self-hosted under /vendor");
        assertTrue(boot.contains("/vendor/react-dom.production.min.js"), "ReactDOM must be self-hosted under /vendor");
        assertTrue(boot.contains("sha384-"), "self-hosted React must be loaded with Subresource Integrity");
        assertTrue(boot.contains("integrity"), "loadScript must set the integrity attribute");
        assertTrue(Files.size(STATIC.resolve("vendor/react.production.min.js")) > 1000, "vendored react present");
        assertTrue(Files.size(STATIC.resolve("vendor/react-dom.production.min.js")) > 1000, "vendored react-dom present");
    }

    @Test
    void signOutFullyTearsDownTheSessionEverywhere() throws IOException {
        // P0 (real sign-out): the logout handlers must (a) tear down the server AppSession, (b) revoke the
        // refresh token, (c) wipe ALL stored credentials + selection, (d) end the Keycloak SSO session.
        for (String js : new String[]{"login.js", "app-boot.js"}) {
            String src = read(js);
            assertTrue(src.contains("/api/logout"), js + " must tear down the server-side AppSession");
            assertTrue(src.contains("openid-connect/revoke"), js + " must revoke the refresh token");
            assertTrue(src.contains("openid-connect/logout"), js + " must end the Keycloak SSO session");
            for (String key : new String[]{"oe_tok", "oe_refresh", "oe_expAt", "oe_sel"}) {
                assertTrue(src.contains(key), js + " must clear stored " + key);
            }
            assertTrue(src.contains("removeItem"), js + " must remove stored credentials, not just memory");
        }
        // The old broken handler only nulled `tok` and redrew the view — that exact shortcut must be gone.
        assertFalse(read("login.js").contains("tok=null; if(ws)ws.close(); viewLogin()"),
                "login.js must not use the no-op logout that left stored tokens behind");
    }

    @Test
    void wsTicketTravelsInSubprotocolNotTheUrl() throws IOException {
        // P1: a ticket in the URL query string leaks into proxy/access logs. The browser must carry it in
        // the approved oe.ticket.<id> subprotocol instead.
        String boot = read("app-boot.js");
        assertTrue(boot.contains("\"oe.ticket.\""), "ticket must be sent via the oe.ticket.* subprotocol");
        assertFalse(boot.contains("\"ticket=\"") || boot.contains("+ \"ticket=\""),
                "the ticket must NOT be appended to the WebSocket URL as a query parameter");
    }

    @Test
    void replayWindowUsesTimezoneAwareConversionNotAHardcodedOffset() throws IOException {
        String boot = read("app-boot.js");
        assertFalse(boot.contains("\"-04:00\"") || boot.contains("'-04:00'"),
                "replay conversion must not hardcode a UTC offset");
        assertTrue(boot.contains("exchangeLocalToUtcInstant"),
                "replay conversion must use the timezone-aware America/New_York calendar");
    }
}
