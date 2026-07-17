package br.com.pedido.order.application.port.in;

import br.com.pedido.order.domain.model.Order;
import reactor.core.publisher.Mono;

public interface CreateOrderUseCase {
    Mono<Order> create(Order order);
}

