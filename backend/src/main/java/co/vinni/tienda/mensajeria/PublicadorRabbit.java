package co.vinni.tienda.mensajeria;

import co.vinni.tienda.evento.PedidoEvento;
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

    public PublicadorRabbit(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    public void publicar(PedidoEvento evento) {
        try {
            rabbit.convertAndSend(MensajeriaConfig.EX_PEDIDOS, evento.routingKey(), evento);
            rabbit.convertAndSend(MensajeriaConfig.EX_BROADCAST, "", evento);
            log.info("[RABBIT] publicado {} en ex.pedidos ({}) y ex.broadcast", evento.tipo(), evento.routingKey());
        } catch (Exception e) {
            // La vista ya se actualizó; solo falló el aviso. Los clientes se ponen al día al refrescar.
            log.error("[RABBIT] no se pudo publicar {}: {}", evento.tipo(), e.getMessage());
        }
    }
}
