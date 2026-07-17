package br.com.pedido.order.adapter.out.persistence.mapper;

import br.com.pedido.order.adapter.out.persistence.entity.OrderEntity;
import br.com.pedido.order.adapter.out.persistence.entity.OrderItemEntity;
import br.com.pedido.order.domain.model.Order;
import br.com.pedido.order.domain.model.OrderItem;
import br.com.pedido.order.domain.model.OrderStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderPersistenceMapper {

    public OrderEntity toOrderEntity(Order order) {
        OrderEntity entity = new OrderEntity();
        entity.setOrderId(order.orderId());
        entity.setCustomerId(order.customerId());
        entity.setOrderDate(order.orderDate());
        entity.setTotalAmount(order.totalAmount());
        entity.setStatus(order.status().name());
        return entity;
    }

    public List<OrderItemEntity> toOrderItemEntities(Order order) {
        return order.items().stream()
                .map(item -> {
                    OrderItemEntity entity = new OrderItemEntity();
                    entity.setOrderId(order.orderId());
                    entity.setProductId(item.productId());
                    entity.setQuantity(item.quantity());
                    entity.setPrice(item.price());
                    return entity;
                })
                .toList();
    }

    public Order toDomain(OrderEntity orderEntity, List<OrderItemEntity> items) {
        List<OrderItem> domainItems = items.stream()
                .map(item -> new OrderItem(item.getProductId(), item.getQuantity(), item.getPrice()))
                .toList();

        return new Order(
                orderEntity.getOrderId(),
                orderEntity.getCustomerId(),
                orderEntity.getOrderDate(),
                domainItems,
                orderEntity.getTotalAmount(),
                OrderStatus.valueOf(orderEntity.getStatus())
        );
    }
}

