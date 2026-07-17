package br.com.pedido.order.application.service;

import br.com.pedido.order.application.port.in.ListOrdersUseCase;
import br.com.pedido.order.application.port.out.OrderRepositoryPort;
import br.com.pedido.order.domain.model.Order;
import br.com.pedido.order.domain.model.OrderStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class ListOrdersService implements ListOrdersUseCase {

    private final OrderRepositoryPort orderRepositoryPort;

    public ListOrdersService(OrderRepositoryPort orderRepositoryPort) {
        this.orderRepositoryPort = orderRepositoryPort;
    }

    @Override
    public Flux<Order> list(OrderStatus status, String orderId) {
        return orderRepositoryPort.findByFilters(status, orderId);
    }
}

