package br.com.pedido.order.adapter.out.kafka;

import br.com.pedido.order.adapter.out.kafka.dto.EventEnvelope;
import br.com.pedido.order.application.port.out.OrderEventPublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

@Component
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class KafkaOrderEventPublisher implements OrderEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaOrderEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

    public KafkaOrderEventPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                                    @Value("${order.payment.topic:order-payment}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Override
    public Mono<Void> publishOrderReserved(EventEnvelope envelope) {
        log.info("[KafkaPublisher] publishing event {} to topic={} partitionKey={}", envelope.eventId(), topic, envelope.partitionKey());
        java.util.concurrent.CompletableFuture<?> lf = kafkaTemplate.send(topic, envelope.partitionKey(), envelope);
        // attach completion logging to the underlying future
        lf.whenComplete((res, ex) -> {
            if (ex != null) {
                log.warn("[KafkaPublisher] failed to send event {}: {}", envelope.eventId(), ex.toString());
            } else {
                log.info("[KafkaPublisher] send future completed for event {}", envelope.eventId());
            }
        });

        java.util.concurrent.CompletableFuture<Void> mapped = lf.thenApply(r -> null);
        return Mono.fromFuture(mapped)
                .doOnSuccess(v -> log.info("[KafkaPublisher] event {} published", envelope.eventId()))
                .doOnError(e -> log.warn("[KafkaPublisher] publish error for event {}: {}", envelope.eventId(), e.toString()));
    }
}




