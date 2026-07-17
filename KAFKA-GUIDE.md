# Guia Completo de Kafka neste Projeto

Este documento reúne **tudo** que foi implementado e configurado neste projeto para o Apache Kafka funcionar: infraestrutura (docker-compose), produtor (producer), consumidor (consumer), contratos de eventos, configurações do Spring, e o passo a passo para rodar/testar/depurar. A ideia é servir como material de estudo para quem está aprendendo Kafka.

---

## 1. Visão geral da arquitetura de mensageria

```
┌─────────────────┐   produz    tópico "order-payment"   ┌──────────────────────┐
│  pedido-service   │ ───────────────────────────────────▶ │  pagamento-service    │
│ (este projeto)    │                                       │ (externo/simulado)   │
│                    │   tópico "payment-order"    consome  │                       │
│                    │ ◀─────────────────────────────────── │                       │
└─────────────────┘                                        └──────────────────────┘
```

- **Producer** (`pedido-service` → tópico `order-payment`): quando um pedido é reservado com sucesso (estoque OK), publicamos um evento `OrderReserved.v1` (payload reduzido, chamado `PaymentCheckoutEventV1`) para o serviço de pagamento decidir como cobrar o cliente.
- **Consumer** (`pedido-service` ← tópico `payment-order`): quando o serviço de pagamento processa o pagamento (aprovado ou não), ele publica um evento `PaymentProcessed.v1` nesse tópico. O `pedido-service` escuta esse tópico, confere se o pagamento foi `PAID`, e caso positivo, dá baixa no estoque e marca o pedido como `COMPLETED` (ou `FAILED` se o pagamento não for aprovado).

Ambos os tópicos são criados automaticamente ao subir a infraestrutura (ver seção 2).

---

## 2. Infraestrutura local (docker-compose)

Arquivo: [`docker-compose.yml`](./docker-compose.yml)

### Serviços Kafka relacionados

| Serviço      | Imagem                              | Papel                                                             |
|--------------|--------------------------------------|--------------------------------------------------------------------|
| `zookeeper`  | `confluentinc/cp-zookeeper:7.4.0`    | Coordenação do cluster Kafka (metadados, eleição de broker/controller) |
| `kafka`      | `confluentinc/cp-kafka:7.4.0`        | O broker Kafka em si                                                |
| `kafka-init` | `confluentinc/cp-kafka:7.4.0`        | Container "de uso único" que cria os tópicos assim que o broker fica saudável |
| `kafka-ui`   | `provectuslabs/kafka-ui:latest`      | Interface web para inspecionar tópicos, mensagens, consumer groups |

### Detalhes importantes do `kafka`

```yaml
kafka:
  image: confluentinc/cp-kafka:7.4.0
  environment:
    KAFKA_BROKER_ID: 1
    KAFKA_ZOOKEEPER_CONNECT: 'zookeeper:2181'
    KAFKA_LISTENERS: INTERNAL://0.0.0.0:9092,EXTERNAL://0.0.0.0:9093
    KAFKA_ADVERTISED_LISTENERS: INTERNAL://kafka:9092,EXTERNAL://localhost:9092
    KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT
    KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    KAFKA_INTER_BROKER_LISTENER_NAME: INTERNAL
  ports:
    - "9092:9093"
  volumes:
    - kafka_data:/var/lib/kafka/data
    - kafka_secrets:/etc/kafka/secrets
```

Pontos-chave para quem está aprendendo:

1. **Dois listeners (`INTERNAL` e `EXTERNAL`)**: Kafka precisa anunciar um endereço que os clientes consigam realmente alcançar (`advertised.listeners`). Como temos clientes rodando **dentro** da rede docker (outros containers, ex.: `kafka-ui`, `kafka-init`) e clientes rodando **fora** (sua aplicação local, ou seu terminal), usamos dois listeners:
   - `INTERNAL://kafka:9092` — usado por containers dentro da rede docker (eles resolvem o hostname `kafka`).
   - `EXTERNAL://localhost:9092` — usado por processos rodando no seu host (sua aplicação Java local, `kafka-console-producer` no terminal, etc). Só é acessível pois o compose mapeia a porta do host `9092` para a porta **do container** `9093` (`"9092:9093"`), que é onde o listener `EXTERNAL` escuta de fato.
2. **`KAFKA_BROKER_ID`**: identificador único do broker no cluster. Se você trocar esse valor **depois** que o broker já gravou dados em `kafka_data`, ele vai falhar ao subir com `InconsistentBrokerIdException` (o Kafka grava o broker.id usado originalmente em `meta.properties` dentro do volume). Regra prática: mantenha estável, ou limpe o volume se precisar mudar.
3. **Volumes nomeados (`kafka_data`, `kafka_secrets`, `zookeeper_data`, `zookeeper_log`)**: garantem que os dados dos tópicos e metadados do Zookeeper **sobrevivem** a um `docker compose down` / `up`. Sem isso, cada `down` limpa containers (mas não recria RES automaticamente os volumes anônimos com o `broker.id` certo), o que causava erros do tipo `NodeExistsException` (znode órfão no Zookeeper) e `InconsistentBrokerIdException` (broker.id divergente do gravado em disco).
4. **`healthcheck`**: usa `kafka-broker-api-versions` para considerar o broker "healthy" só quando ele já responde requisições — outros serviços (`kafka-init`) esperam esse estado antes de continuar (`depends_on: condition: service_healthy`).

