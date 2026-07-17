package br.com.pedido.order.domain.model;

import java.math.BigDecimal;

public record OrderItem(
        String productId,
        Integer quantity,
        BigDecimal price,
        String reservationIdentifier
) {
    public OrderItem withReservationIdentifier(String reservationIdentifier) {
        return new OrderItem(productId, quantity, price, reservationIdentifier);
    }
}

