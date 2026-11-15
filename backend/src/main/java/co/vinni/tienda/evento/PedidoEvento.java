package co.vinni.tienda.evento;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Evento de dominio: algo que YA ocurrió con un pedido. Es el "contrato" que viaja por Kafka
 * (hacia el lado de consultas) y por RabbitMQ (hacia notificaciones y clientes).
 *
 * @param eventId    identificador único (sirve para no aplicar dos veces el mismo evento)
 * @param tipo       PEDIDO_CREADO | PEDIDO_PAGADO | PEDIDO_ENVIADO | PEDIDO_CANCELADO
 * @param pedidoId   pedido al que pertenece (también es la clave del mensaje en Kafka)
 * @param estado     estado del pedido DESPUÉS del evento
 */
public record PedidoEvento(String eventId, String tipo, String pedidoId, String cliente,
                           BigDecimal total, String estado, List<ItemEvento> items, Instant ocurridoEn) {

    public static final String CREADO = "PEDIDO_CREADO";
    public static final String PAGADO = "PEDIDO_PAGADO";
    public static final String ENVIADO = "PEDIDO_ENVIADO";
    public static final String CANCELADO = "PEDIDO_CANCELADO";

    /** PEDIDO_PAGADO -> pedido.pagado (routing key de RabbitMQ). */
    public String routingKey() {
        return tipo.toLowerCase().replace('_', '.');
    }
}