### Criação automática de tópicos (`kafka-init`)

```yaml
kafka-init:
  image: confluentinc/cp-kafka:7.4.0
  depends_on:
    kafka:
      condition: service_healthy
  entrypoint: ["bash","-c","kafka-topics --bootstrap-server kafka:9092 --create --topic order-payment --partitions 1 --replication-factor 1 || true; kafka-topics --bootstrap-server kafka:9092 --create --topic payment-order --partitions 1 --replication-factor 1 || true; sleep 1"]
  restart: "no"
```

- Container "efêmero": ele roda uma vez, cria os dois tópicos (`order-payment` e `payment-order`) usando a CLI `kafka-topics`, e depois termina (`restart: "no"`).
- `|| true` evita que o comando falhe caso o tópico já exista (idempotente entre reinícios).
- 1 partição / replication-factor 1 é suficiente para desenvolvimento local (cluster de broker único).

### Kafka UI

```yaml
kafka-ui:
  image: provectuslabs/kafka-ui:latest
  environment:
    KAFKA_CLUSTERS_0_NAME: local
    KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:9092
  ports:
    - "8085:8080"
```

- Acesse em: **http://localhost:8085**
- Nele você consegue: ver os tópicos, publicar mensagens manualmente (útil para simular o serviço de pagamento), ver mensagens já publicadas, e consultar consumer groups/offsets/lag.
- Note que `KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS` usa `kafka:9092` (o hostname interno), pois o Kafka UI roda **dentro** da rede docker.

### Subir/derrubar a infraestrutura

```bash
# subir tudo (kafka, zookeeper, postgres, kafka-ui) em background
docker compose --env-file .env up -d

# parar tudo (mantém os volumes/dados)
docker compose --env-file .env down

# ver status
docker compose --env-file .env ps

# ver logs de um serviço
docker compose --env-file .env logs -f kafka
```

⚠️ Se você quiser **realmente zerar** os dados do Kafka/Zookeeper (apagar tópicos, offsets, etc), remova os volumes nomeados explicitamente:

```bash
docker compose --env-file .env down
docker volume rm pedido_kafka_data pedido_kafka_secrets pedido_zookeeper_data pedido_zookeeper_log
docker compose --env-file .env up -d
```

---

## 3. Configuração da aplicação Spring Boot

### 3.1 Dependência (`pom.xml`)

```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

### 3.2 Propriedades (`application-dev.properties`)

```properties
# Kafka (development)
spring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
spring.kafka.consumer.group-id=pedido-service
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
# Consumer usa StringDeserializer (não JsonDeserializer) porque o listener faz o
# parse manual do JSON com Jackson (ver seção 5). Isso dá controle total sobre erros
# de desserialização e sobre a lib usada (não depende de headers de tipo do Kafka).
spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.properties.spring.json.trusted.packages=*
```

Pontos-chave:
- `spring.kafka.bootstrap-servers`: endereço do broker. Em `dev` usamos `localhost:9092` (app rodando fora do Docker, acessando o listener `EXTERNAL` do Kafka). Se a app rodasse **dentro** do docker-compose, o valor correto seria `kafka:9092`.
- `spring.kafka.consumer.group-id=pedido-service`: nome do **consumer group**. Todo `@KafkaListener` deste app entra nesse grupo. Isso importa para:
  - Balanceamento de partições entre múltiplas instâncias da app (cada partição só é lida por 1 consumer do grupo por vez).
  - Rastreamento de offset "por grupo" no broker (o Kafka lembra até onde cada grupo já leu).
- `spring.kafka.consumer.auto-offset-reset=earliest`: quando um **consumer group novo** (sem offset commitado) se conecta, ele lê o tópico **desde o início** em vez de só mensagens novas (`latest`, que é o padrão). Isso é ótimo para reprocessar mensagens antigas durante testes/aprendizado.
- Apenas `application-dev.properties` define essas propriedades. Os profiles `local` e `prod` não têm Kafka configurado (propositalmente neste estágio do projeto).

### 3.3 `.env`

```dotenv
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_UI_PORT=8085
```

Usado tanto pelo Spring (`${KAFKA_BOOTSTRAP_SERVERS:...}`) quanto pelo `docker compose --env-file .env` (para variáveis referenciadas no `docker-compose.yml`, embora `KAFKA_UI_PORT` hoje esteja fixo em `8085` no compose — pode ser parametrizado se desejar).

### 3.4 `KafkaConfig.java` — beans explícitos

Arquivo: [`src/main/java/br/com/pedido/config/KafkaConfig.java`](./src/main/java/br/com/pedido/config/KafkaConfig.java)

```java
@Configuration
@EnableKafka
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class KafkaConfig {

    @Bean
    public ProducerFactory<String, Object> producerFactory(@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> pf) {
        return new KafkaTemplate<>(pf);
    }

    @Bean
    public ConsumerFactory<String, String> consumerFactory(...) { ... }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(...) { ... }
}
```

Por que declaramos os beans **explicitamente** em vez de confiar 100% na autoconfiguração do Spring Boot?

- O Spring Boot já autoconfigura `ProducerFactory`, `ConsumerFactory` e `ConcurrentKafkaListenerContainerFactory` a partir das propriedades `spring.kafka.*`. Só de ter `@KafkaListener` em algum bean + `spring-kafka` no classpath, isso já funcionaria "magicamente".
- Mas neste projeto preferimos declarar os beans manualmente por 3 motivos didáticos/práticos:
  1. **Clareza** — fica explícito no código o que está sendo configurado (serializers, deserializers, group-id) sem precisar ler a documentação de autoconfiguração do Spring Boot.
  2. **Controle** — podemos customizar facilmente (ex.: trocar deserializer, adicionar error handlers, mudar concorrência) sem lutar contra beans autoconfigurados.
  3. **Depuração** — Adicionamos logs (`log.info("[KafkaConfig] Building consumer factory ...")`) direto na criação do bean, então já no boot da aplicação você vê exatamente qual `bootstrap-servers`/`group-id` foi usado.
- `@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")`: essa configuração inteira (producer + consumer) só é ativada se essa propriedade existir. Assim, em profiles sem Kafka configurado (`local`, `prod` hoje), a aplicação sobe normalmente sem tentar se conectar a um broker inexistente.
- `@EnableKafka`: habilita o processamento de anotações `@KafkaListener` (tecnicamente o Spring Boot já ativa isso via autoconfiguração quando detecta `spring-kafka` no classpath, mas declaramos explicitamente para deixar claro no código que a "mágica" de registrar listeners depende dessa anotação).

