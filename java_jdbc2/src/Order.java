import java.util.regex.Pattern;

public final class Order {
    private static final Pattern TICKER_PATTERN = Pattern.compile("[A-Z]{1,5}");

    public enum Side {
        BUY,
        SELL
    }

    private final String orderId;
    private final String traderId;
    private final String ticker;
    private final Side side;
    private final double limitPrice;
    private final int quantity;

    public Order(String orderId, String traderId, String ticker, Side side,
                 double limitPrice, int quantity) {
        if (orderId == null || !orderId.matches("[A-Za-z0-9_-]{1,40}")) {
            throw new IllegalArgumentException("Order ID must be 1-40 letters, digits, _ or -.");
        }
        if (traderId == null || traderId.trim().isEmpty() || traderId.trim().length() > 80) {
            throw new IllegalArgumentException("Trader ID must contain 1-80 characters.");
        }
        if (ticker == null || !TICKER_PATTERN.matcher(ticker).matches()) {
            throw new IllegalArgumentException("Ticker must match [A-Z]{1,5}.");
        }
        if (side == null) {
            throw new IllegalArgumentException("Side must be BUY or SELL.");
        }
        if (!Double.isFinite(limitPrice) || limitPrice <= 0) {
            throw new IllegalArgumentException("Limit price must be a positive finite number.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }
        this.orderId = orderId;
        this.traderId = traderId.trim();
        this.ticker = ticker;
        this.side = side;
        this.limitPrice = limitPrice;
        this.quantity = quantity;
    }

    public String getOrderId() { return orderId; }
    public String getTraderId() { return traderId; }
    public String getTicker() { return ticker; }
    public Side getSide() { return side; }
    public double getLimitPrice() { return limitPrice; }
    public int getQuantity() { return quantity; }
}
