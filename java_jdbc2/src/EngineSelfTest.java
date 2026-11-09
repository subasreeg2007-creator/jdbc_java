import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class EngineSelfTest {
    private EngineSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        testOrderValidationAndSides();
        testStrategyAndQualityScores();
        testRiskCalculations();
        testFixParsing();
        testCsvGeneration();
        testConcurrentTasks();
        System.out.println("SELF_TEST_OK: all database-independent checks passed.");
    }

    private static void testOrderValidationAndSides() {
        Order buy = new Order("TEST-BUY", "  student  ", "AAPL",
                Order.Side.BUY, 190.05, 10);
        Order sell = new Order("TEST-SELL", "student", "MSFT",
                Order.Side.SELL, 410.00, 5);
        check("student".equals(buy.getTraderId()), "Trader ID was not trimmed.");
        check(buy.getSide() == Order.Side.BUY && sell.getSide() == Order.Side.SELL,
                "BUY/SELL order validation failed.");
        check(ExecutionWorker.isMarketable(Order.Side.BUY, 190.05, 190.02),
                "Marketable BUY check failed.");
        check(ExecutionWorker.isMarketable(Order.Side.SELL, 410.00, 410.00),
                "Marketable SELL check failed.");
        check(!ExecutionWorker.isMarketable(Order.Side.BUY, 190.00, 190.02),
                "Non-marketable BUY check failed.");
        expectIllegalArgument(() -> new Order("BAD", "student", "aapl",
                Order.Side.BUY, 10, 1), "Lowercase ticker was accepted.");
        expectIllegalArgument(() -> new Order("BAD", "student", "TOOLONG",
                Order.Side.SELL, 10, 1), "Long ticker was accepted.");
    }

    private static void testStrategyAndQualityScores() {
        MarketTelemetry telemetry = new MarketTelemetry(
                "AAPL", 190.00, 190.02, 1.2, 25, 2.5, 1.5, 20);
        for (StrategyEvaluator.Strategy strategy : StrategyEvaluator.Strategy.values()) {
            double score = StrategyEvaluator.evaluateStrategy(strategy, telemetry, 1, 1.9, 10);
            check(score >= 0 && score <= 100, strategy + " score was outside 0-100.");
        }
        StrategyEvaluator.Evaluation alpha =
                StrategyEvaluator.executionQuality(0, 0, 0, 100, false);
        check(alpha.getScore() == 100 && "Alpha Execution".equals(alpha.getGrade()),
                "Alpha score grading failed.");
        StrategyEvaluator.Evaluation low =
                StrategyEvaluator.executionQuality(10, 10, 100, 100, false);
        check(low.getScore() < 60 && "Order Cancelled / Halted".equals(low.getGrade()),
                "Low score grading failed.");
        check("Trade Rejected".equals(StrategyEvaluator.executionQuality(
                1, 1, 0.01, 100, true).getGrade()), "Circuit-breaker grade failed.");
    }

    private static void testRiskCalculations() {
        RiskManager riskManager = new RiskManager(new TradingDAO(), new FillReportWriter());
        check(Math.abs(riskManager.calculateVaRPercent(4_000, 100_000) - 4.0) < 0.000001,
                "VaR calculation failed.");
        check(RiskManager.findBreachReason(6.0, 1.0, 5.0).contains("VaR"),
                "VaR circuit-breaker condition failed.");
        check(RiskManager.findBreachReason(4.0, 5.1, 5.0).contains("Latency"),
                "Latency circuit-breaker condition failed.");
        check(RiskManager.findBreachReason(5.0, 5.0, 5.0) == null,
                "Threshold equality must not count as a breach.");
    }

    private static void testFixParsing() {
        Map<String, String> tags = MainApp.parseFixMessage("35=D|55=AAPL|54=1|38=100");
        check("AAPL".equals(tags.get("55")) && "1".equals(tags.get("54")),
                "FIX tag parsing failed.");
    }

    private static void testCsvGeneration() throws IOException {
        Path directory = Files.createTempDirectory("trading-engine-self-test-");
        try {
            FillReportWriter writer = new FillReportWriter(directory);
            writer.appendFill("TEST-1,student,AAPL,BUY,190.0200,10,1,1.9002,95.00,Alpha Execution,2025-01-15T12:00:00Z");
            writer.appendCircuitBreakerLog(6.0, 1.0, "VaR breach, simulated", 2);
            List<String> fills = Files.readAllLines(directory.resolve("execution_fills.csv"));
            List<String> breaks =
                    Files.readAllLines(directory.resolve("circuit_breaker_logs.csv"));
            check(fills.size() == 2 && fills.get(0).startsWith("order_id,"),
                    "Fill CSV generation failed.");
            check(breaks.size() == 2 && breaks.get(1).contains("\"VaR breach, simulated\""),
                    "Circuit-breaker CSV generation/escaping failed.");
        } finally {
            Files.deleteIfExists(directory.resolve("execution_fills.csv"));
            Files.deleteIfExists(directory.resolve("circuit_breaker_logs.csv"));
            Files.deleteIfExists(directory);
        }
    }

    private static void testConcurrentTasks()
            throws InterruptedException, ExecutionException {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return Thread.currentThread().getName();
                }));
            }
            start.countDown();
            Set<String> threadNames = new HashSet<>();
            for (Future<String> future : futures) {
                threadNames.add(future.get());
            }
            check(threadNames.size() > 1, "Executor did not use concurrent worker threads.");
        } finally {
            executor.shutdownNow();
        }
    }

    private static void expectIllegalArgument(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
