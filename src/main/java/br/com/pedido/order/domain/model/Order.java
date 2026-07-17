package br.com.pedido.order.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record Order(
        String orderId,
        String customerId,
        Instant orderDate,
        List<OrderItem> items,
        BigDecimal totalAmount,
        OrderStatus status
) {
    public Order withStatus(OrderStatus nextStatus) {
        return new Order(orderId, customerId, orderDate, items, totalAmount, nextStatus);
    }

    public Order withItems(List<OrderItem> newItems) {
        return new Order(orderId, customerId, orderDate, newItems, totalAmount, status);
    }
}