### 3.5 `JacksonConfig.java` — ObjectMapper compartilhado

Arquivo: [`src/main/java/br/com/pedido/config/JacksonConfig.java`](./src/main/java/br/com/pedido/config/JacksonConfig.java)

```java
@Configuration
public class JacksonConfig {
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());          // suporta Instant/LocalDateTime etc
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // grava datas como ISO-8601, não epoch
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false); // tolera campos novos/desconhecidos
        return mapper;
    }
}
```

Esse `ObjectMapper` é injetado no `PaymentProcessedListener` para fazer o parse manual do payload JSON recebido do Kafka (ver seção 5). Sem esse bean explícito, a aplicação falhava no startup com:

```
Parameter 0 of constructor in br.com.pedido.order.adapter.in.kafka.PaymentProcessedListener required a bean
of type 'com.fasterxml.jackson.databind.ObjectMapper' that could not be found.
```

### 3.6 `KafkaListenerStartupLogger.java` — observabilidade "sem debugger"

Arquivo: [`src/main/java/br/com/pedido/config/KafkaListenerStartupLogger.java`](./src/main/java/br/com/pedido/config/KafkaListenerStartupLogger.java)

```java
@Component
public class KafkaListenerStartupLogger {

    private final KafkaListenerEndpointRegistry registry;

    @EventListener(ApplicationReadyEvent.class)
    public void logListenerContainers() {
        for (MessageListenerContainer container : registry.getListenerContainers()) {
            log.info("[Kafka] listener id='{}' topics={} groupId={} running={}",
                    container.getListenerId(), ..., container.getGroupId(), container.isRunning());
        }
    }
}
```

- Assim que o Spring termina de subir (`ApplicationReadyEvent`), este componente varre o `KafkaListenerEndpointRegistry` (registro central de todos os containers criados a partir de métodos `@KafkaListener`) e loga o estado de cada um.
- Exemplo real de log:
  ```
  [Kafka] listener id='org.springframework.kafka.KafkaListenerEndpointContainer#0' topics=[payment-order] groupId=pedido-service running=true
  ```
- Isso resolve um problema comum de quem está aprendendo Kafka: "publiquei uma mensagem e nada aconteceu, será que meu listener está ativo?" — com esse log você confirma isso **sem precisar debugar**, só olhando os logs de startup.

---

## 4. Producer — publicando eventos (`order-payment`)

### 4.1 Contrato de evento (envelope genérico)

Arquivo: [`EventEnvelope.java`](./src/main/java/br/com/pedido/order/adapter/out/kafka/dto/EventEnvelope.java)

```java
public record EventEnvelope(
        String eventId,       // UUID único do evento (idempotência/rastreio)
        String eventType,     // ex.: "OrderReserved.v1"
        String eventVersion,  // ex.: "1" — permite evoluir o payload sem quebrar consumidores antigos
        Instant occurredAt,   // quando o evento ocorreu
        String source,        // "pedido-service"
        String correlationId, // opcional, para rastrear uma cadeia de eventos relacionados
        String partitionKey,  // usado como chave Kafka (ex.: orderId) -> garante ordenação por pedido
        Object data            // payload específico do evento (polimórfico)
) {}
```

Por que usar um "envelope" em vez de mandar o payload puro?
- **Versionamento**: `eventType` + `eventVersion` deixam explícito qual é o "contrato" daquele evento, permitindo evoluir o payload sem quebrar consumidores antigos (basta lançar `eventType = "OrderReserved.v2"` no futuro).
- **Rastreabilidade**: `eventId` e `correlationId` ajudam a rastrear e a implementar idempotência do lado do consumidor.
- **Particionamento consistente**: `partitionKey` é sempre `orderId`, garantindo que todos os eventos de um mesmo pedido caiam na mesma partição e sejam processados em ordem.

### 4.2 Payload específico enviado para pagamento

Arquivo: [`PaymentCheckoutEventV1.java`](./src/main/java/br/com/pedido/order/adapter/out/kafka/dto/PaymentCheckoutEventV1.java)

```java
public record PaymentCheckoutEventV1(
        String orderId,
        String customerId,
        BigDecimal totalAmount,
        String paymentMethod,   // fixo em "A_VISTA" neste estágio do projeto
        Instant reservedAt
) {}
```

