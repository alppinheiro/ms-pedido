package br.com.pedido.order.adapter.in.web.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
        String productId,
        Integer quantity,
        BigDecimal price,
        String reservationIdentifier
) {
}

