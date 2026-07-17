import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class TestParsing {
    public static void main(String[] args) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            
            String json = "{\"eventId\": \"e3588b4a-4542-4d3f-b7f6-6ddfc1a5d729\",\"occurredAt\": 1783713891.251659342}";
            EventEnvelope env = mapper.readValue(json, EventEnvelope.class);
            System.out.println("Parsed successfully: " + env);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

record EventEnvelope(String eventId, java.time.Instant occurredAt) {}
