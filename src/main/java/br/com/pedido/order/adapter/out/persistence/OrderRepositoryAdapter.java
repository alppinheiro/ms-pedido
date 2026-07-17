package br.com.pedido.order.adapter.out.persistence;

import br.com.pedido.order.adapter.out.persistence.entity.OrderEntity;
import br.com.pedido.order.adapter.out.persistence.mapper.OrderPersistenceMapper;
import br.com.pedido.order.adapter.out.persistence.repository.OrderItemR2dbcRepository;
import br.com.pedido.order.adapter.out.persistence.repository.OrderR2dbcRepository;
import br.com.pedido.order.application.port.out.OrderRepositoryPort;
import br.com.pedido.order.domain.model.Order;
import br.com.pedido.order.domain.model.OrderStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class OrderRepositoryAdapter implements OrderRepositoryPort {

    private final OrderR2dbcRepository orderR2dbcRepository;
    private final OrderItemR2dbcRepository orderItemR2dbcRepository;
    private final OrderPersistenceMapper orderPersistenceMapper;

    public OrderRepositoryAdapter(
            OrderR2dbcRepository orderR2dbcRepository,
            OrderItemR2dbcRepository orderItemR2dbcRepository,
            OrderPersistenceMapper orderPersistenceMapper
    ) {
        this.orderR2dbcRepository = orderR2dbcRepository;
        this.orderItemR2dbcRepository = orderItemR2dbcRepository;
        this.orderPersistenceMapper = orderPersistenceMapper;
    }

    @Override
    public Mono<Boolean> existsByOrderId(String orderId) {
        return orderR2dbcRepository.existsByOrderId(orderId);
    }

    @Override
    public Mono<Order> save(Order order) {
        return orderR2dbcRepository.save(orderPersistenceMapper.toOrderEntity(order))
                .flatMap(savedOrder -> orderItemR2dbcRepository.saveAll(orderPersistenceMapper.toOrderItemEntities(order))
                        .then(Mono.just(savedOrder)))
                .flatMap(this::loadOrderWithItems);
    }

    @Override
    public Mono<Void> updateItems(Order order) {
        return orderItemR2dbcRepository.findByOrderId(order.orderId())
                .collectList()
                .flatMap(existingEntities -> {
                    existingEntities.forEach(entity -> {
                        order.items().stream()
                                .filter(i -> i.productId().equals(entity.getProductId()))
                                .findFirst()
                                .ifPresent(domainItem -> entity.setReservationIdentifier(domainItem.reservationIdentifier()));
                    });
                    return orderItemR2dbcRepository.saveAll(existingEntities).then();
                });
    }

    @Override
    public Mono<Void> updateStatus(String orderId, OrderStatus status) {
        return orderR2dbcRepository.findByOrderId(orderId)
                .flatMap(existing -> {
                    existing.setStatus(status.name());
                    return orderR2dbcRepository.save(existing);
                })
                .then();
    }

    @Override
    public Flux<Order> findByFilters(OrderStatus status, String orderId) {
        return resolveOrdersByFilters(status, orderId)
                .flatMap(this::loadOrderWithItems);
    }

    private Flux<OrderEntity> resolveOrdersByFilters(OrderStatus status, String orderId) {
        if (status != null && orderId != null && !orderId.isBlank()) {
            return orderR2dbcRepository.findByOrderIdAndStatus(orderId, status.name());
        }
        if (status != null) {
            return orderR2dbcRepository.findByStatus(status.name());
        }
        if (orderId != null && !orderId.isBlank()) {
            return orderR2dbcRepository.findByOrderId(orderId).flux();
        }
        return orderR2dbcRepository.findAll();
    }

    private Mono<Order> loadOrderWithItems(OrderEntity orderEntity) {
        return orderItemR2dbcRepository.findByOrderId(orderEntity.getOrderId())
                .collectList()
                .map(items -> orderPersistenceMapper.toDomain(orderEntity, items));
    }
}

