# Broadcast y WebSocket

## Qué es el broadcast aquí

Un mensaje enviado **una sola vez** por el servidor llega a **todos** los clientes conectados. Así, si una persona compra en la Tienda, el Panel y las demás pestañas lo ven al instante.

## Dos saltos

| Salto | Tecnología | Para qué |
|---|---|---|
| Entre servidores | RabbitMQ `fanout` (`ex.broadcast`) | Que **todas las instancias** del servidor se enteren de cada evento |
| Hacia los navegadores | WebSocket + STOMP | Que cada servidor avise a **sus** clientes conectados |

## Servidor

- `WebSocketConfig`: endpoint `ws://localhost:8080/ws`, broker simple en `/topic`.
- `BroadcastWebSocket`: escucha su cola del fanout y envía una `NotificacionPedido` a `/topic/pedidos`.

## Mensaje que reciben los clientes

```json
{
  "tipo": "PEDIDO_PAGADO",
  "eventId": "…",
  "ocurridoEn": "2026-11-12T23:10:00Z",
  "pedido": { "id": "…", "cliente": "Ana", "total": 1285.00, "estado": "PAGADO", "items": [] },
  "resumen": { "CREADO": 1, "PAGADO": 1, "ENVIADO": 0, "CANCELADO": 0 }
}
```

## Clientes

`frontend/projects/shared/src/tiempo-real.service.ts` abre la conexión con `@stomp/stompjs`, se suscribe a `/topic/pedidos`, guarda los 15 eventos más recientes y se **reconecta solo** cada 3 segundos si se cae. La Tienda y el Panel usan el mismo servicio.
