package co.vinni.tienda.mensajeria;

import co.vinni.tienda.evento.PedidoEvento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumidores de las colas de trabajo (como los de ventas/xpesos del proyecto NBA).
 * Aquí solo escriben en el log lo que harían; en un sistema real llamarían a facturación, bodega, etc.
 */
@Component
public class TrabajadoresRabbit {

    private static final Logger log = LoggerFactory.getLogger(TrabajadoresRabbit.class);

    @RabbitListener(queues = MensajeriaConfig.Q_FACTURACION)
    public void facturacion(PedidoEvento e) {
        log.info("[FACTURACIÓN] generar factura del pedido {} por ${} ({})", e.pedidoId(), e.total(), e.cliente());
    }

    @RabbitListener(queues = MensajeriaConfig.Q_INVENTARIO)
    public void inventario(PedidoEvento e) {
        String accion = PedidoEvento.CANCELADO.equals(e.tipo()) ? "devolver al stock" : "reservar en stock";
        log.info("[INVENTARIO] {} los {} ítems del pedido {}", accion, e.items().size(), e.pedidoId());
    }

    @RabbitListener(queues = MensajeriaConfig.Q_LOGISTICA)
    public void logistica(PedidoEvento e) {
        log.info("[LOGÍSTICA] despachar el pedido {} para {}", e.pedidoId(), e.cliente());
    }
}
