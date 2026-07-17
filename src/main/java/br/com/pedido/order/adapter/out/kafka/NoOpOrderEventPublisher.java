package br.com.pedido.order.adapter.out.kafka;

import br.com.pedido.order.adapter.out.kafka.dto.EventEnvelope;
import br.com.pedido.order.application.port.out.OrderEventPublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * No-op publisher used when no real Kafka publisher is present. Keeps DI stable
 * and makes publishing a best-effort operation in non-Kafka environments.
 */
@Component
@ConditionalOnMissingBean(OrderEventPublisherPort.class)
public class NoOpOrderEventPublisher implements OrderEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpOrderEventPublisher.class);

    @Override
    public Mono<Void> publishOrderReserved(EventEnvelope envelope) {
        log.info("[NoOpPublisher] skipping publish for event {} (no Kafka configured)", envelope.eventId());
        return Mono.empty();
    }
}
