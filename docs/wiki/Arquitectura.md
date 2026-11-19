# Arquitectura

## Piezas

| Pieza | Tecnología | Puerto | Responsabilidad |
|---|---|---|---|
| Tienda | Angular | 4200 | El cliente final arma pedidos y ve los suyos |
| Panel de administración | Angular | 4201 | Ve todos los pedidos, cobra, envía y cancela |
| Servidor | Spring Boot | 8080 | API REST, mensajería y WebSocket |
| Kafka | Apache Kafka | 9092 | Eventos entre comandos y consultas |
| RabbitMQ | RabbitMQ | 5672 / 15672 | Enrutamiento y broadcast |
| Base de comandos | PostgreSQL (Supabase) | 5432 | Pedidos, ítems y outbox (escritura) |
| Base de consultas | MongoDB | 27017 | Documentos `pedido_vista` (lectura) |

## Paquetes del servidor

| Paquete | Qué contiene |
|---|---|
| `comando` | Pedido (reglas de negocio), servicio de comandos, outbox, publicador a Kafka, controlador `POST` |
| `consulta` | Vista de lectura (documento MongoDB), proyector (Kafka → MongoDB), controlador `GET` |
| `mensajeria` | Configuración de Kafka y RabbitMQ, publicador, trabajadores y broadcast |
| `evento` | `PedidoEvento`: el contrato de los mensajes |
| `config` | CORS, WebSocket y manejo de errores |

## Recorrido de un pedido

| Paso | Qué ocurre | Dónde |
|---|---|---|
| 1 | El cliente envía el comando | `PedidoComandoController` |
| 2 | Se guarda el pedido y su evento en una sola transacción | `PedidoComandoServicio` |
| 3 | Un proceso publica el evento en Kafka, con el id del pedido como clave | `OutboxPublicador` |
| 4 | El proyector lo consume y actualiza la vista | `ProyectorKafka` + `ProyeccionServicio` |
| 5 | Se publica en RabbitMQ: `ex.pedidos` (topic) y `ex.broadcast` (fanout) | `PublicadorRabbit` |
| 6 | Las colas de trabajo reaccionan (inventario, facturación, logística) | `TrabajadoresRabbit` |
| 7 | La copia del fanout se envía por WebSocket a todos los clientes | `BroadcastWebSocket` |
| 8 | Los clientes se actualizan solos | `tiempo-real.service.ts` |

## Por qué el comando responde 202

La vista se actualiza después de que el evento pasa por Kafka. El comando ya quedó guardado, pero la lectura puede tardar un instante (*consistencia eventual*). Por eso el servidor responde `202 Accepted` y los clientes esperan el broadcast para refrescar.

Para ver este recorrido en vivo, abre la pestaña **Log del stack** (ver [Log del stack en vivo](Log-del-stack)).

Detalle de cada tema y por qué se usa: `docs/REQUERIMIENTOS.md` §4 del repositorio.