Decisão de design: o serviço de pagamento **não precisa** saber quais produtos/itens compõem o pedido — só precisa saber quem é o cliente, quanto cobrar e a forma de pagamento. Por isso o payload **não inclui a lista de itens** (diferente de um payload de "pedido completo").

### 4.3 Onde o evento é montado e publicado

Arquivo: [`DefaultOrderReservationStrategy.java`](./src/main/java/br/com/pedido/order/application/service/strategy/DefaultOrderReservationStrategy.java)

```java
private Mono<Void> publishOrderReservedEvent(Order order) {
    var reservedEvent = new PaymentCheckoutEventV1(
            order.orderId(), order.customerId(), order.totalAmount(), "A_VISTA", Instant.now());

    var envelope = new EventEnvelope(
            UUID.randomUUID().toString(), "OrderReserved.v1", "1",
            Instant.now(), "pedido-service", null, order.orderId(), reservedEvent);

    return eventPublisher
            .map(ep -> ep.publishOrderReserved(envelope)
                    .onErrorResume(e -> {
                        log.warn("Failed to publish OrderReserved event for order {}: {}", order.orderId(), e.toString());
                        return Mono.empty(); // publicação é "best-effort"
                    }))
            .orElse(Mono.empty());
}
```

Fluxo completo do processamento de um pedido (`process(Order order)`):
1. `validateStock` — confere disponibilidade de estoque para cada item (chamando o serviço externo de estoque, protegido por Circuit Breaker).
2. `reserveStock` — reserva o estoque.
3. `updateStatus(RESERVED)` — atualiza o status do pedido no banco.
4. `publishOrderReservedEvent` — publica o evento `OrderReserved.v1` no tópico `order-payment` (**best-effort**: se o Kafka estiver fora do ar, isso **não** derruba o pedido — só loga um warning).
5. Em caso de erro em qualquer etapa anterior, `markAsFailed` marca o pedido como `FAILED`.

> 💡 **Publicação best-effort**: propositalmente, uma falha ao publicar no Kafka não deve impedir o pedido de ser criado/reservado — é uma decisão de negócio (o Kafka é "fire-and-forget" aqui, não uma transação distribuída).

### 4.4 O publisher de fato (`KafkaOrderEventPublisher`)

Arquivo: [`KafkaOrderEventPublisher.java`](./src/main/java/br/com/pedido/order/adapter/out/kafka/KafkaOrderEventPublisher.java)

```java
@Component
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class KafkaOrderEventPublisher implements OrderEventPublisherPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic; // "order-payment" (via @Value("${order.payment.topic:order-payment}"))

    @Override
    public Mono<Void> publishOrderReserved(EventEnvelope envelope) {
        CompletableFuture<?> future = kafkaTemplate.send(topic, envelope.partitionKey(), envelope);
        future.whenComplete((res, ex) -> {
            if (ex != null) log.warn("[KafkaPublisher] failed to send event {}: {}", envelope.eventId(), ex.toString());
            else log.info("[KafkaPublisher] send future completed for event {}", envelope.eventId());
        });
        return Mono.fromFuture(future.thenApply(r -> null))
                .doOnSuccess(v -> log.info("[KafkaPublisher] event {} published", envelope.eventId()))
                .doOnError(e -> log.warn("[KafkaPublisher] publish error for event {}: {}", envelope.eventId(), e.toString()));
    }
}
```

Pontos-chave:
- `kafkaTemplate.send(topic, key, value)`: API assíncrona do Spring Kafka — retorna um `CompletableFuture` que completa quando o broker confirma o recebimento (ack) ou falha.
- A **chave** (`envelope.partitionKey()` = `orderId`) determina a partição de destino (hash da chave % número de partições). Isso garante que todos os eventos de um mesmo pedido vão sempre para a mesma partição, preservando a ordem entre eles.
- O `value-serializer` configurado (`JsonSerializer`) serializa automaticamente o `EventEnvelope` (e o `data` dentro dele) para JSON antes de mandar pro broker.
- `JsonSerializer.ADD_TYPE_INFO_HEADERS = false`: por padrão, o `JsonSerializer` do Spring Kafka adiciona headers extras na mensagem com o nome completo da classe Java (`__TypeId__`), para permitir desserialização automática com o tipo exato do lado do consumidor. Desativamos isso porque:
  1. O consumidor deste projeto (`PaymentProcessedListener`) faz o parse manual via Jackson (não usa `JsonDeserializer` automático).
  2. Um serviço de pagamento externo (em outra linguagem/stack) não teria como interpretar esses headers específicos do Spring — melhor manter a mensagem "limpa", contendo só o JSON.
- Callback (`.whenComplete(...)`) + `Mono.fromFuture(...)`: integra a API baseada em `CompletableFuture` do Spring Kafka com o mundo reativo (`Mono`) usado no resto da aplicação (WebFlux/R2DBC).

### 4.5 Fallback sem Kafka (`NoOpOrderEventPublisher`)

Arquivo: [`NoOpOrderEventPublisher.java`](./src/main/java/br/com/pedido/order/adapter/out/kafka/NoOpOrderEventPublisher.java)

```java
@Component
@ConditionalOnMissingBean(OrderEventPublisherPort.class)
public class NoOpOrderEventPublisher implements OrderEventPublisherPort {
    @Override
    public Mono<Void> publishOrderReserved(EventEnvelope envelope) {
        log.info("[NoOpPublisher] skipping publish for event {} (no Kafka configured)", envelope.eventId());
        return Mono.empty();
    }
}
```

