public final class StrategyEvaluator {
    private static final double DEFAULT_LATENCY_WEIGHT = 0.40;
    private static final double DEFAULT_SLIPPAGE_WEIGHT = 0.40;
    private static final double DEFAULT_SPREAD_WEIGHT = 0.20;

    public enum Strategy {
        MARKET_MAKING(5.0),
        STATISTICAL_ARBITRAGE(2.0),
        HIGH_VOLATILITY_MOMENTUM(10.0);

        private final double maximumLatencyMs;

        Strategy(double maximumLatencyMs) {
            this.maximumLatencyMs = maximumLatencyMs;
        }

        public double getMaximumLatencyMs() { return maximumLatencyMs; }
    }

    public static final class Evaluation {
        private final double score;
        private final String grade;

        Evaluation(double score, String grade) {
            this.score = score;
            this.grade = grade;
        }

        public double getScore() { return score; }
        public String getGrade() { return grade; }
    }

    private StrategyEvaluator() {
    }

    public static double evaluateStrategy(Strategy strategy, MarketTelemetry telemetry,
                                          long slippageTicks, double commission,
                                          int quantity) {
        double latency = normalized(100.0 * (1.0 - telemetry.getLatencyMs()
                / strategy.getMaximumLatencyMs()));
        double slippage = normalized(100.0 * (1.0 - slippageTicks / 10.0));
        double spread = normalized(100.0 * (1.0 - telemetry.getSpread()
                / telemetry.getAskPrice()));
        double divergence = normalized(Math.abs(telemetry.getPriceDivergenceBps()) * 10.0);
        double momentum = normalized(Math.abs(telemetry.getMomentumBps()) * 10.0);
        double inventoryRisk = normalized(100.0
                * (1.0 - telemetry.getInventoryUnits() / 1000.0));
        double notional = Math.max(telemetry.getBidPrice() * quantity, 0.01);
        double commissionScore = normalized(100.0 - commission / notional * 100_000.0);

        switch (strategy) {
            case MARKET_MAKING:
                return 0.40 * spread + 0.40 * latency + 0.20 * inventoryRisk;
            case STATISTICAL_ARBITRAGE:
                return 0.50 * divergence + 0.30 * latency + 0.20 * slippage;
            case HIGH_VOLATILITY_MOMENTUM:
                return 0.40 * momentum + 0.40 * slippage + 0.20 * commissionScore;
            default:
                throw new IllegalStateException("Unsupported strategy: " + strategy);
        }
    }

    public static Evaluation executionQuality(double latencyMs, long slippageTicks,
                                              double spread, double askPrice,
                                              boolean circuitBreakerActive) {
        if (!Double.isFinite(latencyMs) || latencyMs < 0 || slippageTicks < 0
                || !Double.isFinite(spread) || spread < 0
                || !Double.isFinite(askPrice) || askPrice <= 0) {
            throw new IllegalArgumentException("Quality inputs must be finite and non-negative; "
                    + "ask price must be positive.");
        }
        if (circuitBreakerActive) {
            return new Evaluation(0, "Trade Rejected");
        }
        double latencyScore = normalized(100.0 * (1.0 - latencyMs / 10.0));
        double slippageScore = normalized(100.0 * (1.0 - slippageTicks / 10.0));
        double spreadScore = askPrice <= 0 ? 0
                : normalized(100.0 * (1.0 - spread / askPrice));
        double[] weights = qualityWeights();
        double score = weights[0] * latencyScore
                + weights[1] * slippageScore + weights[2] * spreadScore;
        String grade;
        if (score >= 90) {
            grade = "Alpha Execution";
        } else if (score >= 75) {
            grade = "Optimal Fill";
        } else if (score >= 60) {
            grade = "High Slippage";
        } else {
            grade = "Order Cancelled / Halted";
        }
        return new Evaluation(score, grade);
    }

    private static double normalized(double score) {
        return Math.max(0, Math.min(100, score));
    }

    private static double[] qualityWeights() {
        double latency = readWeight("TRADING_QUALITY_LATENCY_WEIGHT",
                DEFAULT_LATENCY_WEIGHT);
        double slippage = readWeight("TRADING_QUALITY_SLIPPAGE_WEIGHT",
                DEFAULT_SLIPPAGE_WEIGHT);
        double spread = readWeight("TRADING_QUALITY_SPREAD_WEIGHT",
                DEFAULT_SPREAD_WEIGHT);
        if (Math.abs(latency + slippage + spread - 1.0) > 0.000001) {
            throw new IllegalArgumentException("Execution quality weights must sum to 1.0.");
        }
        return new double[] {latency, slippage, spread};
    }

    private static double readWeight(String name, double defaultValue) {
        String configured = System.getenv(name);
        if (configured == null || configured.isBlank()) {
            return defaultValue;
        }
        try {
            double weight = Double.parseDouble(configured);
            if (!Double.isFinite(weight) || weight < 0 || weight > 1) {
                throw new IllegalArgumentException(name + " must be between 0 and 1.");
            }
            return weight;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be a decimal between 0 and 1.",
                    exception);
        }
    }
}
