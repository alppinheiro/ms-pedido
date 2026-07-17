package br.com.pedido.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * Logs the state of every registered {@code @KafkaListener} container once the application
 * is fully started, so it's easy to confirm (without a debugger) that a given listener is
 * really up and consuming from its topic/group.
 */
@Component
public class KafkaListenerStartupLogger {

    private static final Logger log = LoggerFactory.getLogger(KafkaListenerStartupLogger.class);

    private final KafkaListenerEndpointRegistry registry;

    public KafkaListenerStartupLogger(KafkaListenerEndpointRegistry registry) {
        this.registry = registry;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logListenerContainers() {
        if (registry.getListenerContainers().isEmpty()) {
            log.warn("[Kafka] No @KafkaListener containers were registered. Check spring.kafka.bootstrap-servers property.");
            return;
        }
        for (MessageListenerContainer container : registry.getListenerContainers()) {
            log.info("[Kafka] listener id='{}' topics={} groupId={} running={}",
                    container.getListenerId(),
                    java.util.Arrays.toString(container.getContainerProperties().getTopics()),
                    container.getGroupId(),
                    container.isRunning());
        }
    }
}

