package co.vinni.tienda.consulta;

import java.time.Instant;
import java.util.Map;

/** Mensaje que reciben TODOS los clientes conectados por WebSocket (broadcast). */
public record NotificacionPedido(String tipo, String eventId, Instant ocurridoEn,
                                 PedidoVista pedido, Map<String, Long> resumen) {
}
