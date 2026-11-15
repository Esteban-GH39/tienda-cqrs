# Kafka

## Papel en el proyecto

Kafka es el **puente de CQRS**: transporta los eventos desde el lado de comandos hacia el lado de consultas.

| Dato | Valor |
|---|---|
| Tópico | `pedidos.eventos` (3 particiones, 1 réplica) |
| Clave del mensaje | id del pedido |
| Valor | `PedidoEvento` en JSON |
| Productor | `OutboxPublicador` (cada segundo, con `acks=all`) |
| Consumidor | `ProyectorKafka`, grupo `proyector-pedidos` |

## Por qué Kafka aquí

- Es un **registro duradero**: los eventos se quedan guardados y se pueden **reproducir** desde el inicio para reconstruir la vista de consultas.
- Mantiene el **orden por clave**: todos los eventos de un pedido van a la misma partición.
- Permite **escalar** el lado de consultas con más consumidores del mismo grupo.

## Ver los mensajes

Con el contenedor del `docker-compose.yml`:

```
docker exec -it kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic pedidos.eventos --from-beginning
```

## Reconstruir la vista

Para reconstruir la vista, se vacían las colecciones `pedido_vista` y `evento_aplicado` de MongoDB (`db.pedido_vista.drop()` y `db.evento_aplicado.drop()` en `mongosh tienda`) y se reinicia el consumidor con un grupo nuevo (por ejemplo, `proyector-pedidos-2`). Leerá todo el tópico desde el principio.
