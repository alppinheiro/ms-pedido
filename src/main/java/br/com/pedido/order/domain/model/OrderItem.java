package br.com.pedido.order.domain.model;

import java.math.BigDecimal;

public record OrderItem(
        String productId,
        Integer quantity,
        BigDecimal price
) {
}

