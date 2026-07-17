package br.com.pedido.order.application.port.out;

import br.com.pedido.order.domain.model.Order;
import br.com.pedido.order.domain.model.OrderStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface OrderRepositoryPort {
    Mono<Boolean> existsByOrderId(String orderId);

    Mono<Order> save(Order order);

    Mono<Void> updateItems(Order order);

    Mono<Void> updateStatus(String orderId, OrderStatus status);

    Flux<Order> findByFilters(OrderStatus status, String orderId);
}

