package br.com.pedido.order.adapter.out.kafka.dto;

/**
 * Structured outcome information for payment failures.
 */
public record PaymentOutcome(
        String reasonCode,
        String reasonMessage,
        String providerCode
) {
}
