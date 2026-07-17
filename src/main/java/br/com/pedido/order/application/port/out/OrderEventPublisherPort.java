package br.com.pedido.order.application.port.out;

import br.com.pedido.order.adapter.out.kafka.dto.EventEnvelope;
import reactor.core.publisher.Mono;

public interface OrderEventPublisherPort {
    Mono<Void> publishOrderReserved(EventEnvelope envelope);
}

