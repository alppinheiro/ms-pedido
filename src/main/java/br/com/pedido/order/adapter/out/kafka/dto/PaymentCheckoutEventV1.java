package br.com.pedido.order.adapter.out.kafka.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event payload sent to payment service when an order is reserved.
 * Contains only the fields relevant to payment processing (no item list).
 */
public record PaymentCheckoutEventV1(
        String orderId,
        String customerId,
        BigDecimal totalAmount,
        String paymentMethod,
        Instant reservedAt
) {
}

