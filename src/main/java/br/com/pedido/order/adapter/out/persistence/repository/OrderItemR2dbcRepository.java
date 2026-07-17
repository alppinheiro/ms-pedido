package br.com.pedido.order.adapter.out.persistence.repository;

import br.com.pedido.order.adapter.out.persistence.entity.OrderItemEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface OrderItemR2dbcRepository extends ReactiveCrudRepository<OrderItemEntity, Long> {

    Flux<OrderItemEntity> findByOrderId(String orderId);
}

