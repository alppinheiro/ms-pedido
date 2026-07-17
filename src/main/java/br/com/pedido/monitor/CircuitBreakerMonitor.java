package br.com.pedido.monitor;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

@Component
public class CircuitBreakerMonitor {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerMonitor.class);

    private final CircuitBreakerRegistry registry;

    public CircuitBreakerMonitor(CircuitBreakerRegistry registry) {
        this.registry = registry;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void registerListeners() {
        // CircuitBreakerRegistry returns a vavr Seq; use forEach to iterate
        registry.getAllCircuitBreakers().forEach(this::attachListeners);
        // Also log current statuses at startup
        log.info("CircuitBreaker statuses at startup: {}", getStatuses());
    }

    private void attachListeners(CircuitBreaker cb) {
        cb.getEventPublisher()
                .onStateTransition(evt -> onStateTransition(cb, evt));

        cb.getEventPublisher()
                .onCallNotPermitted(evt -> log.warn("CircuitBreaker '{}' - call not permitted (open)", cb.getName()));

        cb.getEventPublisher()
                .onError(evt -> log.warn("CircuitBreaker '{}' recorded error: {}", cb.getName(), evt.toString()));
    }

    private void onStateTransition(CircuitBreaker cb, CircuitBreakerOnStateTransitionEvent evt) {
        log.info("CircuitBreaker '{}' state transition: {} -> {}", cb.getName(), evt.getStateTransition().getFromState(), evt.getStateTransition().getToState());
    }

    public Map<String, String> getStatuses() {
        Map<String, String> map = new java.util.HashMap<>();
        registry.getAllCircuitBreakers().forEach(cb -> map.put(cb.getName(), cb.getState().name()));
        return map;
    }

    // Periodically log statuses so operator can see circuit behaviour in logs
    @Scheduled(fixedDelayString = "${circuitbreaker.monitor.interval:30000}")
    public void logStatuses() {
        Map<String, String> statuses = getStatuses();
        if (!statuses.isEmpty()) {
            log.info("CircuitBreaker periodic status: {}", statuses);
        }
    }
}

