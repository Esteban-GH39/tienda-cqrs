# API

Base: `http://localhost:8080`

## Comandos (escriben) – responden `202 Accepted`

| Método | Ruta | Cuerpo | Efecto |
|---|---|---|---|
| POST | `/api/comandos/pedidos` | `{ "cliente": "Ana", "items": [ { "producto": "Mouse", "cantidad": 2, "precioUnitario": 25 } ] }` | Crea el pedido (CREADO) |
| POST | `/api/comandos/pedidos/{id}/pagar` | (vacío) | CREADO → PAGADO |
| POST | `/api/comandos/pedidos/{id}/enviar` | (vacío) | PAGADO → ENVIADO |
| POST | `/api/comandos/pedidos/{id}/cancelar` | (vacío) | CREADO o PAGADO → CANCELADO |

Respuesta: `{ "pedidoId": "…", "estado": "CREADO", "mensaje": "…" }`

## Consultas (leen)

| Método | Ruta | Parámetros | Devuelve |
|---|---|---|---|
| GET | `/api/consultas/pedidos` | `estado`, `cliente` (opcionales) | Lista (máx. 200, más recientes primero) |
| GET | `/api/consultas/pedidos/{id}` | – | Un pedido o `404` |
| GET | `/api/consultas/resumen` | – | Conteo por estado |

## Errores

| Código | Cuándo | Ejemplo |
|---|---|---|
| 400 | Datos inválidos | `{ "error": "items: no debe estar vacío" }` |
| 404 | Pedido inexistente | `{ "error": "No existe el pedido …" }` |
| 409 | Regla de negocio | `{ "error": "Solo se puede enviar un pedido PAGADO (estado actual: CREADO)" }` |

## WebSocket

| Dato | Valor |
|---|---|
| URL | `ws://localhost:8080/ws` (STOMP) |
| Suscripción | `/topic/pedidos` |
| Mensaje | `NotificacionPedido` ([ver formato](Broadcast-y-WebSocket)) |

## Ejemplo con curl

```
curl -X POST http://localhost:8080/api/comandos/pedidos -H "Content-Type: application/json" -d "{\"cliente\":\"Ana\",\"items\":[{\"producto\":\"Mouse\",\"cantidad\":2,\"precioUnitario\":25}]}"
```
