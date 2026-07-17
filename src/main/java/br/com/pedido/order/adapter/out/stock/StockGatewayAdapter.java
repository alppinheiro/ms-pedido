package br.com.pedido.order.adapter.out.stock;

import br.com.pedido.order.adapter.out.stock.dto.StockReservationRequest;
import br.com.pedido.order.application.port.out.StockGatewayPort;
import br.com.pedido.order.domain.exception.StockServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

@Component
public class StockGatewayAdapter implements StockGatewayPort {

    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    private static final Logger log = LoggerFactory.getLogger(StockGatewayAdapter.class);

    public StockGatewayAdapter(
            WebClient.Builder webClientBuilder,
            @Value("${stock.base-url}") String stockBaseUrl,
            CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        this.webClient = webClientBuilder.baseUrl(stockBaseUrl).build();
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("stockService");
    }

    @Override
    public Mono<Boolean> hasStock(String productId, Integer quantity) {
        log.info("[StockGateway] Calling hasStock for product={} quantity={}, circuit='{}' state='{}'",
                productId, quantity, circuitBreaker.getName(), circuitBreaker.getState());

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/products/{productId}/stock")
                        .queryParam("requestedQuantity", quantity)
                        .build(productId)
                )
                .exchangeToMono(response -> mapStockValidationResponse(response.statusCode()))
                // apply circuit breaker operator
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .doOnNext(result -> log.info("[StockGateway] hasStock result={} for product={}, circuit='{}' state='{}'",
                        result, productId, circuitBreaker.getName(), circuitBreaker.getState()))
                .doOnError(throwable -> {
                    log.warn("[StockGateway] hasStock error for product={} circuit='{}' state='{}' - {}",
                            productId, circuitBreaker.getName(), circuitBreaker.getState(), throwable.toString());
                })
                .onErrorMap(throwable -> new StockServiceUnavailableException(
                        "Servico de estoque indisponivel no momento. Tente novamente em instantes.",
                        throwable
                ))
                .doFinally(signal -> logCircuitBreakerMetrics("hasStock", productId));
    }

    @Override
    public Mono<Void> reserve(String productId, Integer quantity) {
        log.info("[StockGateway] Calling reserve for product={} quantity={}, circuit='{}' state='{}'",
                productId, quantity, circuitBreaker.getName(), circuitBreaker.getState());

        return webClient.post()
                .uri("/api/products/{productId}/stock/reservations", productId)
                .bodyValue(new StockReservationRequest(quantity))
                .retrieve()
                .toBodilessEntity()
                .then()
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .doOnSuccess(v -> log.info("[StockGateway] reserve succeeded for product={} circuit='{}' state='{}'",
                        productId, circuitBreaker.getName(), circuitBreaker.getState()))
                // propagate a meaningful error when reservation cannot be completed
                .onErrorMap(throwable -> {
                    log.warn("[StockGateway] reserve failed for product={} circuit='{}' state='{}' - {}",
                            productId, circuitBreaker.getName(), circuitBreaker.getState(), throwable.toString());
                    return new StockServiceUnavailableException(
                            "Servico de estoque indisponivel no momento. Tente novamente em instantes.",
                            throwable
                    );
                })
                .doFinally(signal -> logCircuitBreakerMetrics("reserve", productId));
    }

    @Override
    public Mono<Void> commit(String productId, Integer quantity) {
        log.info("[StockGateway] Calling commit for product={} quantity={}, circuit='{}' state='{}'",
                productId, quantity, circuitBreaker.getName(), circuitBreaker.getState());

        return webClient.post()
                .uri("/api/products/{productId}/stock/outbound", productId)
                .bodyValue(new StockReservationRequest(quantity))
                .retrieve()
                .toBodilessEntity()
                .then()
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .doOnSuccess(v -> log.info("[StockGateway] commit succeeded for product={} circuit='{}' state='{}'",
                        productId, circuitBreaker.getName(), circuitBreaker.getState()))
                .onErrorMap(throwable -> {
                    log.warn("[StockGateway] commit failed for product={} circuit='{}' state='{}' - {}",
                            productId, circuitBreaker.getName(), circuitBreaker.getState(), throwable.toString());
                    return new StockServiceUnavailableException(
                            "Servico de estoque indisponivel no momento. Tente novamente em instantes.",
                            throwable
                    );
                })
                .doFinally(signal -> logCircuitBreakerMetrics("commit", productId));
    }

    private void logCircuitBreakerMetrics(String operation, String productId) {
        try {
            io.github.resilience4j.circuitbreaker.CircuitBreaker.Metrics m = circuitBreaker.getMetrics();
            log.info("[StockGateway][Metrics] op={} product={} circuit='{}' state='{}' bufferedCalls={} successfulCalls={} failedCalls={} notPermittedCalls={} failureRate={}%%",
                    operation,
                    productId,
                    circuitBreaker.getName(),
                    circuitBreaker.getState(),
                    m.getNumberOfBufferedCalls(),
                    m.getNumberOfSuccessfulCalls(),
                    m.getNumberOfFailedCalls(),
                    m.getNumberOfNotPermittedCalls(),
                    m.getFailureRate());
        } catch (Exception ex) {
            log.warn("[StockGateway][Metrics] failed to read circuit breaker metrics: {}", ex.toString());
        }
    }

    private Mono<Boolean> mapStockValidationResponse(HttpStatusCode statusCode) {
        int code = statusCode.value();
        log.debug("[StockGateway] mapStockValidationResponse status={}", code);
        if (statusCode.is2xxSuccessful()) {
            return Mono.just(Boolean.TRUE);
        }
        // Treat 404 Not Found as 'no stock' (business result)
        if (code == 404) {
            return Mono.just(Boolean.FALSE);
        }
        // For other 4xx client errors treat as failures so CircuitBreaker records them
        if (statusCode.is4xxClientError()) {
            return Mono.error(new IllegalStateException("Stock service returned client error: " + code));
        }
        // Treat 5xx and others as errors
        return Mono.error(new IllegalStateException("Falha ao consultar saldo no servico de estoque. HTTP=" + code));
    }
}

