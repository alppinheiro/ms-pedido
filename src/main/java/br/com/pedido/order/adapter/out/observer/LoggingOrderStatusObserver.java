package br.com.pedido.order.adapter.out.observer;

import br.com.pedido.order.application.service.observer.OrderStatusObserver;
import br.com.pedido.order.domain.model.Order;
import br.com.pedido.order.domain.model.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingOrderStatusObserver implements OrderStatusObserver {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingOrderStatusObserver.class);

    @Override
    public void onStatusChanged(Order order, OrderStatus previousStatus, OrderStatus currentStatus) {
        LOGGER.info(
                "Pedido {} alterado de {} para {}",
                order.orderId(),
                previousStatus,
                currentStatus
        );
    }
}

