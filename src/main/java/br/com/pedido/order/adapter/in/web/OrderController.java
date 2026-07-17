package br.com.pedido.order.adapter.in.web;

import br.com.pedido.order.adapter.in.web.dto.CreateOrderRequest;
import br.com.pedido.order.adapter.in.web.dto.OrderResponse;
import br.com.pedido.order.adapter.in.web.mapper.OrderWebMapper;
import br.com.pedido.order.application.port.in.CreateOrderUseCase;
import br.com.pedido.order.application.port.in.ListOrdersUseCase;
import br.com.pedido.order.domain.model.OrderStatus;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final ListOrdersUseCase listOrdersUseCase;
    private final OrderWebMapper orderWebMapper;

    public OrderController(
            CreateOrderUseCase createOrderUseCase,
            ListOrdersUseCase listOrdersUseCase,
            OrderWebMapper orderWebMapper
    ) {
        this.createOrderUseCase = createOrderUseCase;
        this.listOrdersUseCase = listOrdersUseCase;
        this.orderWebMapper = orderWebMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        return createOrderUseCase.create(orderWebMapper.toDomain(request))
                .map(orderWebMapper::toResponse);
    }

    @GetMapping
    public Flux<OrderResponse> list(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "orderId", required = false) String orderId
    ) {
        OrderStatus parsedStatus = status == null || status.isBlank() ? null : OrderStatus.valueOf(status.toUpperCase());

        return listOrdersUseCase.list(parsedStatus, orderId)
                .map(orderWebMapper::toResponse);
    }
}

