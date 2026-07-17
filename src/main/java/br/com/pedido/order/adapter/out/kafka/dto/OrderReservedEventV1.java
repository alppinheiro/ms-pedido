package br.com.pedido.order.adapter.out.kafka.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderReservedEventV1(
        String orderId,
        String customerId,
        List<OrderItemEvent> items,
        BigDecimal totalAmount,
        Instant reservedAt,
        String status
) {
}

