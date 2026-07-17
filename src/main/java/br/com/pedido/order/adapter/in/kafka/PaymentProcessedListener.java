package br.com.pedido.order.adapter.in.kafka;

import br.com.pedido.order.adapter.out.kafka.dto.EventEnvelope;
import br.com.pedido.order.adapter.out.kafka.dto.PaymentProcessedEventV1;
import br.com.pedido.order.application.port.out.OrderRepositoryPort;
import br.com.pedido.order.application.port.out.StockGatewayPort;
import br.com.pedido.order.domain.model.OrderStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class PaymentProcessedListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessedListener.class);

    private final ObjectMapper objectMapper;
    private final OrderRepositoryPort orderRepositoryPort;
    private final StockGatewayPort stockGatewayPort;

    public PaymentProcessedListener(ObjectMapper objectMapper,
                                    OrderRepositoryPort orderRepositoryPort,
                                    StockGatewayPort stockGatewayPort) {
        this.objectMapper = objectMapper;
        this.orderRepositoryPort = orderRepositoryPort;
        this.stockGatewayPort = stockGatewayPort;
    }

    @KafkaListener(topics = "${payment.processed.topic:payment-order}", groupId = "${spring.kafka.consumer.group-id:pedido-service}")
    public void onMessage(String payload) {
        try {
            EventEnvelope envelope = objectMapper.readValue(payload, EventEnvelope.class);
            log.info("[PaymentListener] received envelope eventId={} type={} partitionKey={}", envelope.eventId(), envelope.eventType(), envelope.partitionKey());

            Object dataObj = envelope.data();
            PaymentProcessedEventV1 event = objectMapper.convertValue(dataObj, PaymentProcessedEventV1.class);

            String orderId = envelope.partitionKey();

            if (event == null) {
                log.warn("[PaymentListener] could not parse event data for envelope={}", envelope.eventId());
                return;
            }

            log.info("[PaymentListener] payment status for order={} status={}", orderId, event.status());

            // Idempotency / state guard: only process when order is RESERVED
            log.info("[PaymentListener] querying database for orderId={}", orderId);
            orderRepositoryPort.findByFilters(null, orderId)
                    .next()
                    .switchIfEmpty(reactor.core.publisher.Mono.defer(() -> {
                        log.warn("[PaymentListener] order {} not found in database, skipping payment event", orderId);
                        return reactor.core.publisher.Mono.empty();
                    }))
                    .flatMap(order -> {
                        log.info("[PaymentListener] order {} found in database, status={}", orderId, order.status());
                        if (order.status() == null || order.status() != OrderStatus.RESERVED) {
                            log.info("[PaymentListener] order {} not in RESERVED state (current={}), skipping processing", orderId, order.status());
                            return reactor.core.publisher.Mono.empty();
                        }

                        if ("PAID".equalsIgnoreCase(event.status())) {
                            log.info("[PaymentListener] processing PAID event for order {}, items count: {}", orderId, order.items() != null ? order.items().size() : 0);
                            // commit stock for each item
                            return Flux.fromIterable(order.items())
                                    .concatMap(item -> {
                                        log.info("[PaymentListener] calling commit for product={} qty={} reservation={}", item.productId(), item.quantity(), item.reservationIdentifier());
                                        return stockGatewayPort.commit(item.productId(), item.quantity(), item.reservationIdentifier());
                                    })
                                    .then(orderRepositoryPort.updateStatus(orderId, OrderStatus.COMPLETED))
                                    .doOnSuccess(v -> log.info("[PaymentListener] order {} completed and stock committed", orderId));
                        } else {
                            // payment failed / unpaid
                            log.info("[PaymentListener] payment not successful for order={} status={} outcome={}", orderId, event.status(), event.outcome());
                            // release or mark as failed - here we mark as FAILED and could optionally call release
                            return orderRepositoryPort.updateStatus(orderId, br.com.pedido.order.domain.model.OrderStatus.FAILED)
                                    .doOnSuccess(v -> log.info("[PaymentListener] order {} marked as FAILED due to payment status {}", orderId, event.status()));
                        }
                    })
                    .doOnError(e -> log.error("[PaymentListener] error processing payment event for order {}: {}", envelope.partitionKey(), e.toString()))
                    .block();

        } catch (Exception e) {
            log.error("[PaymentListener] failed to handle message: {}", e.toString());
        }
    }
}

