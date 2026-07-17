package br.com.pedido.order.application.service.strategy;

import br.com.pedido.order.application.port.out.OrderRepositoryPort;
import br.com.pedido.order.application.port.out.StockGatewayPort;
import br.com.pedido.order.application.port.out.OrderEventPublisherPort;
import br.com.pedido.order.application.service.observer.OrderStatusNotifier;
import br.com.pedido.order.domain.exception.InsufficientStockException;
import br.com.pedido.order.domain.model.Order;
import br.com.pedido.order.domain.model.OrderItem;
import br.com.pedido.order.domain.model.OrderStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Component
public class DefaultOrderReservationStrategy implements OrderReservationStrategy {

    private final StockGatewayPort stockGatewayPort;
    private final OrderRepositoryPort orderRepositoryPort;
    private final OrderStatusNotifier orderStatusNotifier;
    private final Optional<OrderEventPublisherPort> eventPublisher;

    public DefaultOrderReservationStrategy(
            StockGatewayPort stockGatewayPort,
            OrderRepositoryPort orderRepositoryPort,
            OrderStatusNotifier orderStatusNotifier,
            Optional<OrderEventPublisherPort> eventPublisher
    ) {
        this.stockGatewayPort = stockGatewayPort;
        this.orderRepositoryPort = orderRepositoryPort;
        this.orderStatusNotifier = orderStatusNotifier;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Mono<Order> process(Order order) {
        return validateStock(order)
                .then(reserveStock(order))
                .then(orderRepositoryPort.updateStatus(order.orderId(), OrderStatus.RESERVED))
                // publish event to payment topic
                .then(Mono.defer(() -> publishOrderReservedEvent(order)))
                .thenReturn(order.withStatus(OrderStatus.RESERVED))
                .doOnNext(updatedOrder -> orderStatusNotifier.notifyObservers(updatedOrder, OrderStatus.PENDING, OrderStatus.RESERVED))
                .onErrorResume(throwable -> markAsFailed(order).then(Mono.error(throwable)));
    }

    private Mono<Void> publishOrderReservedEvent(Order order) {
        // Build a lighter payload for the payment service: do not include item list
        var reservedEvent = new br.com.pedido.order.adapter.out.kafka.dto.PaymentCheckoutEventV1(
                order.orderId(),
                order.customerId(),
                order.totalAmount(),
                "A_VISTA",
                java.time.Instant.now()
        );

        var envelope = new br.com.pedido.order.adapter.out.kafka.dto.EventEnvelope(
                java.util.UUID.randomUUID().toString(),
                "OrderReserved.v1",
                "1",
                java.time.Instant.now(),
                "pedido-service",
                null,
                order.orderId(),
                reservedEvent
        );

        return eventPublisher
                .map(ep -> ep.publishOrderReserved(envelope)
                        .onErrorResume(e -> {
                            // Publication must be best-effort: do not fail the order because Kafka is down
                            org.slf4j.LoggerFactory.getLogger(DefaultOrderReservationStrategy.class)
                                    .warn("Failed to publish OrderReserved event for order {}: {}", order.orderId(), e.toString());
                            return Mono.empty();
                        }))
                .orElse(Mono.empty());
    }

    private Mono<Void> validateStock(Order order) {
        return Flux.fromIterable(order.items())
                .concatMap(this::assertItemHasStock)
                .then();
    }

    private Mono<Void> assertItemHasStock(OrderItem item) {
        return stockGatewayPort.hasStock(item.productId(), item.quantity())
                .flatMap(hasStock -> {
                    if (Boolean.TRUE.equals(hasStock)) {
                        return Mono.empty();
                    }
                    return Mono.error(new InsufficientStockException(item.productId()));
                });
    }

    private Mono<Void> reserveStock(Order order) {
        return Flux.fromIterable(order.items())
                .concatMap(item -> stockGatewayPort.reserve(item.productId(), item.quantity()))
                .then();
    }

    private Mono<Void> markAsFailed(Order order) {
        return orderRepositoryPort.updateStatus(order.orderId(), OrderStatus.FAILED)
                .doOnSuccess(ignored -> orderStatusNotifier.notifyObservers(order, OrderStatus.PENDING, OrderStatus.FAILED));
    }
}
