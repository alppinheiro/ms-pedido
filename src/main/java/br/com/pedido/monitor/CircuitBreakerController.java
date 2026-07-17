package br.com.pedido.monitor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/internal/circuit-breakers")
public class CircuitBreakerController {

    private final CircuitBreakerMonitor monitor;

    public CircuitBreakerController(CircuitBreakerMonitor monitor) {
        this.monitor = monitor;
    }

    @GetMapping
    public Mono<Map<String, String>> status() {
        return Mono.just(monitor.getStatuses());
    }
}

