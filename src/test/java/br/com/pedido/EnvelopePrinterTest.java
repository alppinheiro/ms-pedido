package br.com.pedido;

import br.com.pedido.order.adapter.out.kafka.dto.EventEnvelope;
import br.com.pedido.order.adapter.out.kafka.dto.PaymentCheckoutEventV1;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

public class EnvelopePrinterTest {

    @Test
    public void printPaymentEnvelope() throws Exception {
        var event = new PaymentCheckoutEventV1(
                "ORDER-999",
                "CUSTOMER-999",
                BigDecimal.valueOf(123.45),
                "A_VISTA",
                Instant.now()
        );

        var envelope = new EventEnvelope(
                java.util.UUID.randomUUID().toString(),
                "OrderReserved.v1",
                "1",
                Instant.now(),
                "pedido-service",
                null,
                "ORDER-999",
                event
        );

        ObjectMapper om = new ObjectMapper();
        om.findAndRegisterModules();
        om.enable(SerializationFeature.INDENT_OUTPUT);
        String json = om.writeValueAsString(envelope);
        System.out.println(json);
    }
}

