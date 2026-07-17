package br.com.pedido.order.domain.exception;

public class StockServiceUnavailableException extends RuntimeException {
    public StockServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

