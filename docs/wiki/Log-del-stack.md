# Log del stack en vivo

La pestaña **Log del stack** (en la Tienda y en el Panel) muestra, en tiempo real, **el momento exacto en que se usa cada herramienta** al procesar un pedido.

## Qué se ve

- Una **franja de siete herramientas** en el mismo orden del diagrama de arquitectura: Angular, Spring Boot, PostgreSQL, Kafka, MongoDB, RabbitMQ y WebSocket. Cada una se ilumina cuando se usa y lleva un contador.
- Una **lista de líneas** (la más nueva arriba) con hora en milisegundos, herramienta, qué pasó y el detalle técnico.
- Filtros por herramienta, pausa, limpiar, y un filtro por pedido (clic en su código).

## Cómo funciona

1. Cada clase del servidor llama a `TrazaServicio.registrar(...)` cuando usa su herramienta.
2. Se guarda en memoria (últimas 300) y se emite por WebSocket a `/topic/traza`.
3. Al abrir la pestaña, el historial se carga con `GET /api/traza`.
4. Las líneas de Angular las genera el navegador (comando enviado, broadcast recibido).
5. Las de PostgreSQL se emiten después del commit.

## Un pedido, línea por línea

| Paso | Herramienta | Qué dice el log |
|---|---|---|
| 1 | Angular | Comando enviado: crear pedido |
| 2 | Spring Boot | Comando recibido: crear pedido |
| 3 | PostgreSQL | Pedido guardado + Evento guardado en el outbox |
| 4 | Kafka | Evento publicado en Kafka (partición y offset) |
| 5 | Kafka | Evento recibido por el proyector |
| 6 | MongoDB | Vista actualizada |
| 7 | RabbitMQ | Publicado en ex.pedidos (routing key y cola) y en ex.broadcast |
| 8 | RabbitMQ | La cola entregó el mensaje (inventario, facturación o logística) |
| 9 | WebSocket | Broadcast enviado a todos los clientes |
| 10 | Angular | Broadcast recibido en este navegador |

Más detalle de cada herramienta, tópicos, colas y routing keys en el [README](https://github.com/Esteban-GH39/tienda-cqrs#readme).
