import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;

public class FillReportWriter {
    private static final String FILL_HEADER =
            "order_id,trader_id,ticker,side,filled_price,quantity,slippage_ticks,"
                    + "commission,quality_score,quality_grade,executed_at";
    private static final String BREAKER_HEADER =
            "timestamp,var_percent,execution_latency_ms,reason,cancelled_orders";
    private final Path outputDirectory;

    public FillReportWriter() {
        this(Path.of("output"));
    }

    FillReportWriter(Path outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public synchronized void appendFill(String csvRow) throws IOException {
        append(outputDirectory.resolve("execution_fills.csv"), FILL_HEADER, List.of(csvRow));
    }

    public synchronized void writeAllFills(List<String> rows) throws IOException {
        Files.createDirectories(outputDirectory);
        try (BufferedWriter writer = Files.newBufferedWriter(
                outputDirectory.resolve("execution_fills.csv"), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            writer.write(FILL_HEADER);
            writer.newLine();
            for (String row : rows) {
                writer.write(row);
                writer.newLine();
            }
        }
    }

    public synchronized void appendCircuitBreakerLog(double varPercent, double latencyMs,
                                                       String reason, int cancelledOrders)
            throws IOException {
        String safeReason = reason.replace("\"", "\"\"");
        String row = String.format("\"%s\",%.4f,%.4f,\"%s\",%d",
                Instant.now(), varPercent, latencyMs, safeReason, cancelledOrders);
        append(outputDirectory.resolve("circuit_breaker_logs.csv"), BREAKER_HEADER, List.of(row));
    }

    private void append(Path file, String header, List<String> rows) throws IOException {
        Files.createDirectories(outputDirectory);
        boolean needsHeader = !Files.exists(file) || Files.size(file) == 0;
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            if (needsHeader) {
                writer.write(header);
                writer.newLine();
            }
            for (String row : rows) {
                writer.write(row);
                writer.newLine();
            }
        }
    }
}
