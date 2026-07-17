package br.com.pedido.order.adapter.out.kafka.dto;

import java.math.BigDecimal;

public record OrderItemEvent(
        String productId,
        Integer quantity,
        BigDecimal price
) {
}

