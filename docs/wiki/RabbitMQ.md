# RabbitMQ

Es la misma idea del proyecto NBA (exchanges, colas, bindings y routing keys), aplicada a pedidos. Spring crea todo al arrancar (`MensajeriaConfig`).

## Exchanges y colas

| Exchange | Tipo | Cola | Binding (routing key) |
|---|---|---|---|
| `ex.pedidos` | topic | `q.inventario` | `pedido.creado` y `pedido.cancelado` |
| `ex.pedidos` | topic | `q.facturacion` | `pedido.pagado` |
| `ex.pedidos` | topic | `q.logistica` | `pedido.enviado` |
| `ex.broadcast` | fanout | cola anónima de cada instancia | (sin clave) |

## Qué publica el servidor

Por cada evento, `PublicadorRabbit` publica **dos veces**:

| Evento | A `ex.pedidos` con la clave | A `ex.broadcast` |
|---|---|---|
| PEDIDO_CREADO | `pedido.creado` | sí |
| PEDIDO_PAGADO | `pedido.pagado` | sí |
| PEDIDO_ENVIADO | `pedido.enviado` | sí |
| PEDIDO_CANCELADO | `pedido.cancelado` | sí |

## Quién consume

| Cola | Consumidor | Qué hace (simulado con un log) |
|---|---|---|
| `q.inventario` | `TrabajadoresRabbit.inventario` | Reserva el stock; si es cancelación, lo devuelve |
| `q.facturacion` | `TrabajadoresRabbit.facturacion` | Genera la factura |
| `q.logistica` | `TrabajadoresRabbit.logistica` | Despacha el pedido |
| cola anónima | `BroadcastWebSocket` | Envía el evento a los clientes por WebSocket |

## Verlo en el panel

En http://localhost:15672 (guest / guest): pestaña **Exchanges** → `ex.pedidos` y `ex.broadcast` → sección *Bindings*. Los consumidores vacían las colas de inmediato; para ver mensajes acumulados, detén el servidor, crea pedidos con otra herramienta o publica a mano desde *Publish message*.