- Só é criado se **nenhum outro** bean `OrderEventPublisherPort` existir no contexto (ou seja, quando `KafkaOrderEventPublisher` não é criado por falta de `spring.kafka.bootstrap-servers`, como acontece nos profiles `local`/`prod`).
- Isso permite que a aplicação funcione (crie/processe pedidos normalmente) **mesmo sem Kafka configurado**, sem precisar de `if`s espalhados pelo código de negócio — um exemplo do padrão *Null Object*.

### 4.6 Verificação de qual publisher está ativo (`OrderEventPublisherInspector`)

Arquivo: [`OrderEventPublisherInspector.java`](./src/main/java/br/com/pedido/order/adapter/out/kafka/OrderEventPublisherInspector.java)

Loga, assim que o contexto Spring sobe, qual implementação de `OrderEventPublisherPort` foi realmente registrada:
```
OrderEventPublisherPort bean found: name=kafkaOrderEventPublisher type=br.com.pedido.order.adapter.out.kafka.KafkaOrderEventPublisher
```
Útil para confirmar rapidamente, sem abrir debugger, se a app subiu "com Kafka" ou em modo *no-op*.

---

## 5. Consumer — consumindo eventos (`payment-order`)

Arquivo: [`PaymentProcessedListener.java`](./src/main/java/br/com/pedido/order/adapter/in/kafka/PaymentProcessedListener.java)

### 5.1 Contrato de evento recebido

Arquivo: [`PaymentProcessedEventV1.java`](./src/main/java/br/com/pedido/order/adapter/out/kafka/dto/PaymentProcessedEventV1.java)

```java
public record PaymentProcessedEventV1(
        String orderId,
        String paymentId,
        String status,          // "PAID", "UNPAID", "REJECTED"
        Instant processedAt,
        BigDecimal amount,
        String paymentMethod,
        String transactionId,
        PaymentOutcome outcome  // motivo estruturado quando status != PAID
) {}
```

E o motivo de falha, quando aplicável ([`PaymentOutcome.java`](./src/main/java/br/com/pedido/order/adapter/out/kafka/dto/PaymentOutcome.java)):

```java
public record PaymentOutcome(
        String reasonCode,     // ex.: "PAYMENT_DECLINED_INSUFFICIENT_FUNDS" (machine-readable)
        String reasonMessage,  // ex.: "Saldo insuficiente" (legível)
        String providerCode    // código bruto devolvido pelo gateway de pagamento
) {}
```

### 5.2 O listener

```java
@Component
public class PaymentProcessedListener {

    private final ObjectMapper objectMapper;
    private final OrderRepositoryPort orderRepositoryPort;
    private final StockGatewayPort stockGatewayPort;

    @KafkaListener(topics = "${payment.processed.topic:payment-order}",
                   groupId = "${spring.kafka.consumer.group-id:pedido-service}")
    public void onMessage(String payload) {
        try {
            EventEnvelope envelope = objectMapper.readValue(payload, EventEnvelope.class);
            PaymentProcessedEventV1 event = objectMapper.convertValue(envelope.data(), PaymentProcessedEventV1.class);
            String orderId = envelope.partitionKey();

            orderRepositoryPort.findByFilters(null, orderId)
                    .next()
                    .flatMap(order -> {
                        if (order.status() != OrderStatus.RESERVED) {
                            // idempotência / guarda de estado: ignora se o pedido não está aguardando pagamento
                            return Mono.empty();
                        }
                        if ("PAID".equalsIgnoreCase(event.status())) {
                            return Flux.fromIterable(order.items())
                                    .concatMap(item -> stockGatewayPort.commit(item.productId(), item.quantity()))
                                    .then(orderRepositoryPort.updateStatus(orderId, OrderStatus.COMPLETED));
                        } else {
                            return orderRepositoryPort.updateStatus(orderId, OrderStatus.FAILED);
                        }
                    })
                    .subscribe();
        } catch (Exception e) {
            log.error("[PaymentListener] failed to handle message: {}", e.toString());
        }
    }
}
```

### 5.3 Anatomia do `@KafkaListener`

```java
@KafkaListener(topics = "${payment.processed.topic:payment-order}",
               groupId = "${spring.kafka.consumer.group-id:pedido-service}")
```

- `topics`: nome do tópico a escutar. Usa um *placeholder* de propriedade com valor default (`payment-order`), então funciona mesmo sem a propriedade `payment.processed.topic` definida em nenhum `.properties`.
- `groupId`: também com valor default (`pedido-service`) — importante para que o listener **sempre** consiga subir, mesmo em profiles que não definem `spring.kafka.consumer.group-id` explicitamente (evita `IllegalArgumentException: Could not resolve placeholder` no startup).
- Assinatura do método (`onMessage(String payload)`): como o consumer está configurado com `StringDeserializer` (ver seção 3.2), o Spring entrega a mensagem já como `String` (o JSON bruto). Poderíamos alternativamente usar `JsonDeserializer` no consumer e receber diretamente um objeto tipado — optamos por String + parse manual para ter controle explícito sobre erros de parsing (try/catch dedicado) e não depender de headers de tipo (`__TypeId__`) que o produtor não envia (`ADD_TYPE_INFO_HEADERS=false`, seção 4.4).

### 5.4 O que acontece "por baixo dos panos" quando a aplicação sobe

