package co.vinni.tienda.consulta;

import co.vinni.tienda.evento.PedidoEvento;
import co.vinni.tienda.mensajeria.PublicadorRabbit;
import co.vinni.tienda.traza.TrazaServicio;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor de KAFKA. Une las dos mitades de CQRS:
 *   evento (Kafka) -> actualiza el modelo de lectura -> avisa por RabbitMQ (notificaciones + broadcast).
 */
@Component
public class ProyectorKafka {

    private static final Logger log = LoggerFactory.getLogger(ProyectorKafka.class);

    private final ProyeccionServicio proyeccion;
    private final PublicadorRabbit rabbit;
    private final ObjectMapper mapper;
    private final TrazaServicio traza;

    public ProyectorKafka(ProyeccionServicio proyeccion, PublicadorRabbit rabbit, ObjectMapper mapper,
                          TrazaServicio traza) {
        this.proyeccion = proyeccion;
        this.rabbit = rabbit;
        this.mapper = mapper;
        this.traza = traza;
    }

    @KafkaListener(topics = "${app.kafka.topic}", groupId = "proyector-pedidos")
    public void alRecibir(String json) {
        PedidoEvento evento;
        try {
            evento = mapper.readValue(json, PedidoEvento.class);
        } catch (JsonProcessingException e) {
            log.error("[KAFKA] mensaje inválido descartado: {}", json);
            return;
        }
        log.info("[KAFKA] recibido {} del pedido {}", evento.tipo(), evento.pedidoId());
        traza.registrar(TrazaServicio.KAFKA, "Evento recibido por el proyector",
                evento.tipo() + " ← tópico pedidos.eventos (grupo proyector-pedidos)", evento.pedidoId());
        if (proyeccion.aplicar(evento)) {
            rabbit.publicar(evento);
        }
    }
}
