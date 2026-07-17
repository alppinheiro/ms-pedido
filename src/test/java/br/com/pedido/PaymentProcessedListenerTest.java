package br.com.pedido;

import br.com.pedido.order.adapter.out.kafka.dto.EventEnvelope;
import br.com.pedido.order.adapter.out.kafka.dto.PaymentProcessedEventV1;
import br.com.pedido.config.JacksonConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

public class PaymentProcessedListenerTest {
    @Test
    public void testDeserialization() throws Exception {
        ObjectMapper mapper = new JacksonConfig().objectMapper();
        String json = """
        {
            "eventId": "e3588b4a-4542-4d3f-b7f6-6ddfc1a5d729",
            "eventType": "PaymentProcessed.v1",
            "eventVersion": "1",
            "occurredAt": 1783713891.251659342,
            "source": "pagamento-service",
            "correlationId": null,
            "partitionKey": "ORDER-323",
            "data": {
                "orderId": "ORDER-323",
                "paymentId": "PAY-123456",
                "status": "PAID",
                "processedAt": 1783713891.242380790,
                "amount": 24.3,
                "details": {
                    "paymentMethod": "A_VISTA",
                    "transactionId": "TRX-98765"
                }
            }
        }
        """;
        EventEnvelope envelope = mapper.readValue(json, EventEnvelope.class);
        System.out.println("Envelope: " + envelope.eventId());
        PaymentProcessedEventV1 event = mapper.convertValue(envelope.data(), PaymentProcessedEventV1.class);
        System.out.println("Event: " + event.status());
    }
}
