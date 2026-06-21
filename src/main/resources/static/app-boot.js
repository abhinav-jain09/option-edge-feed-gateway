"use strict";
// OptionsEdge authenticated boot (dev gateway stub). SECURITY (P0):
//   * React/ReactDOM are SELF-HOSTED from /vendor and loaded with Subresource Integrity (no third-party CDN);
//   * the only place external data reaches the DOM (a script-load error message) uses textContent / DOM nodes,
//     never innerHTML;
//   * a restrictive CSP (see app.html) confines scripts to 'self'.
// HISTORICAL ACCURACY (P0): the replay window's New-York wall-clock is converted to UTC through the shared
// timezone-aware calendar (America/New_York, DST-correct, rejects nonexistent/ambiguous) — never a fixed offset.
(function () {
  var S = window.sessionStorage;
  var tok = S.getItem("oe_tok"), refresh = S.getItem("oe_refresh");
  var expAt = Number(S.getItem("oe_expAt") || 0);
  var sel = null; try { sel = JSON.parse(S.getItem("oe_sel") || "null"); } catch (e) {}
  if (!tok || !sel) { location.replace("/index.html"); return; }

  // P1 (environment portability): Keycloak config is INJECTED by the deployment via /auth-config — never
  // hardcoded — so token refresh / revoke / logout target the deployed Keycloak, not the user's localhost.
  var KC = null, CLIENT = null, TOKEN_URL = null, REVOKE_URL = null, LOGOUT_URL = null;
  function loadAuthConfig() {
    return fetch("/auth-config", { headers: { "Accept": "application/json" } })
      .then(function (r) { if (!r.ok) throw new Error("auth-config " + r.status); return r.json(); })
      .then(function (c) {
        if (!c.issuer || !c.clientId) throw new Error("auth-config incomplete");
        KC = String(c.issuer).replace(/\/+$/, "");
        CLIENT = c.clientId;
        TOKEN_URL = KC + "/protocol/openid-connect/token";
        REVOKE_URL = KC + "/protocol/openid-connect/revoke";
        LOGOUT_URL = KC + "/protocol/openid-connect/logout";
      });
  }

  function persist() { S.setItem("oe_tok", tok); S.setItem("oe_refresh", refresh); S.setItem("oe_expAt", String(expAt)); }

  function ensureToken() {
    if (Date.now() < expAt) return Promise.resolve(tok);
    var body = new URLSearchParams({ grant_type: "refresh_token", client_id: CLIENT, refresh_token: refresh });
    return fetch(TOKEN_URL, { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body })
      .then(function (r) { if (!r.ok) { location.replace("/index.html"); return null; } return r.json(); })
      .then(function (j) { if (!j) return null; tok = j.access_token; refresh = j.refresh_token; expAt = Date.now() + (j.expires_in - 30) * 1000; persist(); return tok; })
      .catch(function () { return tok; });
  }

  function mintTicket() {
    return ensureToken().then(function (t) {
      if (!t) return null;
      return fetch("/api/ws-ticket", {
        method: "POST",
        headers: { "Authorization": "Bearer " + t, "Content-Type": "application/json" },
        body: JSON.stringify({ source: sel.source, symbol: sel.symbol, expiry: sel.expiry, strikeLo: sel.strikeLo, strikeHi: sel.strikeHi })
      }).then(function (r) { return r.ok ? r.json() : null; }).then(function (j) {
        if (j && j.appSessionId) appSessionId = j.appSessionId;
        return j ? j.ticket : null;
      }).catch(function () { return null; });
    });
  }

  var appSessionId = null;
  var currentTicket = null, minting = null;
  function refreshTicket() {
    if (minting) return minting;
    minting = mintTicket().then(function (tk) { if (tk) currentTicket = tk; minting = null; return tk; });
    return minting;
  }

  window.__OPTIONS_EDGE_ENV__ = { VITE_APP_PROFILE: "dev" };

  function configJson() {
    return {
      appProfile: "dev", apiBaseUrl: "", missionControlUrl: "", provider: "IB",
      marketDataSource: sel.source, symbol: sel.symbol, expiry: sel.expiry, selectionEpoch: 0,
      tradingClass: "", secType: "", exchange: "", port: 4001, clientId: 0,
      maxStrikes: sel.maxStrikes || 45, delayed: false,
      feedGatewayEnabled: true, feedGatewayAvailable: true, feedGatewayWsUrl: "", databentoReplayUiEnabled: false
    };
  }
  function jsonResponse(obj) { return new Response(JSON.stringify(obj), { status: 200, headers: { "Content-Type": "application/json" } }); }

  var realFetch = window.fetch.bind(window);
  window.fetch = function (input, init) {
    var url = typeof input === "string" ? input : (input && input.url) || "";
    if (url.indexOf("/api/config") >= 0) return Promise.resolve(jsonResponse(configJson()));
    if (url.indexOf("/api/connect") >= 0) {
      try {
        if (init && init.body) {
          var p = new URLSearchParams(init.body);
          if (p.get("marketDataSource")) sel.source = p.get("marketDataSource");
          if (p.get("symbol")) sel.symbol = p.get("symbol");
          if (p.get("expiry")) sel.expiry = p.get("expiry");
          if (p.get("maxStrikes")) sel.maxStrikes = Number(p.get("maxStrikes"));
          S.setItem("oe_sel", JSON.stringify(sel));
        }
      } catch (e) {}
      currentTicket = null; refreshTicket();
      return Promise.resolve(jsonResponse({ config: configJson() }));
    }
    return realFetch(input, init);
  };

  var RealWS = window.WebSocket;
  function PatchedWS(url, protocols) {
    var u = String(url);
    if (u.indexOf("/ws/events") >= 0 && currentTicket) {
      // P1: carry the single-use ticket in the APPROVED subprotocol, never the URL — a ticket in the query
      // string leaks into proxy/access logs and could be replayed from any origin before redemption.
      var sub = "oe.ticket." + currentTicket;
      currentTicket = null;
      setTimeout(refreshTicket, 0); // pre-mint the next ticket for reconnects
      if (protocols === undefined) return new RealWS(u, sub);
      return new RealWS(u, [].concat(protocols).concat(sub));
    }
    return protocols === undefined ? new RealWS(u) : new RealWS(u, protocols);
  }
  PatchedWS.prototype = RealWS.prototype;
  PatchedWS.CONNECTING = RealWS.CONNECTING; PatchedWS.OPEN = RealWS.OPEN;
  PatchedWS.CLOSING = RealWS.CLOSING; PatchedWS.CLOSED = RealWS.CLOSED;
  window.WebSocket = PatchedWS;

  // Load a script from our OWN origin, verifying Subresource Integrity when a hash is supplied.
  function loadScript(src, integrity) {
    return new Promise(function (res, rej) {
      var s = document.createElement("script");
      s.src = src;
      if (integrity) { s.integrity = integrity; s.crossOrigin = "anonymous"; }
      s.onload = res;
      s.onerror = function () { rej(new Error("failed to load " + src)); };
      document.body.appendChild(s);
    });
  }

  // ---- Live↔Replay control bar ----
  var mode = "LIVE";
  function el(id) { return document.getElementById(id); }
  function setMode(next) {
    mode = next;
    var badge = el("oe-replay-badge"); if (badge) { badge.className = next; badge.textContent = next.replace(/_/g, " "); }
    var running = next === "REPLAY_RUNNING", replayShown = running || next === "REPLAY_COMPLETE";
    if (el("oe-rp-start-btn")) el("oe-rp-start-btn").disabled = running;
    if (el("oe-rp-stop-btn")) el("oe-rp-stop-btn").disabled = !running;
    if (el("oe-rp-live-btn")) el("oe-rp-live-btn").disabled = !replayShown;
    // A runId is one-shot. Once a replay ENDS — returned to live (LIVE) or finished/stopped
    // (REPLAY_COMPLETE) — clear the field so a stale orchestrated-run id can never silently turn the NEXT
    // replay into a run-backed one, including a fresh Start issued from the completed state without first
    // returning to live. startReplay() captures the value before any mode change, so this never races the
    // user's input for the current start.
    if (next === "LIVE" || next === "REPLAY_COMPLETE") {
      var ri = el("oe-rp-run-id"); if (ri) { ri.value = ""; }
    }
  }
  function rpMsg(text) { var m = el("oe-rp-msg"); if (m) m.textContent = text || ""; }

  // Convert the date + ET wall-clock inputs to a UTC instant via the shared timezone-aware calendar
  // (America/New_York). This is DST-correct (standard vs daylight) and rejects nonexistent/ambiguous
  // local times — NOT a hardcoded UTC offset.
  function etToUtc(dateStr, timeStr) {
    var hhmm = timeStr.length === 5 ? timeStr : timeStr.slice(0, 5);
    var cal = window.OptionChainMarketCalendar;
    if (!cal || !cal.exchangeLocalToUtcInstant) { throw new Error("calendar not loaded"); }
    return cal.exchangeLocalToUtcInstant(dateStr + "T" + hhmm);
  }
  function authHeaders(t) { return { "Authorization": "Bearer " + t, "Content-Type": "application/json" }; }

  function startReplay() {
    rpMsg("");
    if (!appSessionId) { rpMsg("no session yet"); return; }
    var date = el("oe-rp-date").value, expiry = date.replace(/-/g, "");
    var startUtc, endUtc;
    try { startUtc = etToUtc(date, el("oe-rp-start").value); endUtc = etToUtc(date, el("oe-rp-end").value); }
    catch (e) { rpMsg(e.message); return; }
    // Optional orchestrated-run id: when present the gateway authorizes it against the orchestrator and
    // streams that run's *.replay.<runId>.* topics instead of slicing the live topics by time. Sent only
    // when non-empty so a blank field stays a plain live-slice replay.
    var runIdInput = el("oe-rp-run-id");
    var runId = runIdInput && runIdInput.value ? runIdInput.value.trim() : "";
    setMode("REPLAY_RUNNING");
    ensureToken().then(function (t) {
      if (!t) { rpMsg("session expired"); setMode("LIVE"); return; }
      var body = { sessionId: appSessionId, symbol: sel.symbol, expiry: expiry, startUtc: startUtc, endUtc: endUtc, maxRecords: 50000 };
      if (runId) { body.runId = runId; }
      return fetch("/api/replay/historical/start", {
        method: "POST", headers: authHeaders(t),
        body: JSON.stringify(body)
      }).then(function (r) {
        return r.json().catch(function () { return {}; }).then(function (j) {
          if (!r.ok) { rpMsg(j.error || ("replay failed (" + r.status + ")")); setMode("LIVE"); }
        });
      });
    }).catch(function () { rpMsg("replay request failed"); setMode("LIVE"); });
  }

  function modeCall(path, nextOnOk, label) {
    if (!appSessionId) return;
    ensureToken().then(function (t) {
      if (!t) { rpMsg("session expired"); return; }
      return fetch(path, { method: "POST", headers: authHeaders(t), body: JSON.stringify({ sessionId: appSessionId }) })
        .then(function (r) { return r.json().catch(function () { return {}; }).then(function (j) {
          if (r.ok) { setMode(nextOnOk); rpMsg(""); } else { rpMsg(j.error || (label + " failed")); }
        }); });
    }).catch(function () { rpMsg(label + " failed"); });
  }

  // P0 (real sign-out): tear down the server AppSession (closes the WS), revoke the refresh token, wipe all
  // stored credentials + selection, then end the Keycloak SSO session — leaving nothing to resume.
  // (REVOKE_URL / LOGOUT_URL are set from the injected config in loadAuthConfig.)
  var SESSION_KEYS = ["oe_tok", "oe_refresh", "oe_expAt", "oe_sel", "pkce_verifier", "pkce_state"];
  function doLogout() {
    var accessTok = tok, refreshTok = refresh;
    var serverLogout = accessTok
      ? fetch("/api/logout", { method: "POST", headers: { "Authorization": "Bearer " + accessTok } }).catch(function () {})
      : Promise.resolve();
    var revoke = refreshTok
      ? fetch(REVOKE_URL, { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" },
          body: new URLSearchParams({ client_id: CLIENT, token: refreshTok, token_type_hint: "refresh_token" }) }).catch(function () {})
      : Promise.resolve();
    Promise.all([serverLogout, revoke]).then(function () {
      tok = null; refresh = null; expAt = 0; currentTicket = null;
      SESSION_KEYS.forEach(function (k) { try { S.removeItem(k); } catch (e) {} });
      var redirect = location.origin + "/index.html";
      location.assign(LOGOUT_URL + "?" + new URLSearchParams({ client_id: CLIENT, post_logout_redirect_uri: redirect }));
    });
  }

  function setupReplayBar() {
    var bar = el("oe-replay-bar"); if (!bar) return;
    bar.style.display = "flex";
    setMode("LIVE");
    el("oe-rp-start-btn").onclick = startReplay;
    el("oe-rp-stop-btn").onclick = function () { modeCall("/api/replay/historical/stop", "REPLAY_COMPLETE", "stop"); };
    el("oe-rp-live-btn").onclick = function () { setMode("RETURNING_TO_LIVE"); modeCall("/api/replay/live/resume", "LIVE", "return to live"); };
    if (el("oe-signout-btn")) el("oe-signout-btn").onclick = doLogout;
    // Populate the runId datalist with the caller's orchestrated runs (PR-2). Best-effort: on any failure
    // the field stays a plain free-text input (PR-1 behavior). Refresh on focus so the list stays current.
    var runIdField = el("oe-rp-run-id");
    if (runIdField) { runIdField.addEventListener("focus", loadReplayRuns); }
    loadReplayRuns();
  }

  // Fetch the caller's orchestrated replay runs from the gateway proxy and fill the #oe-rp-run-list
  // datalist. Built with DOM nodes (value/label as properties, NEVER innerHTML) so a run field can't inject.
  function loadReplayRuns() {
    var list = el("oe-rp-run-list"); if (!list) { return; }
    // Clear FIRST so a failed/empty refresh degrades to plain free-text (no stale suggestions survive);
    // a successful fetch repopulates below.
    list.textContent = "";
    ensureToken().then(function (t) {
      if (!t) { return; }
      return fetch("/api/replay/historical/runs", { headers: { "Authorization": "Bearer " + t } })
        .then(function (r) { return r.ok ? r.json() : []; })
        .then(function (runs) {
          (Array.isArray(runs) ? runs : []).forEach(function (run) {
            if (!run || !run.runId) { return; }
            var opt = document.createElement("option");
            opt.value = run.runId;
            var windowLabel = (run.startTime && run.endTime) ? (run.startTime + "-" + run.endTime) : "";
            opt.label = [run.state, run.replayDate, windowLabel].filter(Boolean).join(" ");
            list.appendChild(opt);
          });
        });
    }).catch(function () { /* degrade silently to free-text entry */ });
  }

  // Build the boot-error box with DOM nodes + textContent (NEVER innerHTML) so an error message can't inject.
  function showBootError(message) {
    var root = document.getElementById("optionChainRoot");
    if (!root) return;
    root.textContent = "";
    var box = document.createElement("div");
    box.id = "oe-booterr";
    box.appendChild(document.createTextNode("Could not load the option chain (" + message + ")."));
    box.appendChild(document.createElement("br"));
    box.appendChild(document.createTextNode("Check your network, then "));
    var retry = document.createElement("a"); retry.href = "/app.html"; retry.textContent = "retry";
    box.appendChild(retry);
    box.appendChild(document.createTextNode(" or "));
    var signin = document.createElement("a"); signin.href = "/index.html"; signin.textContent = "sign in again";
    box.appendChild(signin);
    box.appendChild(document.createTextNode("."));
    root.appendChild(box);
  }

  // Boot: load the injected Keycloak config, mint the first ticket, load self-hosted React (SRI-pinned),
  // then the option-chain app.
  var REACT_SRI = "sha384-DGyLxAyjq0f9SPpVevD6IgztCFlnMF6oW/XQGmfe+IsZ8TqEiDrcHkMLKI6fiB/Z";
  var REACT_DOM_SRI = "sha384-gTGxhz21lVGYNMcdJOyq01Edg0jhn/c22nsx0kyqP0TxaV5WVdsSH1fSDUf5YJj1";
  loadAuthConfig()
    .then(function () { return refreshTicket(); })
    .then(function () { return loadScript("/vendor/react.production.min.js", REACT_SRI); })
    .then(function () { return loadScript("/vendor/react-dom.production.min.js", REACT_DOM_SRI); })
    .then(function () { return loadScript("/option-chain.js"); })
    .then(function () { setupReplayBar(); })
    .catch(function (err) { showBootError(err && err.message || "error"); });
})();
