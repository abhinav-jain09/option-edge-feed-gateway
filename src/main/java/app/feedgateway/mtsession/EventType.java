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
    DIRECTIONAL_PRESSURE(Scope.CONTRACT),
    STRIKE_FLOW(Scope.CONTRACT),
    VOLUME_SANDWICH(Scope.CONTRACT),
    GEX_BY_STRIKE(Scope.CONTRACT),
    // Max pain is a per-(symbol,expiry) aggregate (one value covers the whole chain), so it routes
    // CONTRACT-scoped by source|symbol|expiry with NO strike filter — every session on that chain receives it.
    MAX_PAIN(Scope.CONTRACT),
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
