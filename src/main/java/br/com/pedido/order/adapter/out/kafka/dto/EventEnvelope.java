package br.com.pedido.order.adapter.out.kafka.dto;

import java.lang.Object;
import java.time.Instant;

public record EventEnvelope(
        String eventId,
        String eventType,
        String eventVersion,
        Instant occurredAt,
        String source,
        String correlationId,
        String partitionKey,
        Object data
) {
}


