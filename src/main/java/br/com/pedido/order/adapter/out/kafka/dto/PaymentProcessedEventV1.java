package br.com.pedido.order.adapter.out.kafka.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * PaymentProcessedEventV1: event produced by payment service back to order service.
 * Fields:
 * - orderId
 * - paymentId
 * - status (PAID, UNPAID, REJECTED)
 * - processedAt
 * - amount
 * - paymentMethod
 * - transactionId
 * - outcome (optional structured reason)
 */
public record PaymentProcessedEventV1(
        String orderId,
        String paymentId,
        String status,
        Instant processedAt,
        BigDecimal amount,
        String paymentMethod,
        String transactionId,
        PaymentOutcome outcome
) {
}

