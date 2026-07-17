package br.com.pedido.order.adapter.in.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record OrderItemRequest(
        @NotBlank(message = "productId e obrigatorio")
        String productId,

        @NotNull(message = "quantity e obrigatorio")
        @Positive(message = "quantity deve ser maior que zero")
        Integer quantity,

        @NotNull(message = "price e obrigatorio")
        @DecimalMin(value = "0.0", inclusive = false, message = "price deve ser maior que zero")
        BigDecimal price
) {
}

