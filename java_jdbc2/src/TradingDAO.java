import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class TradingDAO {
    public void addOrder(Order order) throws SQLException {
        String sql = "INSERT INTO orders "
                + "(order_id, trader_id, ticker, side, limit_price, quantity, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, 'PENDING')";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, order.getOrderId());
            statement.setString(2, order.getTraderId());
            statement.setString(3, order.getTicker());
            statement.setString(4, order.getSide().name());
            statement.setDouble(5, order.getLimitPrice());
            statement.setInt(6, order.getQuantity());
            statement.executeUpdate();
        }
    }

    public List<Order> findPendingOrders() throws SQLException {
        String sql = "SELECT order_id, trader_id, ticker, side, limit_price, quantity "
                + "FROM orders WHERE status = 'PENDING' ORDER BY created_at";
        List<Order> orders = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                orders.add(new Order(
                        results.getString("order_id"),
                        results.getString("trader_id"),
                        results.getString("ticker"),
                        Order.Side.valueOf(results.getString("side")),
                        results.getDouble("limit_price"),
                        results.getInt("quantity")));
            }
        }
        return orders;
    }

    public List<String> findAllOrders() throws SQLException {
        String sql = "SELECT order_id, trader_id, ticker, side, limit_price, quantity, status, "
                + "created_at FROM orders ORDER BY created_at DESC";
        List<String> rows = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                rows.add(String.format("%s | %s | %s %s | %.4f x %d | %s | %s",
                        results.getString("order_id"), results.getString("trader_id"),
                        results.getString("side"), results.getString("ticker"),
                        results.getDouble("limit_price"), results.getInt("quantity"),
                        results.getString("status"), results.getTimestamp("created_at")));
            }
        }
        return rows;
    }

    public void saveTelemetry(MarketTelemetry telemetry) throws SQLException {
        String sql = "INSERT INTO order_book_logs "
                + "(ticker, bid_price, ask_price, spread, latency_ms, market_depth_lots, "
                + "price_divergence_bps, momentum_bps, inventory_units) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, telemetry.getTicker());
            statement.setDouble(2, telemetry.getBidPrice());
            statement.setDouble(3, telemetry.getAskPrice());
            statement.setDouble(4, telemetry.getSpread());
            statement.setDouble(5, telemetry.getLatencyMs());
            statement.setInt(6, telemetry.getMarketDepthLots());
            statement.setDouble(7, telemetry.getPriceDivergenceBps());
            statement.setDouble(8, telemetry.getMomentumBps());
            statement.setInt(9, telemetry.getInventoryUnits());
            statement.executeUpdate();
        }
    }

    public MarketTelemetry findLatestTelemetry(String ticker) throws SQLException {
        String sql = "SELECT ticker, bid_price, ask_price, latency_ms, market_depth_lots, "
                + "price_divergence_bps, momentum_bps, inventory_units "
                + "FROM order_book_logs WHERE ticker = ? ORDER BY recorded_at DESC LIMIT 1";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ticker);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    return null;
                }
                return new MarketTelemetry(
                        results.getString("ticker"), results.getDouble("bid_price"),
                        results.getDouble("ask_price"), results.getDouble("latency_ms"),
                        results.getInt("market_depth_lots"),
                        results.getDouble("price_divergence_bps"),
                        results.getDouble("momentum_bps"), results.getInt("inventory_units"));
            }
        }
    }

    public void markExecuted(String orderId) throws SQLException {
        updateOrderStatus(orderId, "EXECUTED");
    }

    public void markCancelled(String orderId) throws SQLException {
        updateOrderStatus(orderId, "CANCELLED");
    }

    private void updateOrderStatus(String orderId, String status) throws SQLException {
        String sql = "UPDATE orders SET status = ? WHERE order_id = ? AND status = 'PENDING'";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setString(2, orderId);
            statement.executeUpdate();
        }
    }

    public void saveExecution(Order order, double filledPrice, long slippageTicks,
                              double commission, double qualityScore, String qualityGrade,
                              Instant executedAt)
            throws SQLException {
        String sql = "INSERT INTO executions "
                + "(order_id, filled_price, slippage_ticks, commission, quality_score, "
                + "quality_grade, executed_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement(sql);
                 PreparedStatement update = connection.prepareStatement(
                         "UPDATE orders SET status = 'EXECUTED' "
                                 + "WHERE order_id = ? AND status = 'PENDING'")) {
                insert.setString(1, order.getOrderId());
                insert.setDouble(2, filledPrice);
                insert.setLong(3, slippageTicks);
                insert.setDouble(4, commission);
                insert.setDouble(5, qualityScore);
                insert.setString(6, qualityGrade);
                insert.setTimestamp(7, Timestamp.from(executedAt.truncatedTo(ChronoUnit.MICROS)));
                insert.executeUpdate();

                update.setString(1, order.getOrderId());
                if (update.executeUpdate() != 1) {
                    throw new SQLException("Order is no longer pending: " + order.getOrderId());
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public List<String> findExecutionHistory() throws SQLException {
        String sql = "SELECT e.order_id, o.ticker, o.side, e.filled_price, e.slippage_ticks, "
                + "e.commission, e.quality_score, e.quality_grade, e.executed_at "
                + "FROM executions e JOIN orders o ON o.order_id = e.order_id "
                + "ORDER BY e.executed_at DESC";
        List<String> rows = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                rows.add(String.format(
                        "%s | %s %s | fill %.4f | %d ticks | commission %.4f | "
                                + "score %.1f (%s) | %s",
                        results.getString("order_id"), results.getString("side"),
                        results.getString("ticker"), results.getDouble("filled_price"),
                        results.getLong("slippage_ticks"), results.getDouble("commission"),
                        results.getDouble("quality_score"), results.getString("quality_grade"),
                        results.getTimestamp("executed_at")));
            }
        }
        return rows;
    }

    public List<String> findAllExecutionCsvRows() throws SQLException {
        String sql = "SELECT e.order_id, o.trader_id, o.ticker, o.side, e.filled_price, "
                + "o.quantity, e.slippage_ticks, e.commission, e.quality_score, "
                + "e.quality_grade, e.executed_at FROM executions e "
                + "JOIN orders o ON o.order_id = e.order_id ORDER BY e.executed_at";
        List<String> rows = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                rows.add(String.format("%s,%s,%s,%s,%.4f,%d,%d,%.4f,%.2f,%s,%s",
                        csvField(results.getString("order_id")),
                        csvField(results.getString("trader_id")),
                        csvField(results.getString("ticker")),
                        csvField(results.getString("side")),
                        results.getDouble("filled_price"), results.getInt("quantity"),
                        results.getLong("slippage_ticks"), results.getDouble("commission"),
                        results.getDouble("quality_score"),
                        csvField(results.getString("quality_grade")),
                        csvField(results.getTimestamp("executed_at").toInstant().toString())));
            }
        }
        return rows;
    }

    public void recordRiskBreach(double varPercent, double latencyMs, String reason)
            throws SQLException {
        String sql = "INSERT INTO risk_breaches "
                + "(var_percent, execution_latency_ms, reason, action_taken) "
                + "VALUES (?, ?, ?, 'CIRCUIT_BREAKER_HALT')";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDouble(1, varPercent);
            statement.setDouble(2, latencyMs);
            statement.setString(3, reason);
            statement.executeUpdate();
        }
    }

    public List<String> cancelPendingOrders() throws SQLException {
        List<String> cancelledIds = new ArrayList<>();
        String selectSql = "SELECT order_id FROM orders WHERE status = 'PENDING'";
        String updateSql = "UPDATE orders SET status = 'CANCELLED' WHERE status = 'PENDING'";
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement select = connection.prepareStatement(selectSql);
                 ResultSet results = select.executeQuery()) {
                while (results.next()) {
                    cancelledIds.add(results.getString("order_id"));
                }
                try (PreparedStatement update = connection.prepareStatement(updateSql)) {
                    update.executeUpdate();
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
        return cancelledIds;
    }

    private String csvField(String value) {
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
