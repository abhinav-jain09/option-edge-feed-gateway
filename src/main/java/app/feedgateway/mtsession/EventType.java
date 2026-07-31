package app.feedgateway.mtsession;

/**
 * Classification of a market-data event as it flows through the gateway (OE-DDD-001 §8.6).
 *
 * <p>Contract-scoped events route by {@code source|symbol|expiry} and are strike-filtered per
 * user; underlying events route by {@code source|underlying} and bypass the symbol/expiry/strike
 * match (they are fanned to every session of that source whose symbol resolves to the underlying).
 */
public enum EventType {
    SNAPSHOT(Scope.CONTRACT),
    PACE(Scope.CONTRACT),
    // Board-level pace ranking — one compact record per (symbol,expiry) chain, like MAX_PAIN: routes
    // CONTRACT-scoped by source|symbol|expiry with NO strike filter (every session on the chain gets it).
    PACE_RANK(Scope.CONTRACT),
    DIRECTIONAL_PRESSURE(Scope.CONTRACT),
    STRIKE_FLOW(Scope.CONTRACT),
    // Per-strike delta-flow signal — one record per (symbol, expiry, strike), so it routes
    // CONTRACT-scoped by source|symbol|expiry and is strike-filtered per user (like GEX_BY_STRIKE).
    DELTA_FLOW(Scope.CONTRACT),
    // Per-strike strike-intelligence signal — one record per (symbol, expiry, strike), so it routes
    // CONTRACT-scoped by source|symbol|expiry and is strike-filtered per user (like DELTA_FLOW).
    STRIKE_INTEL(Scope.CONTRACT),
    // Per-strike call+put price-reconciliation reading. Both legs travel in one record and are
    // evaluated together by the UI; strike-filtered exactly like STRIKE_INTEL.
    OPTION_TRUTH(Scope.CONTRACT),
    // Per-strike strike-invasion signal — one record per (symbol, strike, direction); contract v2 emits
    // a live UP and DOWN record per strike concurrently. SPX-only, so it carries NO expiry; routes
    // CONTRACT-scoped by source|symbol|expiry (with a blank expiry) and is strike-filtered per user
    // (like STRIKE_INTEL). Direction never affects routing — it only disambiguates the gateway cache key.
    STRIKE_INVASION(Scope.CONTRACT),
    MISSION_PACE(Scope.CONTRACT),
    MISSION_CONTROL(Scope.CONTRACT),
    // Whole-underlying spread-skew snapshot — ONE record per underlying (SPX-only, single-value
    // last-snapshot-wins cache). The payload names its market `underlying` (mapped to symbol in
    // GatewayRecordMapper; there is NO symbol field) with a NULLABLE expiry; routes CONTRACT-scoped
    // by source|symbol|expiry with NO strike filter (like MISSION_CONTROL).
    SPREAD_SKEW(Scope.CONTRACT),
    VOLUME_SANDWICH(Scope.CONTRACT),
    MISSION_SANDWICH(Scope.CONTRACT),
    GEX_BY_STRIKE(Scope.CONTRACT),
    // Per-strike OI-arrival status from the gex service's watchdog (OI_MISSING/OI_OK). Routes exactly like
    // GEX_BY_STRIKE (CONTRACT scope, per-strike), so the option-chain UI can badge each strike.
    GEX_OI_STATUS(Scope.CONTRACT),
    STRIKE_SR(Scope.CONTRACT),
    // Gamma migration: one record per (symbol,expiry) describing how the whole chain's gamma is
    // MOVING, so it routes CONTRACT-scoped with no strike filter (like MAX_PAIN). It names a hot
    // strike inside the payload, but the record is about the chain, not that strike — filtering it
    // per-strike would deliver it only to sessions already watching the strike that just heated up,
    // which is precisely the session that does not need telling.
    GAMMA_MIGRATION(Scope.CONTRACT),
    // Max pain is a per-(symbol,expiry) aggregate (one value covers the whole chain), so it routes
    // CONTRACT-scoped by source|symbol|expiry with NO strike filter — every session on that chain receives it.
    MAX_PAIN(Scope.CONTRACT),
    // ES-on-SPX aligned GEX — one whole-book record per (symbol=SPX,expiry) covering every mapped strike,
    // so it routes CONTRACT-scoped by source|symbol|expiry with NO strike filter (like MAX_PAIN).
    ES_GEX(Scope.CONTRACT),
    // ES strike-intelligence projected onto SPX strikes — per-strike (symbol=SPX,expiry,strike=spxStrike),
    // routed CONTRACT-scoped by source|symbol|expiry and strike-filtered per user (like STRIKE_INTEL). A
    // separate ES-origin overlay layer, distinct from the native SPX STRIKE_INTEL.
    ES_STRIKE_INTEL(Scope.CONTRACT),
    // Strike-liquidity heatmap column frame — one per-second record per (symbol,expiry) chain
    // covering every strike, so it routes CONTRACT-scoped with NO strike filter (like MAX_PAIN).
    LIQUIDITY_HEATMAP(Scope.CONTRACT),
    // Option Price Behavior is a per-(symbol,tradingDate) dashboard aggregate. Route it like a whole-chain
    // contract-scoped event, using tradingDate as the expiry/date key.
    OPTION_PRICE_BEHAVIOR(Scope.CONTRACT),
    // Dealer-ledger signal — the gateway JOINS dealer-ledger-profile + -state into one per-(symbol,expiry)
    // envelope, so it routes CONTRACT-scoped by source|symbol|expiry with NO strike filter (like MAX_PAIN).
    DEALER_LEDGER(Scope.CONTRACT),
    OPB_BY_OPTION(Scope.CONTRACT),
    OPB_SESSION(Scope.CONTRACT),
    VIX_PRICE(Scope.UNDERLYING),
    INDEX_PRICE(Scope.UNDERLYING),
    SPX_PRICE(Scope.UNDERLYING),

    // HPSF decision signals carry no per-strike routing key — a signal/audit/exit-intent/top-candidates
    // set is computed for a whole (symbol, expiry) chain, so they route contract-scoped by source|symbol|
    // expiry with NO strike filter (every session on that chain receives them, review P0 — HPSF bypass).
    // HPSF market-flow is a whole-underlying summary with no expiry, so it routes underlying-scoped.
    HPSF_LATEST_SIGNAL(Scope.CONTRACT),
    HPSF_TOP_CANDIDATES(Scope.CONTRACT),
    HPSF_AUDIT(Scope.CONTRACT),
    HPSF_EXIT_INTENT(Scope.CONTRACT),
    HPSF_MARKET_FLOW(Scope.UNDERLYING);

    private enum Scope { CONTRACT, UNDERLYING }

    private final Scope scope;

    EventType(Scope scope) {
        this.scope = scope;
    }

    public boolean isContractScoped() {
        return scope == Scope.CONTRACT;
    }

    public boolean isUnderlying() {
        return scope == Scope.UNDERLYING;
    }

    /**
     * The underlying instrument an underlying-scoped event pertains to. ES/index and SPX-price map
     * to {@code SPX}; the VIX feed maps to {@code VIX}.
     *
     * @throws IllegalStateException if called on a contract-scoped event.
     */
    public String underlyingSymbol() {
        return switch (this) {
            case VIX_PRICE -> "VIX";
            case INDEX_PRICE, SPX_PRICE, HPSF_MARKET_FLOW -> "SPX";
            default -> throw new IllegalStateException("Not an underlying event: " + this);
        };
    }
}
