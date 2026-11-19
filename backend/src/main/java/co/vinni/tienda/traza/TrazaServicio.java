package co.vinni.tienda.traza;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LOG DEL STACK EN TIEMPO REAL. Cada pieza del sistema llama a registrar(...) justo cuando usa su herramienta
 * (guardar en PostgreSQL, publicar en Kafka, escribir en MongoDB, enrutar en RabbitMQ, empujar por WebSocket).
 * La línea se guarda en memoria (para quien abra la pestaña "Log" después) y se emite por WebSocket
 * a /topic/traza (para quien ya la tiene abierta). Nunca rompe el negocio: si algo falla aquí, se ignora.
 */
@Service
public class TrazaServicio {

    public static final String SPRING = "SPRING";
    public static final String POSTGRES = "POSTGRES";
    public static final String KAFKA = "KAFKA";
    public static final String MONGODB = "MONGODB";
    public static final String RABBITMQ = "RABBITMQ";
    public static final String WEBSOCKET = "WEBSOCKET";

    public static final String DESTINO = "/topic/traza";
    private static final int MAXIMO = 300;
    private static final Logger log = LoggerFactory.getLogger(TrazaServicio.class);

    private final ObjectProvider<SimpMessagingTemplate> ws;
    private final Deque<TrazaEvento> historial = new ArrayDeque<>();
    private final Map<String, Long> ultimaVez = new ConcurrentHashMap<>();
    private long secuencia = 0;

    public TrazaServicio(ObjectProvider<SimpMessagingTemplate> ws) {
        this.ws = ws;
    }

    /** Para pruebas unitarias: guarda el historial pero no emite por WebSocket. */
    public static TrazaServicio vacio() {
        return new TrazaServicio(null);
    }

    /** Registra YA. Úsalo cuando la herramienta ya se usó (publicar, consumir, escribir en Mongo...). */
    public void registrar(String herramienta, String accion, String detalle, String pedidoId) {
        emitir(herramienta, accion, detalle, pedidoId, Instant.now());
    }

    /**
     * Igual que registrar, pero como mucho una vez cada "minimoMs" por clave. Sirve para las lecturas, que los
     * clientes repiten muchas veces seguidas, y así el log no se llena de líneas iguales.
     */
    public void registrarLimitado(String clave, long minimoMs, String herramienta, String accion, String detalle,
                                  String pedidoId) {
        long ahora = System.currentTimeMillis();
        Long anterior = ultimaVez.get(clave);
        if (anterior != null && ahora - anterior < minimoMs) {
            return;
        }
        ultimaVez.put(clave, ahora);
        registrar(herramienta, accion, detalle, pedidoId);
    }

    /**
     * Registra cuando la transacción de PostgreSQL SE CONFIRMA (commit). Así el log nunca muestra un
     * "guardado" que luego se revirtió. Fuera de una transacción registra de inmediato.
     */
    public void registrarTrasCommit(String herramienta, String accion, String detalle, String pedidoId) {
        Instant ahora = Instant.now();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    emitir(herramienta, accion, detalle, pedidoId, ahora);
                }
            });
        } else {
            emitir(herramienta, accion, detalle, pedidoId, ahora);
        }
    }

    /** Últimas líneas, de la más antigua a la más nueva. */
    public synchronized List<TrazaEvento> recientes() {
        return new ArrayList<>(historial);
    }

    private synchronized void emitir(String herramienta, String accion, String detalle, String pedidoId, Instant ts) {
        TrazaEvento linea = new TrazaEvento(++secuencia, ts, herramienta, accion, detalle, pedidoId);
        historial.addLast(linea);
        while (historial.size() > MAXIMO) {
            historial.removeFirst();
        }
        try {
            SimpMessagingTemplate plantilla = ws == null ? null : ws.getIfAvailable();
            if (plantilla != null) {
                plantilla.convertAndSend(DESTINO, linea);
            }
        } catch (Exception e) {
            log.debug("[TRAZA] no se pudo emitir: {}", e.getMessage());
        }
    }
}
