package br.com.pedido.order.adapter.in.web.error;

import java.time.Instant;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message
) {
}

