package com.example.orderplatform.inventory.messaging;

public final class Topics {
    public static final String ORDER_CREATED = "order.created";
    public static final String STOCK_RESERVED = "stock.reserved";
    public static final String STOCK_REJECTED = "stock.rejected";
    public static final String ORDER_CONFIRMED = "order.confirmed";
    public static final String ORDER_CANCELLED = "order.cancelled";

    private Topics() {
    }
}