1. O Spring escaneia todos os beans procurando métodos anotados com `@KafkaListener` (habilitado por `@EnableKafka`).
2. Para cada um encontrado, cria um `KafkaListenerEndpoint` e registra no `KafkaListenerEndpointRegistry`.
3. Usa a `ConcurrentKafkaListenerContainerFactory` (bean configurado em `KafkaConfig`) para criar um `KafkaMessageListenerContainer` — esse container:
   - Cria um `KafkaConsumer` real (client Kafka) usando as props do `ConsumerFactory` (bootstrap-servers, group-id, deserializers).
   - Assina (`subscribe`) o(s) tópico(s) configurado(s).
   - Inicia uma thread própria que fica em loop chamando `consumer.poll(...)`, entregando cada registro consumido para o método anotado (`onMessage`).
4. Ao entrar no grupo (`group-id=pedido-service`), o consumer participa do protocolo de **rebalanceamento** do Kafka: o *group coordinator* (um broker eleito) atribui partições aos consumers ativos daquele grupo.
5. Como não há offset commitado ainda (primeira vez que este group-id existe), `auto-offset-reset=earliest` faz o consumer começar a ler **do início** do tópico.
6. A partir daí, toda vez que uma mensagem chega no tópico `payment-order`, ela é entregue automaticamente ao método `onMessage`.

> ⚠️ **Isso só acontece se a aplicação estiver rodando.** Publicar uma mensagem no Kafka UI não "acorda" a aplicação — a mensagem simplesmente fica gravada no tópico esperando até que um consumer do grupo `pedido-service` esteja ativo para lê-la (respeitando `auto-offset-reset` na primeira conexão, ou a partir do último offset commitado nas conexões seguintes).

### 5.5 Idempotência e guarda de estado

```java
if (order.status() == null || order.status() != OrderStatus.RESERVED) {
    log.info("order {} not in RESERVED state (current={}), skipping processing", orderId, order.status());
    return Mono.empty();
}
```

- Antes de processar, o listener verifica se o pedido **ainda está** em `RESERVED`. Isso evita processar a mesma mensagem duas vezes (reprocessamento por causa de rebalance, retry do broker, replay manual, etc.) e evita aplicar a baixa de estoque mais de uma vez para o mesmo pedido.
- É uma forma simples de idempotência baseada no **estado atual do agregado** (Order), em vez de guardar uma tabela separada de "eventos já processados". Suficiente para o estágio atual do projeto; para um sistema em produção mais robusto, normalmente se guardaria também o `eventId` já processado.

---

## 6. Passo a passo: rodando e testando localmente

### 6.1 Subir a infraestrutura

```bash
docker compose --env-file .env up -d
docker compose --env-file .env ps   # confirme kafka/zookeeper/postgres/kafka-ui "healthy"/"Up"
```

### 6.2 Rodar a aplicação (profile `dev`)

```bash
./mvnw -DskipTests package
export SPRING_PROFILES_ACTIVE=dev
java -jar target/pedido-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

No log de startup, confirme:
```
[KafkaConfig] Building consumer factory bootstrapServers=localhost:9092 groupId=pedido-service autoOffsetReset=earliest
...
Started PedidoApplication in X seconds
[Kafka] listener id='...' topics=[payment-order] groupId=pedido-service running=true
```

### 6.3 Testar o producer (criar um pedido)

```bash
curl -X POST 'http://localhost:8080/api/orders' \
  -H 'Content-Type: application/json' \
  -d '{
    "orderId": "ORDER-100",
    "customerId": "CUSTOMER-123",
    "orderDate": "2024-06-15T10:30:00Z",
    "items": [{"productId": "SKU-00001", "quantity": 2, "price": 5.90}],
    "totalAmount": 11.80
  }'
```

Se o estoque validar/reservar com sucesso, você verá no log:
```
[KafkaPublisher] publishing event <uuid> to topic=order-payment partitionKey=ORDER-100
[KafkaPublisher] send future completed for event <uuid>
[KafkaPublisher] event <uuid> published
```

Confirme no Kafka UI (http://localhost:8085 → tópico `order-payment`) que a mensagem chegou.

### 6.4 Testar o consumer (simular resposta do serviço de pagamento)

Publique manualmente uma mensagem no tópico `payment-order` (via Kafka UI, usando a chave/partitionKey = `ORDER-100`, o mesmo `orderId` do pedido criado):

```json
{
  "eventId": "test-001",
  "eventType": "PaymentProcessed.v1",
  "eventVersion": "1",
  "occurredAt": 1783713891.25,
  "source": "pagamento-service",
  "correlationId": null,
  "partitionKey": "ORDER-100",
  "data": {
    "orderId": "ORDER-100",
    "paymentId": "PAY-123456",
    "status": "PAID",
    "processedAt": 1783713891.24,
    "amount": 11.80,
    "paymentMethod": "A_VISTA",
    "transactionId": "TRX-98765",
    "outcome": null
  }
}
```

Ou via terminal, usando o container do Kafka diretamente:

```bash
cat > /tmp/payment-test.json << 'EOF'
{"eventId":"test-001","eventType":"PaymentProcessed.v1","eventVersion":"1","occurredAt":1783713891.25,"source":"pagamento-service","correlationId":null,"partitionKey":"ORDER-100","data":{"orderId":"ORDER-100","paymentId":"PAY-123456","status":"PAID","processedAt":1783713891.24,"amount":11.80,"paymentMethod":"A_VISTA","transactionId":"TRX-98765","outcome":null}}
EOF
docker exec -i pedido-kafka bash -lc "kafka-console-producer --bootstrap-server localhost:9092 --topic payment-order" < /tmp/payment-test.json
```

No log da aplicação você deve ver (em segundos, sem precisar reiniciar nada):
```
[PaymentListener] received envelope eventId=test-001 type=PaymentProcessed.v1 partitionKey=ORDER-100
[PaymentListener] payment status for order=ORDER-100 status=PAID
[PaymentListener] order ORDER-100 completed and stock committed
```

### 6.5 Comandos úteis de diagnóstico (dentro do container Kafka)

```bash
# listar tópicos
docker exec pedido-kafka bash -lc "kafka-topics --bootstrap-server localhost:9092 --list"

