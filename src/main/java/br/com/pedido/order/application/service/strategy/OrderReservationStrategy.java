package br.com.pedido.order.application.service.strategy;

import br.com.pedido.order.domain.model.Order;
import reactor.core.publisher.Mono;

public interface OrderReservationStrategy {
    Mono<Order> process(Order order);
}

