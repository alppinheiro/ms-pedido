package br.com.pedido.order.application.service.observer;

import br.com.pedido.order.domain.model.Order;
import br.com.pedido.order.domain.model.OrderStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderStatusNotifier {

    private final List<OrderStatusObserver> observers;

    public OrderStatusNotifier(List<OrderStatusObserver> observers) {
        this.observers = observers;
    }

    public void notifyObservers(Order order, OrderStatus previousStatus, OrderStatus currentStatus) {
        observers.forEach(observer -> observer.onStatusChanged(order, previousStatus, currentStatus));
    }
}

