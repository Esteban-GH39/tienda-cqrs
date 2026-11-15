# CQRS

**CQRS** (*Command Query Responsibility Segregation*) separa lo que **cambia** datos (comandos) de lo que los **lee** (consultas), cada uno con su propio modelo.

## Cómo se aplicó

| | Comandos (escribir) | Consultas (leer) |
|---|---|---|
| Paquete | `comando` | `consulta` |
| API | `POST /api/comandos/pedidos...` | `GET /api/consultas/...` |
| **Motor de base de datos** | **PostgreSQL** (Supabase) | **MongoDB** |
| Dónde | Esquema `comando` | Base `tienda` |
| Tablas / colecciones | `pedido`, `pedido_item`, `outbox_evento` | `pedido_vista`, `evento_aplicado` |
| Acceso a datos | JPA (entidades con reglas) | Spring Data MongoDB (`MongoTemplate`) |
| Forma de los datos | Normalizada (pedido + ítems en tablas) | Documento (pedido con sus ítems adentro) |
| Quién lo alimenta | El comando del usuario | El proyector, a partir de eventos de Kafka |

## Reglas de negocio (solo en el lado de comandos)

| Comando | Permitido si el estado es | Pasa a |
|---|---|---|
| Pagar | CREADO | PAGADO |
| Enviar | PAGADO | ENVIADO |
| Cancelar | CREADO o PAGADO | CANCELADO |

Cualquier otro caso responde `409` con un mensaje.

## Qué mantiene unidos los dos lados

Solo los **eventos** (`PedidoEvento`). El lado de consultas nunca lee las tablas de comando y el de comandos nunca lee la vista. Como son **dos motores distintos**, no pueden compartir una transacción: por eso existen el outbox y el proyector idempotente.

## Costos y cómo se manejan

| Costo | Cómo se maneja |
|---|---|
| La vista tarda un instante en actualizarse | `202 Accepted` y broadcast para refrescar |
| Pérdida de eventos | Patrón outbox con reintentos |
| Eventos repetidos | Proyector idempotente (colección `evento_aplicado`) |
| Sin transacción entre las dos bases | Cada paso del proyector se puede repetir sin dañar nada |
| Orden de los eventos | Clave de Kafka = id del pedido |
