package br.com.pedido.order.application.service.factory;

import br.com.pedido.order.application.service.strategy.DefaultOrderReservationStrategy;
import br.com.pedido.order.application.service.strategy.OrderReservationStrategy;
import br.com.pedido.order.domain.model.Order;
import org.springframework.stereotype.Component;

@Component
public class OrderReservationStrategyFactory {

    private final DefaultOrderReservationStrategy defaultOrderReservationStrategy;

    public OrderReservationStrategyFactory(DefaultOrderReservationStrategy defaultOrderReservationStrategy) {
        this.defaultOrderReservationStrategy = defaultOrderReservationStrategy;
    }

    public OrderReservationStrategy getStrategy(Order order) {
        return defaultOrderReservationStrategy;
    }
}

