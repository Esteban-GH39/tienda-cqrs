package co.vinni.tienda.comando;

import co.vinni.tienda.traza.TrazaServicio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lee los eventos pendientes del outbox y los publica en Kafka, EN ORDEN.
 * La clave del mensaje es el id del pedido: Kafka manda todos los eventos de un pedido a la misma
 * partición, así que el lado de consultas los recibe en el orden en que ocurrieron.
 * Si Kafka está caído, el evento queda pendiente y se reintenta en la siguiente vuelta (no se pierde).
 */
@Component
public class OutboxPublicador {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublicador.class);

    private final OutboxRepositorio outbox;
    private final KafkaTemplate<String, String> kafka;
    private final String topico;
    private final TrazaServicio traza;
    private final AtomicBoolean kafkaCaido = new AtomicBoolean(false);

    public OutboxPublicador(OutboxRepositorio outbox, KafkaTemplate<String, String> kafka,
                            @Value("${app.kafka.topic}") String topico, TrazaServicio traza) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.topico = topico;
        this.traza = traza;
    }

    @Scheduled(fixedDelayString = "${app.outbox.intervalo-ms:1000}")
    @Transactional
    public void publicarPendientes() {
        List<OutboxEvento> pendientes = outbox.findTop100ByPublicadoFalseOrderByIdAsc();
        for (OutboxEvento e : pendientes) {
            try {
                SendResult<String, String> r = kafka.send(topico, e.getClave(), e.getPayload()).get(10, TimeUnit.SECONDS);
                e.marcarPublicado();
                if (kafkaCaido.getAndSet(false)) {
                    traza.registrar(TrazaServicio.KAFKA, "Kafka volvió a responder", "Se reanuda la publicación del outbox", null);
                }
                String donde = (r != null && r.getRecordMetadata() != null)
                        ? " · partición " + r.getRecordMetadata().partition() + ", offset " + r.getRecordMetadata().offset() : "";
                traza.registrar(TrazaServicio.KAFKA, "Evento publicado en Kafka",
                        e.getTipo() + " → tópico " + topico + " (clave = id del pedido)" + donde, e.getClave());
                log.info("[KAFKA] publicado {} del pedido {}", e.getTipo(), e.getClave());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                if (!kafkaCaido.getAndSet(true)) {
                    traza.registrar(TrazaServicio.KAFKA, "Kafka no responde",
                            "El evento queda pendiente en el outbox y se reintenta cada segundo (no se pierde)", e.getClave());
                }
                log.warn("[KAFKA] no se pudo publicar {} ({}). Se reintenta luego.", e.getTipo(), ex.getMessage());
                return; // se detiene para no alterar el orden
            }
        }
    }
}
