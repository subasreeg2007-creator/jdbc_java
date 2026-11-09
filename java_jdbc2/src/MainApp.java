import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainApp {
    private static final Path ORDER_STREAM = Path.of("input", "order_stream.txt");
    private final Scanner scanner = new Scanner(System.in);
    private final TradingDAO dao = new TradingDAO();
    private final FillReportWriter reportWriter = new FillReportWriter();
    private final RiskManager riskManager = new RiskManager(dao, reportWriter);
    private StrategyEvaluator.Strategy selectedStrategy =
            StrategyEvaluator.Strategy.MARKET_MAKING;
    private double potentialLoss;
    private double portfolioEquity = 100_000.0;

    public static void main(String[] args) {
        new MainApp().run();
    }

    private void run() {
        demonstrateFixParsing();
        boolean running = true;
        while (running) {
            printMenu();
            String choice = prompt("Select an option: ");
            try {
                switch (choice) {
                    case "1": addNewOrder(); break;
                    case "2": viewAllOrders(); break;
                    case "3": enterMarketTelemetry(); break;
                    case "4": executeOrders(); break;
                    case "5": selectStrategy(); break;
                    case "6": calculateExecutionQuality(); break;
                    case "7": checkPortfolioVaR(); break;
                    case "8": viewCircuitBreakerStatus(); break;
                    case "9": viewExecutionHistory(); break;
                    case "10": generateFillReport(); break;
                    case "11": readOrderStreamFile(); break;
                    case "12":
                        running = false;
                        System.out.println("Trading engine stopped.");
                        break;
                    default:
                        System.out.println("Enter a menu option from 1 to 12.");
                }
            } catch (IllegalArgumentException exception) {
                System.err.println("Invalid input: " + exception.getMessage());
            } catch (SQLException | IOException exception) {
                System.err.println("Operation failed: " + exception.getMessage());
            }
        }
    }

    private void printMenu() {
        System.out.println();
        System.out.println("========== HIGH FREQUENCY TRADING ENGINE ==========");
        System.out.println("1. Add New Order");
        System.out.println("2. View All Orders");
        System.out.println("3. Enter Market Telemetry");
        System.out.println("4. Execute Orders");
        System.out.println("5. Select Trading Strategy");
        System.out.println("6. Calculate Execution Quality Score");
        System.out.println("7. Check Portfolio VaR");
        System.out.println("8. View Circuit Breaker Status");
        System.out.println("9. View Execution History");
        System.out.println("10. Generate Fill Report");
        System.out.println("11. Read Order Stream File");
        System.out.println("12. Exit");
        System.out.println("===================================================");
    }

    private void addNewOrder() throws SQLException {
        if (riskManager.isHalted()) {
            System.out.println("Order rejected: the circuit breaker is active.");
            return;
        }
        String orderId = prompt("Order ID (leave blank to generate one): ").trim();
        if (orderId.isEmpty()) {
            orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8);
        }
        String traderId = prompt("Trader ID: ").trim();
        String ticker = prompt("Ticker (A-Z, 1-5 letters): ").trim();
        Order.Side side = Order.Side.valueOf(prompt("Side (BUY/SELL): ").trim().toUpperCase());
        double limitPrice = readDouble("Limit price: ");
        int quantity = readInt("Quantity: ");
        Order order = new Order(orderId, traderId, ticker, side, limitPrice, quantity);
        dao.addOrder(order);
        System.out.println("Order accepted and saved as PENDING: " + order.getOrderId());
    }

    private void viewAllOrders() throws SQLException {
        printRows("Orders", dao.findAllOrders());
    }

    private void enterMarketTelemetry() throws SQLException {
        MarketTelemetry telemetry = promptTelemetry();
        dao.saveTelemetry(telemetry);
        System.out.println("Telemetry saved for " + telemetry.getTicker()
                + " (spread " + String.format("%.4f", telemetry.getSpread()) + ").");
    }

    private MarketTelemetry promptTelemetry() {
        String ticker = prompt("Ticker: ").trim();
        double bid = readDouble("Bid price: ");
        double ask = readDouble("Ask price: ");
        double latency = readDouble("Network latency (ms): ");
        int depth = readInt("Market depth (lots): ");
        double divergence = readDouble("Price divergence (basis points, e.g. 2.5): ");
        double momentum = readDouble("Momentum vector (basis points, e.g. 1.5): ");
        int inventory = readInt("Current inventory units: ");
        return new MarketTelemetry(ticker, bid, ask, latency, depth, divergence, momentum, inventory);
    }

    private void executeOrders() throws SQLException, IOException {
        if (riskManager.isHalted()) {
            System.out.println("Execution blocked: circuit breaker is active.");
            return;
        }
        List<Order> orders = dao.findPendingOrders();
        if (orders.isEmpty()) {
            System.out.println("There are no pending orders.");
            return;
        }
        int threadCount = Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors()));
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            List<ExecutionWorker> workers = new ArrayList<>();
            for (Order order : orders) {
                ExecutionWorker worker = new ExecutionWorker(
                        order, selectedStrategy, dao, riskManager, reportWriter,
                        potentialLoss, portfolioEquity);
                workers.add(worker);
                executor.execute(worker);
            }
            for (ExecutionWorker worker : workers) {
                try {
                    System.out.println(worker.getResult());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Order execution was interrupted.", exception);
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    if (cause instanceof SQLException) {
                        throw (SQLException) cause;
                    }
                    if (cause instanceof IOException) {
                        throw (IOException) cause;
                    }
                    throw new IOException("An order worker failed.", cause);
                }
            }
        } finally {
            executor.shutdown();
        }
    }

    private void selectStrategy() {
        System.out.println("1. MARKET MAKING (maximum latency 5 ms)");
        System.out.println("2. STATISTICAL ARBITRAGE (maximum latency 2 ms)");
        System.out.println("3. HIGH-VOLATILITY MOMENTUM (maximum latency 10 ms)");
        switch (prompt("Select strategy: ").trim()) {
            case "1":
                selectedStrategy = StrategyEvaluator.Strategy.MARKET_MAKING;
                break;
            case "2":
                selectedStrategy = StrategyEvaluator.Strategy.STATISTICAL_ARBITRAGE;
                break;
            case "3":
                selectedStrategy = StrategyEvaluator.Strategy.HIGH_VOLATILITY_MOMENTUM;
                break;
            default:
                throw new IllegalArgumentException("Select strategy 1, 2 or 3.");
        }
        System.out.println("Selected strategy: " + selectedStrategy);
    }

    private void calculateExecutionQuality() {
        double latency = readDouble("Measured latency (ms): ");
        long slippageTicks = readLong("Execution slippage (ticks): ");
        double spread = readDouble("Bid/ask spread: ");
        double ask = readDouble("Ask price: ");
        StrategyEvaluator.Evaluation evaluation = StrategyEvaluator.executionQuality(
                latency, slippageTicks, spread, ask, riskManager.isHalted());
        System.out.printf("Execution quality: %.2f / 100 — %s%n",
                evaluation.getScore(), evaluation.getGrade());
    }

    private void checkPortfolioVaR() throws SQLException, IOException {
        potentialLoss = readDouble("Potential portfolio loss: ");
        portfolioEquity = readDouble("Total portfolio equity: ");
        double varPercent = riskManager.calculateVaRPercent(potentialLoss, portfolioEquity);
        boolean allowed = riskManager.assess(potentialLoss, portfolioEquity, 0,
                selectedStrategy.getMaximumLatencyMs());
        System.out.printf("VaR = %.2f%% (limit 5.00%%).%n", varPercent);
        if (!allowed) {
            System.out.println("Account halted: pending orders were cancelled and the breach was logged.");
        } else {
            System.out.println("VaR check passed.");
        }
    }

    private void viewCircuitBreakerStatus() {
        System.out.println("Circuit breaker: "
                + (riskManager.isHalted() ? "CIRCUIT_BREAKER_HALT (active)" : "inactive"));
    }

    private void viewExecutionHistory() throws SQLException {
        printRows("Execution history", dao.findExecutionHistory());
    }

    private void generateFillReport() throws SQLException, IOException {
        List<String> rows = dao.findAllExecutionCsvRows();
        reportWriter.writeAllFills(rows);
        System.out.println("Wrote " + rows.size() + " execution row(s) to "
                + "output/execution_fills.csv.");
    }

    private void readOrderStreamFile() throws IOException, SQLException {
        if (riskManager.isHalted()) {
            System.out.println("Order stream not imported: the circuit breaker is active.");
            return;
        }
        if (!Files.isRegularFile(ORDER_STREAM)) {
            throw new IOException("Input file not found: " + ORDER_STREAM.toAbsolutePath());
        }
        int accepted = 0;
        int rejected = 0;
        try (BufferedReader reader = Files.newBufferedReader(ORDER_STREAM, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank() || line.trim().startsWith("#")) {
                    continue;
                }
                try {
                    String[] fields = line.split(",", -1);
                    if (fields.length != 13) {
                        throw new IllegalArgumentException(
                                "Expected 13 comma-separated fields, found " + fields.length);
                    }
                    Order order = new Order(fields[0].trim(), fields[1].trim(), fields[2].trim(),
                            Order.Side.valueOf(fields[3].trim().toUpperCase()),
                            Double.parseDouble(fields[4].trim()),
                            Integer.parseInt(fields[5].trim()));
                    MarketTelemetry telemetry = new MarketTelemetry(fields[2].trim(),
                            Double.parseDouble(fields[6].trim()),
                            Double.parseDouble(fields[7].trim()),
                            Double.parseDouble(fields[8].trim()),
                            Integer.parseInt(fields[9].trim()),
                            Double.parseDouble(fields[10].trim()),
                            Double.parseDouble(fields[11].trim()),
                            Integer.parseInt(fields[12].trim()));
                    dao.addOrder(order);
                    dao.saveTelemetry(telemetry);
                    accepted++;
                } catch (IllegalArgumentException | SQLException exception) {
                    rejected++;
                    System.err.println("Line " + lineNumber + " rejected: " + exception.getMessage());
                }
            }
        }
        System.out.printf("Order stream complete: %d accepted, %d rejected.%n", accepted, rejected);
    }

    private void demonstrateFixParsing() {
        String sample = "35=D|55=AAPL|54=1|38=100";
        Map<String, String> tags = parseFixMessage(sample);
        System.out.println("FIX sample parsed: 55=" + tags.get("55")
                + ", 54=" + tags.get("54") + " (35=D|55=AAPL|54=1|38=100)");
    }

    static Map<String, String> parseFixMessage(String message) {
        Map<String, String> tags = new HashMap<>();
        for (String field : message.split("\\|")) {
            int equalsIndex = field.indexOf('=');
            if (equalsIndex <= 0 || equalsIndex == field.length() - 1) {
                throw new IllegalArgumentException("Malformed FIX tag: " + field);
            }
            String[] parts = field.split("=");
            if (parts.length != 2 || parts[0].trim().isEmpty()) {
                throw new IllegalArgumentException("Malformed FIX tag: " + field);
            }
            tags.put(parts[0], parts[1]);
        }
        return tags;
    }

    private String prompt(String label) {
        System.out.print(label);
        return scanner.nextLine();
    }

    private double readDouble(String label) {
        try {
            return Double.parseDouble(prompt(label).trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a valid number for " + label.trim());
        }
    }

    private int readInt(String label) {
        try {
            return Integer.parseInt(prompt(label).trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a whole number for " + label.trim());
        }
    }

    private long readLong(String label) {
        try {
            return Long.parseLong(prompt(label).trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a whole number for " + label.trim());
        }
    }

    private void printRows(String heading, List<String> rows) {
        System.out.println("--- " + heading + " ---");
        if (rows.isEmpty()) {
            System.out.println("No records found.");
            return;
        }
        for (String row : rows) {
            System.out.println(row);
        }
    }
}
