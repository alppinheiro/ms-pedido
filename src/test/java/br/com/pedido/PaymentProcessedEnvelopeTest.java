package br.com.pedido;

import br.com.pedido.order.adapter.out.kafka.dto.EventEnvelope;
import br.com.pedido.order.adapter.out.kafka.dto.PaymentProcessedEventV1;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public class PaymentProcessedEnvelopeTest {

    @Test
    public void printPaymentProcessedEnvelope() throws Exception {
        var event = new PaymentProcessedEventV1(
                "ORDER-320",
                "PAY-123456",
                "PAID",
                Instant.now(),
                BigDecimal.valueOf(24.30),
                "A_VISTA",
                "TRX-98765",
                null
        );

        var envelope = new EventEnvelope(
                java.util.UUID.randomUUID().toString(),
                "PaymentProcessed.v1",
                "1",
                Instant.now(),
                "pagamento-service",
                null,
                "ORDER-320",
                event
        );

        ObjectMapper om = new ObjectMapper();
        om.findAndRegisterModules();
        om.enable(SerializationFeature.INDENT_OUTPUT);
        String json = om.writeValueAsString(envelope);
        System.out.println(json);
    }
}

