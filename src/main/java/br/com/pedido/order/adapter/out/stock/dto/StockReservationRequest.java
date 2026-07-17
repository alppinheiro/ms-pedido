package br.com.pedido.order.adapter.out.stock.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StockReservationRequest(Integer quantity, String reservationIdentifier) {
}

