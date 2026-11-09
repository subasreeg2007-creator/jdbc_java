import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class ExecutionWorker implements Runnable {
    private static final double TICK_SIZE = 0.01;
    private static final double COMMISSION_RATE = 0.001;

    private final Order order;
    private final StrategyEvaluator.Strategy strategy;
    private final TradingDAO dao;
    private final RiskManager riskManager;
    private final FillReportWriter reportWriter;
    private final double potentialLoss;
    private final double portfolioEquity;
    private final FutureTask<String> result = new FutureTask<>(this::process);

    public ExecutionWorker(Order order, StrategyEvaluator.Strategy strategy, TradingDAO dao,
                           RiskManager riskManager, FillReportWriter reportWriter,
                           double potentialLoss, double portfolioEquity) {
        this.order = order;
        this.strategy = strategy;
        this.dao = dao;
        this.riskManager = riskManager;
        this.reportWriter = reportWriter;
        this.potentialLoss = potentialLoss;
        this.portfolioEquity = portfolioEquity;
    }

    @Override
    public void run() {
        result.run();
    }

    public String getResult() throws InterruptedException, ExecutionException {
        return result.get();
    }

    private String process() throws SQLException, IOException {
        long startNanos = System.nanoTime();
        String threadName = Thread.currentThread().getName();
        if (riskManager.isHalted()) {
            return order.getOrderId() + " rejected: circuit breaker is active.";
        }

        MarketTelemetry telemetry = dao.findLatestTelemetry(order.getTicker());
        if (telemetry == null) {
            return order.getOrderId() + " not executed: no telemetry for " + order.getTicker();
        }
        if (!riskManager.assess(potentialLoss, portfolioEquity, telemetry.getLatencyMs(),
                strategy.getMaximumLatencyMs())) {
            return order.getOrderId() + " rejected: risk check failed.";
        }

        double filledPrice = order.getSide() == Order.Side.BUY
                ? telemetry.getAskPrice() : telemetry.getBidPrice();
        if (!isMarketable(order.getSide(), order.getLimitPrice(), filledPrice)) {
            return order.getOrderId() + " remains pending: limit price is not marketable.";
        }

        double midpoint = (telemetry.getBidPrice() + telemetry.getAskPrice()) / 2.0;
        long slippageTicks = Math.round(Math.abs(filledPrice - midpoint) / TICK_SIZE);
        double commission = filledPrice * order.getQuantity() * COMMISSION_RATE;
        double strategyScore = StrategyEvaluator.evaluateStrategy(
                strategy, telemetry, slippageTicks, commission, order.getQuantity());
        StrategyEvaluator.Evaluation quality = StrategyEvaluator.executionQuality(
                telemetry.getLatencyMs(), slippageTicks, telemetry.getSpread(),
                telemetry.getAskPrice(), riskManager.isHalted());
        if (quality.getScore() < 60) {
            dao.markCancelled(order.getOrderId());
            return order.getOrderId() + " cancelled: execution quality below 60.";
        }
        Instant executedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String csvRow = String.format("\"%s\",\"%s\",\"%s\",\"%s\",%.4f,%d,%d,%.4f,"
                        + "%.2f,\"%s\",\"%s\"",
                csvField(order.getOrderId()), csvField(order.getTraderId()),
                csvField(order.getTicker()), csvField(order.getSide().name()),
                filledPrice, order.getQuantity(), slippageTicks, commission,
                quality.getScore(), csvField(quality.getGrade()), executedAt);
        if (!riskManager.executeIfActive(order, filledPrice, slippageTicks, commission,
                quality.getScore(), quality.getGrade(), executedAt, csvRow)) {
            return order.getOrderId() + " rejected: circuit breaker became active.";
        }

        long elapsedMicros = (System.nanoTime() - startNanos) / 1_000;
        return String.format("%s filled at %.4f | execution quality %.1f (%s) | "
                        + "%s strategy score %.1f | thread %s | elapsed %d microseconds",
                order.getOrderId(), filledPrice, quality.getScore(), quality.getGrade(),
                strategy, strategyScore, threadName, elapsedMicros);
    }

    private String csvField(String value) {
        return value.replace("\"", "\"\"");
    }

    static boolean isMarketable(Order.Side side, double limitPrice, double marketPrice) {
        if (side == null || !Double.isFinite(limitPrice) || !Double.isFinite(marketPrice)
                || limitPrice <= 0 || marketPrice <= 0) {
            throw new IllegalArgumentException("Side and prices must be valid.");
        }
        return side == Order.Side.BUY ? limitPrice >= marketPrice : limitPrice <= marketPrice;
    }
}
