package br.com.pedido.order.adapter.in.web.error;

import br.com.pedido.order.domain.exception.BusinessException;
import br.com.pedido.order.domain.exception.StockServiceUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), "Status informado e invalido."));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(WebExchangeBindException exception) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        String message = exception.getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .orElse("Payload invalido.");

        return ResponseEntity.status(status)
                .body(new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message));
    }

    @ExceptionHandler(StockServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleStockServiceUnavailable(StockServiceUnavailableException exception) {
        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status)
                .body(new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), exception.getMessage()));
    }
}

