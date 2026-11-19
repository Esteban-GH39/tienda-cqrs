package co.vinni.tienda.mensajeria;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Declara TODO lo de mensajería al arrancar (Spring crea lo que falte en el broker):
 *
 *  KAFKA     tópico pedidos.eventos (3 particiones)
 *
 *  RABBITMQ  ex.pedidos (topic)    pedido.pagado   -> q.facturacion
 *                                  pedido.creado   -> q.inventario
 *                                  pedido.cancelado-> q.inventario
 *                                  pedido.enviado  -> q.logistica
 *            ex.broadcast (fanout) -> cola anónima de cada instancia del servidor -> WebSocket
 */
@Configuration
public class MensajeriaConfig {

    public static final String EX_PEDIDOS = "ex.pedidos";
    public static final String EX_BROADCAST = "ex.broadcast";
    public static final String Q_FACTURACION = "q.facturacion";
    public static final String Q_INVENTARIO = "q.inventario";
    public static final String Q_LOGISTICA = "q.logistica";

    // ---------- Kafka ----------
    @Bean
    public NewTopic topicoPedidos(@Value("${app.kafka.topic}") String nombre) {
        return TopicBuilder.name(nombre).partitions(3).replicas(1).build();
    }

    /**
     * Si el proyector falla (por ejemplo, la base de datos está caída) se reintenta cada 2 segundos SIN
     * saltarse el mensaje, para no perder eventos ni alterar su orden.
     */
    @Bean
    public DefaultErrorHandler manejadorErroresKafka() {
        return new DefaultErrorHandler(new FixedBackOff(2000L, FixedBackOff.UNLIMITED_ATTEMPTS));
    }

    // ---------- RabbitMQ ----------
    @Bean
    public MessageConverter conversorJson(ObjectMapper mapper) {
        return new Jackson2JsonMessageConverter(mapper);
    }

    @Bean
    public TopicExchange exPedidos() {
        return new TopicExchange(EX_PEDIDOS, true, false);
    }

    @Bean
    public FanoutExchange exBroadcast() {
        return new FanoutExchange(EX_BROADCAST, true, false);
    }

    @Bean
    public Queue qFacturacion() {
        return QueueBuilder.durable(Q_FACTURACION).build();
    }

    @Bean
    public Queue qInventario() {
        return QueueBuilder.durable(Q_INVENTARIO).build();
    }

    @Bean
    public Queue qLogistica() {
        return QueueBuilder.durable(Q_LOGISTICA).build();
    }

    /** Cola temporal propia de ESTA instancia: así cada servidor recibe una copia del broadcast. */
    @Bean
    public Queue broadcastQueue() {
        // Nombre único por instancia, no exclusiva: funciona igual en RabbitMQ 3.x y 4.x.
        // autoDelete: se borra al desconectarse el servidor; x-expires: limpieza si quedó huérfana (1 h).
        return QueueBuilder.durable("q.broadcast." + java.util.UUID.randomUUID())
                .autoDelete()
                .withArgument("x-expires", 3_600_000)
                .build();
    }

    @Bean
    public Declarables bindings(TopicExchange exPedidos, FanoutExchange exBroadcast,
                                Queue qFacturacion, Queue qInventario, Queue qLogistica, Queue broadcastQueue) {
        return new Declarables(
                BindingBuilder.bind(qFacturacion).to(exPedidos).with("pedido.pagado"),
                BindingBuilder.bind(qInventario).to(exPedidos).with("pedido.creado"),
                BindingBuilder.bind(qInventario).to(exPedidos).with("pedido.cancelado"),
                BindingBuilder.bind(qLogistica).to(exPedidos).with("pedido.enviado"),
                BindingBuilder.bind(broadcastQueue).to(exBroadcast));
    }
}
