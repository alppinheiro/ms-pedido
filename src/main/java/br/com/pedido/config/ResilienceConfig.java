package br.com.pedido.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class ResilienceConfig {
    private static final Logger log = LoggerFactory.getLogger(ResilienceConfig.class);
    // Provide a CircuitBreakerRegistry bean that reads a few common configuration
    // properties from the Spring Environment. This keeps the project independent
    // of the resilience4j Spring Boot autoconfiguration module while still
    // allowing basic configuration via properties (useful in dev/profile tests).

    @Bean
    public io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry circuitBreakerRegistry(org.springframework.core.env.Environment env) {
        // Read configuration for 'stockService' instance
        // sensible development-friendly defaults (match application-dev.properties)
        String slidingWindowType = env.getProperty("resilience4j.circuitbreaker.instances.stockService.slidingWindowType", "COUNT_BASED");
        int slidingWindowSize = Integer.parseInt(env.getProperty("resilience4j.circuitbreaker.instances.stockService.slidingWindowSize", "5"));
        int minimumNumberOfCalls = Integer.parseInt(env.getProperty("resilience4j.circuitbreaker.instances.stockService.minimumNumberOfCalls", "2"));
        float failureRateThreshold = Float.parseFloat(env.getProperty("resilience4j.circuitbreaker.instances.stockService.failureRateThreshold", "25"));
        String waitDuration = env.getProperty("resilience4j.circuitbreaker.instances.stockService.waitDurationInOpenState", "10s");

        log.info("Resilience4j stockService config: slidingWindowType={}, slidingWindowSize={}, minimumNumberOfCalls={}, failureRateThreshold={}%, waitDuration={}",
                slidingWindowType, slidingWindowSize, minimumNumberOfCalls, failureRateThreshold, waitDuration);

        java.time.Duration waitDurationParsed = parseDuration(waitDuration);

        io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType swType =
                "TIME_BASED".equalsIgnoreCase(slidingWindowType) ?
                        io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType.TIME_BASED :
                        io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType.COUNT_BASED;

        io.github.resilience4j.circuitbreaker.CircuitBreakerConfig config = io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                .slidingWindowType(swType)
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(minimumNumberOfCalls)
                .failureRateThreshold(failureRateThreshold)
                .waitDurationInOpenState(waitDurationParsed)
                .recordExceptions(Throwable.class)
                .build();

        // Use this config as registry default so circuitBreaker("stockService") gets
        // the profile-driven thresholds (window size, minimum calls, failure rate, etc).
        return io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry.of(config);
    }

    private java.time.Duration parseDuration(String value) {
        try {
            if (value == null) return java.time.Duration.ofSeconds(60);
            value = value.trim().toLowerCase();
            if (value.endsWith("ms")) {
                long ms = Long.parseLong(value.substring(0, value.length() - 2));
                return java.time.Duration.ofMillis(ms);
            }
            if (value.endsWith("s")) {
                long s = Long.parseLong(value.substring(0, value.length() - 1));
                return java.time.Duration.ofSeconds(s);
            }
            if (value.endsWith("m")) {
                long m = Long.parseLong(value.substring(0, value.length() - 1));
                return java.time.Duration.ofMinutes(m);
            }
            // fallback to ISO-8601 parser
            return java.time.Duration.parse(value);
        } catch (Exception e) {
            return java.time.Duration.ofSeconds(60);
        }
    }
}

