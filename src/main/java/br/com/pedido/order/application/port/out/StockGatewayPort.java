package br.com.pedido.order.application.port.out;

import reactor.core.publisher.Mono;

public interface StockGatewayPort {
    Mono<Boolean> hasStock(String productId, Integer quantity);

    Mono<Void> reserve(String productId, Integer quantity);

    /**
     * Confirm (commit) the reservation by actually deducting the stock for the product.
     */
    Mono<Void> commit(String productId, Integer quantity);
}

