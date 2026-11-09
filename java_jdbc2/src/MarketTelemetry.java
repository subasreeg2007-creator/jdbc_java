public final class MarketTelemetry {
    private final String ticker;
    private final double bidPrice;
    private final double askPrice;
    private final double latencyMs;
    private final int marketDepthLots;
    private final double priceDivergenceBps;
    private final double momentumBps;
    private final int inventoryUnits;

    public MarketTelemetry(String ticker, double bidPrice, double askPrice, double latencyMs,
                           int marketDepthLots, double priceDivergenceBps,
                           double momentumBps, int inventoryUnits) {
        if (ticker == null || !ticker.matches("[A-Z]{1,5}")) {
            throw new IllegalArgumentException("Ticker must match [A-Z]{1,5}.");
        }
        if (!Double.isFinite(bidPrice) || !Double.isFinite(askPrice)
                || bidPrice <= 0 || askPrice < bidPrice) {
            throw new IllegalArgumentException("Prices must be positive and ask must be >= bid.");
        }
        if (!Double.isFinite(latencyMs) || latencyMs < 0) {
            throw new IllegalArgumentException("Latency must be a non-negative number.");
        }
        if (marketDepthLots < 0 || !Double.isFinite(priceDivergenceBps)
                || !Double.isFinite(momentumBps) || inventoryUnits < 0) {
            throw new IllegalArgumentException("Depth, divergence, momentum or inventory is invalid.");
        }
        this.ticker = ticker;
        this.bidPrice = bidPrice;
        this.askPrice = askPrice;
        this.latencyMs = latencyMs;
        this.marketDepthLots = marketDepthLots;
        this.priceDivergenceBps = priceDivergenceBps;
        this.momentumBps = momentumBps;
        this.inventoryUnits = inventoryUnits;
    }

    public String getTicker() { return ticker; }
    public double getBidPrice() { return bidPrice; }
    public double getAskPrice() { return askPrice; }
    public double getSpread() { return askPrice - bidPrice; }
    public double getLatencyMs() { return latencyMs; }
    public int getMarketDepthLots() { return marketDepthLots; }
    public double getPriceDivergenceBps() { return priceDivergenceBps; }
    public double getMomentumBps() { return momentumBps; }
    public int getInventoryUnits() { return inventoryUnits; }
}
