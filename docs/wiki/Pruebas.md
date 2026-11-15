# Pruebas

## Automáticas (servidor)

`mvn test` en `backend/`. Usan **H2 en modo PostgreSQL** para el lado de comandos y un **almacén en memoria** para el de consultas. No necesitan Kafka, RabbitMQ ni MongoDB.

| Clase | Qué comprueba |
|---|---|
| `PedidoComandoServicioTest` | Crear guarda pedido y evento; el flujo completo genera un evento por cambio; no se envía sin pagar; no se cancela uno enviado; pedido inexistente |
| `OutboxPublicadorTest` | Publica en orden y marca como publicado; si Kafka falla, el evento queda pendiente |
| `ProyeccionServicioTest` | Crea y actualiza la vista; un evento repetido se ignora; reprocesar un `PEDIDO_CREADO` no pisa un estado más nuevo; resumen y filtros |

GitHub Actions ejecuta estas pruebas y compila los dos clientes en cada `push` (`.github/workflows/ci.yml`).

## Demostración manual (todo encendido)

| # | Acción | Debe verse |
|---|---|---|
| 1 | Abrir la Tienda en dos pestañas y el Panel | Los tres con "Conectado en vivo" |
| 2 | En la Tienda: nombre `Ana`, agregar productos, **Hacer pedido** | Aparece en Mis pedidos de ambas pestañas, en el Panel y en "En vivo" |
| 3 | Log del servidor | `[KAFKA] publicado`, `[PROYECTOR] aplicado`, `[RABBIT] publicado`, `[INVENTARIO] reservar`, `[WEBSOCKET] broadcast` |
| 4 | En el Panel: **Marcar pagado** | `[FACTURACIÓN]` en el log; `PAGADO` en todas las pantallas |
| 5 | En el Panel: **Enviar** | `[LOGÍSTICA]` en el log; `ENVIADO` en todas las pantallas |
| 6 | Intentar enviar un pedido CREADO con `curl` | Respuesta `409` |
| 7 | Detener Kafka (`docker stop kafka`), crear un pedido, volver a iniciarlo (`docker start kafka`) | El pedido aparece cuando Kafka vuelve |
| 8 | Supabase → Table Editor (esquema `comando`) | Datos en `pedido`, `pedido_item` y `outbox_evento` |
| 8b | `docker exec -it mongo mongosh tienda --eval "db.pedido_vista.find()"` | Los pedidos como documentos, con sus ítems adentro |
| 9 | Panel de RabbitMQ → Exchanges | `ex.pedidos` y `ex.broadcast` con sus enlaces |

## Limitación conocida

Las pruebas automáticas no levantan los brokers ni MongoDB. La integración real con Kafka, RabbitMQ y MongoDB se verifica con la demostración manual.
