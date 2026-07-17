package br.com.pedido.order.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        String orderId,
        String customerId,
        Instant orderDate,
        List<OrderItemResponse> items,
        BigDecimal totalAmount,
        String status
) {
}

