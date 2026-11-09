CREATE DATABASE IF NOT EXISTS trading_engine
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE trading_engine;

CREATE TABLE IF NOT EXISTS orders (
    order_id VARCHAR(40) NOT NULL,
    trader_id VARCHAR(80) NOT NULL,
    ticker VARCHAR(5) NOT NULL,
    side ENUM('BUY', 'SELL') NOT NULL,
    limit_price DECIMAL(18, 6) NOT NULL,
    quantity INT UNSIGNED NOT NULL,
    status ENUM('PENDING', 'EXECUTED', 'CANCELLED') NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (order_id),
    CONSTRAINT chk_orders_ticker CHECK (ticker REGEXP '^[A-Z]{1,5}$'),
    CONSTRAINT chk_orders_price CHECK (limit_price > 0),
    CONSTRAINT chk_orders_quantity CHECK (quantity > 0),
    INDEX idx_orders_status_created (status, created_at),
    INDEX idx_orders_ticker (ticker)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS order_book_logs (
    log_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    ticker VARCHAR(5) NOT NULL,
    bid_price DECIMAL(18, 6) NOT NULL,
    ask_price DECIMAL(18, 6) NOT NULL,
    spread DECIMAL(18, 6) NOT NULL,
    latency_ms DECIMAL(12, 6) NOT NULL,
    market_depth_lots INT UNSIGNED NOT NULL,
    price_divergence_bps DECIMAL(12, 6) NOT NULL DEFAULT 0,
    momentum_bps DECIMAL(12, 6) NOT NULL DEFAULT 0,
    inventory_units INT UNSIGNED NOT NULL DEFAULT 0,
    recorded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (log_id),
    CONSTRAINT chk_telemetry_ticker CHECK (ticker REGEXP '^[A-Z]{1,5}$'),
    CONSTRAINT chk_telemetry_bid CHECK (bid_price > 0),
    CONSTRAINT chk_telemetry_ask CHECK (ask_price >= bid_price),
    CONSTRAINT chk_telemetry_spread CHECK (spread >= 0),
    CONSTRAINT chk_telemetry_latency CHECK (latency_ms >= 0),
    INDEX idx_telemetry_latest (ticker, recorded_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS executions (
    execution_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id VARCHAR(40) NOT NULL,
    filled_price DECIMAL(18, 6) NOT NULL,
    slippage_ticks BIGINT UNSIGNED NOT NULL,
    commission DECIMAL(18, 6) NOT NULL,
    quality_score DECIMAL(5, 2) NOT NULL,
    quality_grade VARCHAR(40) NOT NULL,
    executed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (execution_id),
    CONSTRAINT uq_execution_order UNIQUE (order_id),
    CONSTRAINT fk_executions_order FOREIGN KEY (order_id)
        REFERENCES orders (order_id),
    CONSTRAINT chk_execution_price CHECK (filled_price > 0),
    CONSTRAINT chk_execution_commission CHECK (commission >= 0),
    CONSTRAINT chk_execution_quality CHECK (quality_score BETWEEN 0 AND 100),
    INDEX idx_executions_time (executed_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS risk_breaches (
    breach_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    var_percent DECIMAL(12, 6) NOT NULL,
    execution_latency_ms DECIMAL(12, 6) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    action_taken VARCHAR(40) NOT NULL,
    recorded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (breach_id),
    CONSTRAINT chk_breach_var CHECK (var_percent >= 0),
    CONSTRAINT chk_breach_latency CHECK (execution_latency_ms >= 0),
    INDEX idx_risk_breaches_time (recorded_at)
) ENGINE=InnoDB;

-- Safe, fictional sample rows for exploring the schema.
INSERT IGNORE INTO orders
    (order_id, trader_id, ticker, side, limit_price, quantity, status)
VALUES
    ('SAMPLE-EXEC-01', 'demo-trader', 'AAPL', 'BUY', 190.020000, 10, 'EXECUTED'),
    ('SAMPLE-PENDING-01', 'demo-trader', 'MSFT', 'SELL', 410.000000, 5, 'PENDING');

INSERT INTO order_book_logs
    (ticker, bid_price, ask_price, spread, latency_ms, market_depth_lots,
     price_divergence_bps, momentum_bps, inventory_units)
SELECT 'AAPL', 190.000000, 190.020000, 0.020000, 1.200000, 25, 2.500000, 1.500000, 20
WHERE NOT EXISTS (
    SELECT 1 FROM order_book_logs WHERE ticker = 'AAPL'
);

INSERT IGNORE INTO executions
    (order_id, filled_price, slippage_ticks, commission, quality_score,
     quality_grade, executed_at)
VALUES
    ('SAMPLE-EXEC-01', 190.020000, 1, 1.900200, 91.20,
     'Alpha Execution', '2025-01-15 12:00:00.123456');

INSERT INTO risk_breaches
    (var_percent, execution_latency_ms, reason, action_taken)
SELECT 6.000000, 1.200000, 'SAMPLE ONLY: fictional VaR threshold exceeded',
       'CIRCUIT_BREAKER_HALT'
WHERE NOT EXISTS (
    SELECT 1 FROM risk_breaches
    WHERE reason = 'SAMPLE ONLY: fictional VaR threshold exceeded'
);
