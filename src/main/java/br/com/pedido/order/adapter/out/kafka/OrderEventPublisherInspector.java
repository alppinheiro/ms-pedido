package br.com.pedido.order.adapter.out.kafka;

import br.com.pedido.order.application.port.out.OrderEventPublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

@Component
public class OrderEventPublisherInspector implements ApplicationListener<ContextRefreshedEvent> {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisherInspector.class);

    private final ApplicationContext ctx;

    public OrderEventPublisherInspector(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        String[] names = ctx.getBeanNamesForType(OrderEventPublisherPort.class);
        if (names == null || names.length == 0) {
            log.warn("No OrderEventPublisherPort bean found in context");
            return;
        }
        for (String n : names) {
            Object b = ctx.getBean(n);
            log.info("OrderEventPublisherPort bean found: name={} type={}", n, b.getClass().getName());
        }
    }
}

