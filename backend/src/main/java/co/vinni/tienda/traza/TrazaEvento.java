package co.vinni.tienda.traza;

import java.time.Instant;

/**
 * Una línea del LOG DEL STACK: "en este momento se usó esta herramienta para esto".
 * herramienta: SPRING | POSTGRES | KAFKA | MONGODB | RABBITMQ | WEBSOCKET
 */
public record TrazaEvento(long seq, Instant ts, String herramienta, String accion, String detalle, String pedidoId) {
}
