package br.com.pedido.order.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CreateOrderRequest(
        @NotBlank(message = "orderId e obrigatorio")
        String orderId,

        @NotBlank(message = "customerId e obrigatorio")
        String customerId,

        @NotNull(message = "orderDate e obrigatorio")
        Instant orderDate,

        @NotEmpty(message = "items e obrigatorio")
        List<@Valid OrderItemRequest> items,

        @NotNull(message = "totalAmount e obrigatorio")
        @DecimalMin(value = "0.0", inclusive = false, message = "totalAmount deve ser maior que zero")
        BigDecimal totalAmount
) {
}

