package co.vinni.tienda.mensajeria;

import co.vinni.tienda.evento.PedidoEvento;
import co.vinni.tienda.traza.TrazaServicio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica cada evento en RabbitMQ DOS veces, como hicimos en el proyecto NBA:
 *  - ex.pedidos   (topic)  con routing key pedido.creado / pedido.pagado ... -> colas de trabajo
 *  - ex.broadcast (fanout) sin routing key                                   -> todos los servidores/clientes
 */
@Component
public class PublicadorRabbit {

    private static final Logger log = LoggerFactory.getLogger(PublicadorRabbit.class);

    private final RabbitTemplate rabbit;
    private final TrazaServicio traza;

    public PublicadorRabbit(RabbitTemplate rabbit, TrazaServicio traza) {
        this.rabbit = rabbit;
        this.traza = traza;
    }

    /** Qué cola recibe cada routing key (las reglas de los bindings de MensajeriaConfig). */
    static String colaDe(String routingKey) {
        return switch (routingKey) {
            case "pedido.creado", "pedido.cancelado" -> MensajeriaConfig.Q_INVENTARIO;
            case "pedido.pagado" -> MensajeriaConfig.Q_FACTURACION;
            case "pedido.enviado" -> MensajeriaConfig.Q_LOGISTICA;
            default -> "(ninguna cola)";
        };
    }

    public void publicar(PedidoEvento evento) {
        try {
            rabbit.convertAndSend(MensajeriaConfig.EX_PEDIDOS, evento.routingKey(), evento);
            traza.registrar(TrazaServicio.RABBITMQ, "Publicado en ex.pedidos (topic)",
                    "routing key " + evento.routingKey() + " → " + colaDe(evento.routingKey()), evento.pedidoId());
            rabbit.convertAndSend(MensajeriaConfig.EX_BROADCAST, "", evento);
            traza.registrar(TrazaServicio.RABBITMQ, "Publicado en ex.broadcast (fanout)",
                    "una copia para cada servidor conectado → WebSocket", evento.pedidoId());
            log.info("[RABBIT] publicado {} en ex.pedidos ({}) y ex.broadcast", evento.tipo(), evento.routingKey());
        } catch (Exception e) {
            // La vista ya se actualizó; solo falló el aviso. Los clientes se ponen al día al refrescar.
            log.error("[RABBIT] no se pudo publicar {}: {}", evento.tipo(), e.getMessage());
        }
    }
}
