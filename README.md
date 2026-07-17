# Pedido Service

Microservico reativo para recebimento de pedidos e reserva de estoque em outro servico.

## Stack

- Java 17
- Spring Boot WebFlux
- R2DBC (H2 local, PostgreSQL producao)
- Flyway
- Docker / Docker Compose

## Endpoints

- `POST /api/orders` - recebe pedido e tenta reservar estoque
- `GET /api/orders?status=PENDING&orderId=ORDER-001` - consulta pedidos com filtros opcionais

## Rodando local (H2)

```bash
./mvnw spring-boot:run
```

## Rodando com Docker Compose (PostgreSQL)

```bash
docker compose up --build
```

## Exemplo de payload

```json
{
  "orderId": "ORDER-001",
  "customerId": "CUSTOMER-123",
  "orderDate": "2024-06-15T10:30:00Z",
  "items": [
    {
      "productId": "SKU-001",
      "quantity": 2,
      "price": 5.90
    }
  ],
  "totalAmount": 11.80
}
```