# ver detalhes de um tópico (partições, líder, réplicas)
docker exec pedido-kafka bash -lc "kafka-topics --bootstrap-server localhost:9092 --describe --topic payment-order"

# listar consumer groups ativos/conhecidos
docker exec pedido-kafka bash -lc "kafka-consumer-groups --bootstrap-server localhost:9092 --list"

# ver offset/lag do nosso grupo (LAG=0 significa "tudo consumido")
docker exec pedido-kafka bash -lc "kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group pedido-service"

# ler mensagens de um tópico desde o início (apenas para depuração manual)
docker exec -it pedido-kafka bash -lc "kafka-console-consumer --bootstrap-server localhost:9092 --topic payment-order --from-beginning --max-messages 5"

# publicar uma mensagem manualmente via stdin
docker exec -i pedido-kafka bash -lc "kafka-console-producer --bootstrap-server localhost:9092 --topic payment-order" < arquivo.json
```

---

## 7. Problemas reais encontrados durante o desenvolvimento (e as soluções)

Esta seção documenta os erros reais enfrentados ao configurar Kafka neste projeto — úteis para reconhecer os mesmos sintomas no futuro.

### 7.1 `NodeExistsException` ao subir o Kafka

**Sintoma:**
```
ERROR Error while creating ephemeral at /brokers/ids/1, node already exists and owner '...' does not match current session '...'
```
**Causa:** o Zookeeper ainda tinha o registro (znode efêmero) de uma sessão anterior do broker, que não foi limpo corretamente (containers derrubados abruptamente, sem um "clean shutdown").

**Solução:** garantir volumes nomeados e persistentes para `zookeeper` e `kafka` (feito em `docker-compose.yml`), e, quando necessário, remover o container/volume conflitante e recriar.

### 7.2 `InconsistentBrokerIdException`

**Sintoma:**
```
kafka.common.InconsistentBrokerIdException: Configured broker.id 2 doesn't match stored broker.id Some(1) in meta.properties.
```
**Causa:** o `KAFKA_BROKER_ID` foi alterado no compose, mas o volume de dados (`/var/lib/kafka/data`) ainda tinha o `meta.properties` do broker antigo, gravado com o `broker.id` anterior.

**Solução:** manter o `broker.id` estável (voltamos para `1`) e, caso precise trocar, remover o volume `kafka_data` antes de subir novamente.

### 7.3 `BadSqlGrammarException` / H2 sendo usado em vez de Postgres

**Sintoma:** erros de SQL estranhos ao criar pedido, aparentemente usando um dialeto errado.
**Causa:** profile ativo incorreto — `local` usa H2 em memória (feito só para debug rápido sem Postgres), enquanto o esperado para desenvolvimento "real" era `dev` (Postgres).
**Solução:** documentar claramente a diferença entre os profiles:
- `local` → H2 em memória (debug rápido, sem infraestrutura).
- `dev` → Postgres local + Kafka local (fluxo completo).
- `prod` → Postgres/Kafka reais de produção.

### 7.4 `ApplicationFailedToStart`: `ObjectMapper` bean não encontrado

**Sintoma:**
```
Parameter 0 of constructor in ...PaymentProcessedListener required a bean of type 'com.fasterxml.jackson.databind.ObjectMapper' that could not be found.
```
**Causa:** nenhuma configuração explícita de `ObjectMapper` existia no projeto.
**Solução:** criado `JacksonConfig` (seção 3.5) com um bean `@Primary` de `ObjectMapper`.

### 7.5 Listener não processava mensagens publicadas manualmente pelo Kafka UI

**Sintoma:** mensagem publicada no tópico `payment-order`, mas nada acontecia — nem um log aparecia.
**Causa raiz:** **a aplicação simplesmente não estava rodando** no momento da publicação (nenhum consumer group ativo). Confirmado rodando `kafka-consumer-groups --list` no broker: nenhum grupo listado.
**Solução / boas práticas aplicadas:**
1. `KafkaListenerStartupLogger` (seção 3.6) — para confirmar visualmente, todo startup, que o listener está `running=true`.
2. `groupId` do `@KafkaListener` com valor default (`pedido-service`) — para o listener nunca falhar ao registrar por falta da propriedade em algum profile.
3. `ConsumerFactory`/`ConcurrentKafkaListenerContainerFactory` explícitos em `KafkaConfig` — deixando o "registro como consumer" 100% garantido e visível no código, sem depender de mágica de autoconfiguração.
4. Teste real feito: com a aplicação rodando, publicamos uma mensagem nova via `kafka-console-producer` e, em ~1 segundo, o log confirmou o consumo (`[PaymentListener] received envelope ...`), e `kafka-consumer-groups --describe --group pedido-service` mostrou `LAG=0`.

**Lição para quem está aprendendo:** Kafka é *durável* (a mensagem fica no tópico esperando), mas **não é mágico** — só existe consumo se houver, no momento em questão (ou depois), um processo com um consumer daquele group-id, com o listener realmente rodando, conectado ao broker certo.

---

## 8. Glossário rápido

| Termo | Significado |
|---|---|
| **Broker** | Um servidor Kafka (processo) que armazena e serve mensagens. |
| **Tópico (topic)** | Canal nomeado onde mensagens são publicadas/consumidas (ex.: `order-payment`). |
| **Partição** | Subdivisão de um tópico; permite paralelismo e ordena mensagens dentro dela. |
| **Chave (key)** | Valor usado para decidir em qual partição uma mensagem cai (mesma chave → mesma partição). |
| **Producer** | Cliente que publica (envia) mensagens para um tópico. |
| **Consumer** | Cliente que lê (consome) mensagens de um tópico. |
| **Consumer Group** | Conjunto de consumers que dividem entre si as partições de um tópico (balanceamento de carga). |
| **Offset** | Posição/índice de uma mensagem dentro de uma partição. |
| **Committed offset** | Offset até onde um consumer group já confirmou ter processado. |
| **Lag** | Diferença entre o offset mais recente do tópico e o offset commitado do grupo — mensagens ainda não processadas. |
| **`auto-offset-reset`** | O que fazer quando não há offset commitado: `earliest` (do início) ou `latest` (só novas). |
| **Zookeeper** | Serviço de coordenação usado pelo Kafka (nesta versão) para eleição de controller e metadados do cluster. |
| **`@KafkaListener`** | Anotação do Spring Kafka que transforma um método em um consumer de um ou mais tópicos. |
| **`@EnableKafka`** | Habilita o processamento das anotações `@KafkaListener` no contexto Spring. |
| **`KafkaTemplate`** | Client de alto nível do Spring Kafka para publicar mensagens (producer). |
| **Envelope de evento** | Estrutura genérica (metadados + payload) usada para padronizar/versionar eventos publicados. |
| **Best-effort publish** | Estratégia onde a falha ao publicar um evento não interrompe/reverte a operação de negócio principal. |

---

## 9. Arquivos-chave para consulta rápida

| Camada | Arquivo |
|---|---|
| Infra (docker) | [`docker-compose.yml`](./docker-compose.yml) |
| Config Spring Kafka | [`src/main/java/br/com/pedido/config/KafkaConfig.java`](./src/main/java/br/com/pedido/config/KafkaConfig.java) |
| Observabilidade do consumer | [`src/main/java/br/com/pedido/config/KafkaListenerStartupLogger.java`](./src/main/java/br/com/pedido/config/KafkaListenerStartupLogger.java) |
| ObjectMapper | [`src/main/java/br/com/pedido/config/JacksonConfig.java`](./src/main/java/br/com/pedido/config/JacksonConfig.java) |
| Producer (implementação) | [`src/main/java/br/com/pedido/order/adapter/out/kafka/KafkaOrderEventPublisher.java`](./src/main/java/br/com/pedido/order/adapter/out/kafka/KafkaOrderEventPublisher.java) |
| Producer (fallback no-op) | [`src/main/java/br/com/pedido/order/adapter/out/kafka/NoOpOrderEventPublisher.java`](./src/main/java/br/com/pedido/order/adapter/out/kafka/NoOpOrderEventPublisher.java) |
| Producer (inspetor de bean ativo) | [`src/main/java/br/com/pedido/order/adapter/out/kafka/OrderEventPublisherInspector.java`](./src/main/java/br/com/pedido/order/adapter/out/kafka/OrderEventPublisherInspector.java) |
| Onde o evento é montado | [`src/main/java/br/com/pedido/order/application/service/strategy/DefaultOrderReservationStrategy.java`](./src/main/java/br/com/pedido/order/application/service/strategy/DefaultOrderReservationStrategy.java) |
| Consumer (listener) | [`src/main/java/br/com/pedido/order/adapter/in/kafka/PaymentProcessedListener.java`](./src/main/java/br/com/pedido/order/adapter/in/kafka/PaymentProcessedListener.java) |
| Contratos de evento (DTOs) | `src/main/java/br/com/pedido/order/adapter/out/kafka/dto/*.java` |
| Propriedades (dev) | [`src/main/resources/application-dev.properties`](./src/main/resources/application-dev.properties) |
| Variáveis de ambiente | [`.env`](./.env) |

---

## 10. Próximos passos possíveis (para continuar aprendendo)

Ideias de evolução, caso queira se aprofundar mais neste projeto:

1. **Múltiplas partições** para `order-payment`/`payment-order` e testar concorrência real entre consumers (`concurrency` na `ConcurrentKafkaListenerContainerFactory`).
2. **Dead Letter Topic (DLT)** — configurar um `DefaultErrorHandler` com `DeadLetterPublishingRecoverer` para mensagens que falham repetidamente no consumer.
3. **Idempotência mais robusta** — persistir `eventId`s já processados (tabela `processed_events`) em vez de depender só do status atual do pedido.
4. **Testes automatizados de integração** com Testcontainers (subir um Kafka efêmero durante os testes).
5. **Producer com `acks=all` e idempotência habilitada** (`enable.idempotence=true`) para garantir exactly-once no envio.
6. **Schema Registry** (Avro/Protobuf) em vez de JSON solto, para validação de contrato mais forte entre produtor e consumidor.

