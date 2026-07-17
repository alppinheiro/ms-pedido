package br.com.pedido.order.application.service.observer;

import br.com.pedido.order.domain.model.Order;
import br.com.pedido.order.domain.model.OrderStatus;

public interface OrderStatusObserver {
    void onStatusChanged(Order order, OrderStatus previousStatus, OrderStatus currentStatus);
}

