"use strict";
// OptionsEdge sign-in (dev gateway stub). SECURITY INVARIANT (P0 — XSS):
//   * the HTML template strings below are CONSTANT — they contain NO external/user/token data;
//   * EVERY piece of external data (the OIDC error_description, token claims such as preferred_username
//     and given_name) is written ONLY via textContent, never concatenated into an HTML string.
// A restrictive CSP (see index.html) and self-hosted scripts back this up. Do not reintroduce
// `innerHTML = ... + external + ...`.

// P1 (environment portability): the Keycloak issuer + client id are INJECTED by the deployment via
// /auth-config — never hardcoded — so a remote browser talks to the deployed Keycloak, not localhost.
let KC = null, CLIENT = null, TOKEN_URL, AUTH_URL, REG_URL, REVOKE_URL, LOGOUT_URL;
const SESSION_KEYS = ["oe_tok", "oe_refresh", "oe_expAt", "oe_sel", "pkce_verifier", "pkce_state"];
const REDIRECT_URI = location.origin + "/";

async function loadAuthConfig() {
  const r = await fetch("/auth-config", { headers: { "Accept": "application/json" } });
  if (!r.ok) throw new Error("auth-config " + r.status);
  const c = await r.json();
  if (!c.issuer || !c.clientId) throw new Error("auth-config incomplete");
  KC = c.issuer.replace(/\/+$/, "");
  CLIENT = c.clientId;
  TOKEN_URL = KC + "/protocol/openid-connect/token";
  AUTH_URL = KC + "/protocol/openid-connect/auth";
  REG_URL = KC + "/protocol/openid-connect/registrations";
  REVOKE_URL = KC + "/protocol/openid-connect/revoke";
  LOGOUT_URL = KC + "/protocol/openid-connect/logout";
}
let tok = null, refresh = null, expAt = 0, ws = null;
const main = document.getElementById("main");
const el = id => document.getElementById(id);

function parseJwt(t){ try { const s=t.split(".")[1].replace(/-/g,"+").replace(/_/g,"/"); return JSON.parse(atob(s)); } catch(e){ return {}; } }

