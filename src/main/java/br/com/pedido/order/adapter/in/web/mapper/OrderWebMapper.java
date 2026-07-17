package br.com.pedido.order.adapter.in.web.mapper;

import br.com.pedido.order.adapter.in.web.dto.CreateOrderRequest;
import br.com.pedido.order.adapter.in.web.dto.OrderItemResponse;
import br.com.pedido.order.adapter.in.web.dto.OrderResponse;
import br.com.pedido.order.domain.model.Order;
import br.com.pedido.order.domain.model.OrderItem;
import br.com.pedido.order.domain.model.OrderStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderWebMapper {

    public Order toDomain(CreateOrderRequest request) {
        List<OrderItem> items = request.items().stream()
                .map(item -> new OrderItem(item.productId(), item.quantity(), item.price()))
                .toList();

        return new Order(
                request.orderId(),
                request.customerId(),
                request.orderDate(),
                items,
                request.totalAmount(),
                OrderStatus.PENDING
        );
    }

    public OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.items().stream()
                .map(item -> new OrderItemResponse(item.productId(), item.quantity(), item.price()))
                .toList();

        return new OrderResponse(
                order.orderId(),
                order.customerId(),
                order.orderDate(),
                items,
                order.totalAmount(),
                order.status().name()
        );
    }
}

