package br.com.pedido.order.adapter.out.persistence.repository;

import br.com.pedido.order.adapter.out.persistence.entity.OrderEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface OrderR2dbcRepository extends ReactiveCrudRepository<OrderEntity, Long> {

    Mono<Boolean> existsByOrderId(String orderId);

    Flux<OrderEntity> findByStatus(String status);

    Mono<OrderEntity> findByOrderId(String orderId);

    Flux<OrderEntity> findByOrderIdAndStatus(String orderId, String status);
}

