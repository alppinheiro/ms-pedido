# Projeto para receber pedidos de produtos e gerar reserva no estoque em outro microserviço

Esse microserviço será desenvolvido em java 17 e utilizará o framework spring boot. Será desenvolvido com arquitetura
hexagonal utilizando as melhores praticas de desenvolvimento de software, como SOLID, Clean Code e Testes Automatizados.
- Utilizar design patterns como Strategy, Factory, Adapter, Observer, etc.
- Não utilizar a anotação @Autowired para injeção de dependência, utilizar construtor para injeção de dependência.
- Criar dtos para entrada e saída de dados, não utilizar entidades para entrada e saída de dados.
- Utilizar mappers para conversão de dtos para entidades e vice-versa.
- Deixar o dominio do microserviço desacoplado do framework spring boot, ou seja, o dominio não deve ter nenhuma dependência do spring boot.
- Endpoints assincronos utilizando webflux
- Banco de dados H2 para ambiente local e testes, e PostgreSQL r2dbc para ambiente de produção
- Flyway para versionamento do banco de dados
- Utilizar docker e docker-compose para orquestração dos microserviços e banco de dados
- .env para variáveis de ambiente

# Regra de negócio
O microserviço será responsável por receber pedidos de produtos e gerar reserva no estoque em outro microserviço. Então teremos os endpoints:
- Receber pedido de produto
- Consultar pedidos recebidos

# Outro microserviço de estoque
Documentação: http://localhost:8081/swagger-ui/index.html#/
Endpoint de reserva: POST -> /api/products/{productId}/stock/reservations
Payload:
```json
{
  "quantity": 10
}
```
Endpoint de baixa: POST -> /api/products/{productId}/stock/inbound
Payload:
```json 
{
  "quantity": 10
}
```

Endpoint de consulta de saldo: GET -> /api/products/{productId}/stock?requestedQuantity={n}


Payload de exemplo para receber pedido de produto:
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
    },
    {
      "productId": "SKU-002",
      "quantity": 1,
      "price": 12.50
    }
  ],
  "totalAmount": 24.30,
  "status": "PENDING"
}
``` 
# Regra de negócio para receber pedido de produto
Ao receber um pedido de produto, o microserviço deverá validar se existe saldo suficiente no estoque do produto, caso exista, deverá gerar uma reserva no estoque do outro microserviço. 
Caso não exista saldo suficiente no estoque, deverá retornar uma mensagem de erro informando que não existe saldo suficiente no estoque. 
O microserviço deverá registrar o pedido recebido no banco de dados, com status "PENDING". 
Caso a reserva seja gerada com sucesso, o status do pedido deverá ser atualizado para "RESERVED". 
Caso a reserva não seja gerada com sucesso, o status do pedido deverá ser atualizado para "FAILED".
O microserviço não poderá receber duas vezes o mesmo pedido, ou seja, o orderId deverá ser único.
O microserviço deverá expor um endpoint para consultar os pedidos recebidos, com possibilidade de filtrar por status do pedido e orderId. 

    
# Continuação do desenvolvimento do fluxo de pedido de produto
Após a reserva ser gerada com sucesso, devemos publicar um payload com type específico em uma fila utilizando apache kafka,
para que outro microserviço de pagamento possa consumir e processar o pagamento do pedido.
Após o pagamento ser processado o microserviço de pagamento deverá publicar um payload com type especifico em uma fila utilizando apache kafka, 
para que o microserviço de pedido possa consumir e processar a baixa do estoque do produto chamando a baixa do endpoint de estoque.

# Endpoint de baixa do estoque do produto
```
postman request POST 'http://localhost:8081/api/products/{sku}/stock/outbound' \
--header 'Content-Type: application/json' \
--body '{
"quantity": 1 -> quantidade do produto que foi pago e deve ser dado baixa no estoque
}'
```

# Apache kafka
Adicionar o apache kafka e apache kafka UI no docker-compose
Criar as fila para o microserviço de pedido enviar dados para o pagamento e para
o microserviço de pagamento enviar dados para o microserviço de pedido processar a baixa do estoque




Etapa 1 - Infra Kafka e ambientes
Atualizar docker-compose.yml com Kafka e Kafka UI.
Definir variáveis .env e propriedades (bootstrap-servers, nomes dos tópicos).
Entregável: stack sobe e tópicos visíveis no Kafka UI.
Etapa 2 - Contratos de evento
Definir payloads versionados:
OrderReservedEvent (pedido -> pagamento)
PaymentProcessedEvent (pagamento -> pedido)
Definir chaves de partição (sugestão: orderId) e eventType.
Entregável: contrato documentado e pronto para producer/consumer.
Etapa 3 - Producer no fluxo de reserva
Criar porta de saída no domínio (hexagonal) para publicação de evento.
Implementar adapter Kafka producer.
Publicar evento logo após reserva com sucesso (status RESERVED).
Entregável: pedido reservado gera mensagem no tópico de pagamento.
Etapa 4 - Consumer de pagamento + baixa no estoque
Criar adapter Kafka consumer para PaymentProcessedEvent.
Criar serviço de aplicação para processar evento e chamar endpoint outbound.
Atualizar status do pedido após baixa (sugestão: COMPLETED ou PAID_AND_STOCK_DEBITED).
Entregável: evento de pagamento aprovado dispara baixa e atualização de status.
Etapa 5 - Confiabilidade e idempotência
Evitar reprocessamento de evento (idempotência por eventId/orderId+type).
Estratégia de retry + DLT para falhas de consumo.
Circuit breaker também no client de baixa de estoque.
Entregável: fluxo robusto contra duplicidade/falha transitória.
Etapa 6 - Observabilidade e documentação
Logs estruturados por orderId/eventId.
Endpoint/monitor para saúde de consumidores e lag básico.
Atualizar README/Postman com fluxo assíncrono ponta a ponta.
Entregável: operação e troubleshooting facilitados.