import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class RiskManager {
    private final TradingDAO dao;
    private final FillReportWriter reportWriter;
    private final AtomicBoolean halted = new AtomicBoolean(false);

    public RiskManager(TradingDAO dao, FillReportWriter reportWriter) {
        this.dao = dao;
        this.reportWriter = reportWriter;
    }

    public double calculateVaRPercent(double potentialPortfolioLoss, double totalPortfolioEquity) {
        if (!Double.isFinite(potentialPortfolioLoss) || potentialPortfolioLoss < 0
                || !Double.isFinite(totalPortfolioEquity) || totalPortfolioEquity <= 0) {
            throw new IllegalArgumentException("Loss must be >= 0 and equity must be > 0.");
        }
        return potentialPortfolioLoss / totalPortfolioEquity * 100.0;
    }

    public synchronized boolean assess(double potentialLoss, double equity, double latencyMs,
                                       double maximumLatencyMs)
            throws SQLException, IOException {
        double varPercent = calculateVaRPercent(potentialLoss, equity);
        String reason = findBreachReason(varPercent, latencyMs, maximumLatencyMs);
        if (reason != null) {
            triggerHalt(varPercent, latencyMs, reason);
            return false;
        }
        return !halted.get();
    }

    static String findBreachReason(double varPercent, double latencyMs,
                                   double maximumLatencyMs) {
        String reason = null;
        if (varPercent > 5.0) {
            reason = String.format("VaR %.2f%% exceeds 5.00%%", varPercent);
        }
        if (latencyMs > maximumLatencyMs) {
            String latencyReason = String.format("Latency %.3f ms exceeds %.3f ms",
                    latencyMs, maximumLatencyMs);
            reason = reason == null ? latencyReason : reason + "; " + latencyReason;
        }
        return reason;
    }

    public synchronized void triggerHalt(double varPercent, double latencyMs, String reason)
            throws SQLException, IOException {
        if (halted.compareAndSet(false, true)) {
            List<String> cancelled = dao.cancelPendingOrders();
            dao.recordRiskBreach(varPercent, latencyMs, reason);
            reportWriter.appendCircuitBreakerLog(varPercent, latencyMs, reason, cancelled.size());
            System.err.println("CIRCUIT_BREAKER_HALT: " + reason
                    + ". Cancelled pending orders: " + cancelled.size());
        }
    }

    public synchronized boolean executeIfActive(Order order, double filledPrice, long slippageTicks,
                                                double commission, double qualityScore,
                                                String qualityGrade, Instant executedAt,
                                                String csvRow) throws SQLException, IOException {
        if (halted.get()) {
            return false;
        }
        dao.saveExecution(order, filledPrice, slippageTicks, commission, qualityScore,
                qualityGrade, executedAt);
        reportWriter.appendFill(csvRow);
        return true;
    }

    public boolean isHalted() {
        return halted.get();
    }
}
