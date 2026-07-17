package br.com.pedido.order.domain.exception;

public class InsufficientStockException extends BusinessException {
    public InsufficientStockException(String productId) {
        super("Nao existe saldo suficiente no estoque para o produto " + productId + ".");
    }
}

