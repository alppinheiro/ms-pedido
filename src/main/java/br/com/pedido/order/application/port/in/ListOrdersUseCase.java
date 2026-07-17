package br.com.pedido.order.application.port.in;

import br.com.pedido.order.domain.model.Order;
import br.com.pedido.order.domain.model.OrderStatus;
import reactor.core.publisher.Flux;

public interface ListOrdersUseCase {
    Flux<Order> list(OrderStatus status, String orderId);
}

