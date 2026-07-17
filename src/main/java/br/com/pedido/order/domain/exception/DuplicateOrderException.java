package br.com.pedido.order.domain.exception;

public class DuplicateOrderException extends BusinessException {
    public DuplicateOrderException(String orderId) {
        super("Pedido com orderId " + orderId + " ja foi recebido.");
    }
}

