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
    VIX_PRICE(Scope.UNDERLYING),
    INDEX_PRICE(Scope.UNDERLYING),
    SPX_PRICE(Scope.UNDERLYING);

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
            case INDEX_PRICE, SPX_PRICE -> "SPX";
            default -> throw new IllegalStateException("Not an underlying event: " + this);
        };
    }
}