// ---- PKCE (authorization-code flow, required by the options-edge-web client) ----
function b64url(buf){ return btoa(String.fromCharCode.apply(null, new Uint8Array(buf))).replace(/\+/g,"-").replace(/\//g,"_").replace(/=+$/,""); }
function randVerifier(){ const a=new Uint8Array(32); crypto.getRandomValues(a); return b64url(a.buffer); }
async function challenge(v){ const d=await crypto.subtle.digest("SHA-256", new TextEncoder().encode(v)); return b64url(d); }

async function startRedirect(endpoint) {
  const verifier = randVerifier(), state = randVerifier();
  sessionStorage.setItem("pkce_verifier", verifier);
  sessionStorage.setItem("pkce_state", state);
  const cc = await challenge(verifier);
  const url = endpoint + "?" + new URLSearchParams({
    client_id: CLIENT, response_type: "code", scope: "openid",
    redirect_uri: REDIRECT_URI, state,
    code_challenge: cc, code_challenge_method: "S256"
  }).toString();
  location.assign(url);
}

// On return from Keycloak (?code=...), exchange the code for tokens.
async function handleRedirectCallback() {
  const q = new URLSearchParams(location.search);
  const code = q.get("code");
  if (q.get("error")) {
    // error_description is attacker-controllable (it is reflected from the URL). It is rendered ONLY
    // through textContent inside viewLogin — never as HTML — so it cannot inject script.
    history.replaceState({}, "", REDIRECT_URI);
    viewLogin(q.get("error_description") || q.get("error"));
    return true;
  }
  if (!code) return false;
  const verifier = sessionStorage.getItem("pkce_verifier");
  history.replaceState({}, "", REDIRECT_URI);
  if (q.get("state") !== sessionStorage.getItem("pkce_state") || !verifier) { viewLogin("Sign-in session expired, please try again."); return true; }
  try {
    const body = new URLSearchParams({ grant_type:"authorization_code", client_id:CLIENT, code, redirect_uri:REDIRECT_URI, code_verifier:verifier });
    const r = await fetch(TOKEN_URL, { method:"POST", headers:{"Content-Type":"application/x-www-form-urlencoded"}, body });
    if (!r.ok) { viewLogin("Could not complete sign-in."); return true; }
    const j = await r.json();
    tok=j.access_token; refresh=j.refresh_token; expAt=Date.now()+(j.expires_in-30)*1000;
    sessionStorage.removeItem("pkce_verifier"); sessionStorage.removeItem("pkce_state");
    viewApp();
  } catch(e){ viewLogin("Could not reach Keycloak to complete sign-in."); }
  return true;
}

function viewLogin(err) {
  el("who").style.display="none"; el("logout").style.display="none";
  // CONSTANT template — no external data. P1: PKCE authorization-code ONLY — no password field, no password
  // grant. "Sign in" redirects to Keycloak's hosted login (the only place the password is entered).
  main.innerHTML =
    '<div class="card narrow">' +
      '<h2 class="center">Sign in</h2>' +
      '<p class="muted center">Access the OptionsEdge live feed.</p>' +
      '<div style="height:6px"></div>' +
      '<button class="primary" id="login">Sign in with Keycloak</button>' +
      '<div class="msg" id="m"></div>' +
      '<p class="muted center" style="margin-top:18px">No account? <a href="#" id="register">Create one</a></p>' +
    '</div>';
  const m = el("m");
  m.textContent = err || "";                  // external/reflected data — textContent, never HTML
  m.className = "msg" + (err ? " err" : "");
  el("login").onclick = () => startRedirect(AUTH_URL);
  el("register").onclick = e => { e.preventDefault(); startRedirect(REG_URL); };
}

async function ensureToken() {
  if (Date.now() < expAt) return true;
  try {
    const body = new URLSearchParams({ grant_type:"refresh_token", client_id:CLIENT, refresh_token:refresh });
    const r = await fetch(TOKEN_URL, { method:"POST", headers:{"Content-Type":"application/x-www-form-urlencoded"}, body });
    if (!r.ok) return false;
    const j = await r.json(); tok=j.access_token; refresh=j.refresh_token; expAt=Date.now()+(j.expires_in-30)*1000; return true;
  } catch(e){ return false; }
}

function viewApp() {
  const t = parseJwt(tok);
  const roles = (t.realm_access&&t.realm_access.roles||[]).filter(r=>["user","ibkr-user","trader","admin"].includes(r));
  el("who").style.display="";
  el("who").textContent = (t.preferred_username||"user")+" · "+(roles.join(", ")||"user");  // claim — textContent
  el("logout").style.display=""; el("logout").onclick = doLogout;
  const ibkr = roles.includes("ibkr-user");
  // CONSTANT template — no external data. The greeting (which embeds a token claim) is set via textContent.
  main.innerHTML =
    '<div class="card">' +
      '<h2 id="greet"></h2>' +
      '<p class="muted">Authenticated via Keycloak. Choose a feed and enter the live application.</p>' +
      '<div class="controls">' +
        '<div><label>Source</label><select id="src"><option>DATABENTO</option>' + (ibkr?'<option>IBKR</option>':'') + '</select></div>' +
        '<div><label>Symbol</label><input id="sym" value="SPX"></div>' +
        '<div><label>Expiry</label><input id="exp" value="20260617"></div>' +
        '<div><label>Strike from</label><input id="lo" value="0"></div>' +
        '<div><label>Strike to</label><input id="hi" value="1000000"></div>' +
        '<div><button class="primary" id="enter">Enter application →</button></div>' +
      '</div>' +
      '<div class="msg err" id="m2"></div>' +
      '<p class="muted" style="margin-top:14px">Opens the live option chain for your session. ' +
      'Your feed and strike window are isolated from other users.</p>' +
    '</div>';
  // given_name / preferred_username are signed claims but still EXTERNAL data — textContent only.
  el("greet").textContent = "You’re in, " + (t.given_name || t.preferred_username || "trader") + " 👋";
  el("enter").onclick = enterApp;
}

// P0 (real sign-out): a complete logout. Tear down the SERVER session (closes every socket), REVOKE the
// refresh token at Keycloak, wipe ALL stored credentials + selection, then end the Keycloak SSO session.
// Nothing is left that /app.html could load to resume the authenticated application.
async function doLogout() {
  const accessTok = tok, refreshTok = refresh;
  // 1. Server-side AppSession teardown (force-closes the user's WebSockets) — do it while the bearer is valid.
  try { if (accessTok) await fetch("/api/logout", { method:"POST", headers:{ "Authorization":"Bearer "+accessTok } }); } catch(e){}
  // 2. Revoke the refresh token so it can never mint a new access token (kills the offline/SSO session).
  try {
    if (refreshTok) await fetch(REVOKE_URL, { method:"POST", headers:{"Content-Type":"application/x-www-form-urlencoded"},
      body: new URLSearchParams({ client_id:CLIENT, token:refreshTok, token_type_hint:"refresh_token" }) });
  } catch(e){}
  // 3. Drop every in-memory and stored credential + selection, and close any open socket.
  try { if (ws) ws.close(); } catch(e){}
  tok=null; refresh=null; expAt=0;
  SESSION_KEYS.forEach(k => { try { sessionStorage.removeItem(k); } catch(e){} });
  // 4. End the Keycloak browser SSO session (RP-initiated logout), returning to the sign-in page.
  location.assign(LOGOUT_URL + "?" + new URLSearchParams({ client_id: CLIENT, post_logout_redirect_uri: REDIRECT_URI }));
}

async function enterApp() {
  el("m2").textContent = "";
  if (!await ensureToken()) { viewLogin("Session expired, please sign in again."); return; }
  const sel = {
    source: el("src").value, symbol: el("sym").value.trim().toUpperCase(), expiry: el("exp").value.trim(),
    strikeLo: parseFloat(el("lo").value), strikeHi: parseFloat(el("hi").value), maxStrikes: 45
  };
  // Hand the session + selection to the real option-chain UI.
  sessionStorage.setItem("oe_tok", tok);
  sessionStorage.setItem("oe_refresh", refresh);
  sessionStorage.setItem("oe_expAt", String(expAt));
  sessionStorage.setItem("oe_sel", JSON.stringify(sel));
  location.assign("/app.html");
}

// Load the deployment's Keycloak config first, then run the PKCE callback / show the sign-in view.
loadAuthConfig()
  .then(() => handleRedirectCallback().then(handled => { if (!handled) viewLogin(); }))
  .catch(() => { main.textContent = "Sign-in is temporarily unavailable (could not load configuration)."; });
