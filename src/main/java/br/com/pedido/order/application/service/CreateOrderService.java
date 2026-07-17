package br.com.pedido.order.application.service;

import br.com.pedido.order.application.port.in.CreateOrderUseCase;
import br.com.pedido.order.application.port.out.OrderRepositoryPort;
import br.com.pedido.order.application.service.factory.OrderReservationStrategyFactory;
import br.com.pedido.order.domain.exception.DuplicateOrderException;
import br.com.pedido.order.domain.model.Order;
import br.com.pedido.order.domain.model.OrderStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class CreateOrderService implements CreateOrderUseCase {

    private final OrderRepositoryPort orderRepositoryPort;
    private final OrderReservationStrategyFactory orderReservationStrategyFactory;

    public CreateOrderService(
            OrderRepositoryPort orderRepositoryPort,
            OrderReservationStrategyFactory orderReservationStrategyFactory
    ) {
        this.orderRepositoryPort = orderRepositoryPort;
        this.orderReservationStrategyFactory = orderReservationStrategyFactory;
    }

    @Override
    public Mono<Order> create(Order order) {
        Order pendingOrder = order.withStatus(OrderStatus.PENDING);

        return orderRepositoryPort.existsByOrderId(order.orderId())
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.error(new DuplicateOrderException(order.orderId()));
                    }
                    return orderRepositoryPort.save(pendingOrder)
                            .flatMap(savedOrder -> orderReservationStrategyFactory.getStrategy(savedOrder).process(savedOrder));
                });
    }
}

