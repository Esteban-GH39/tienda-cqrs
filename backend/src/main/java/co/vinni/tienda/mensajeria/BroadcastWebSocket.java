package co.vinni.tienda.mensajeria;

import co.vinni.tienda.consulta.NotificacionPedido;
import co.vinni.tienda.consulta.PedidoVistaRepositorio;
import co.vinni.tienda.evento.PedidoEvento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * BROADCAST: recibe de RabbitMQ (ex.broadcast, fanout) y lo empuja a TODOS los clientes conectados
 * por WebSocket en /topic/pedidos. Ningún cliente tiene que preguntar (sin polling).
 */
@Component
public class BroadcastWebSocket {

    private static final Logger log = LoggerFactory.getLogger(BroadcastWebSocket.class);
    public static final String DESTINO = "/topic/pedidos";

    private final SimpMessagingTemplate ws;
    private final PedidoVistaRepositorio vistas;

    public BroadcastWebSocket(SimpMessagingTemplate ws, PedidoVistaRepositorio vistas) {
        this.ws = ws;
        this.vistas = vistas;
    }

    @RabbitListener(queues = "#{broadcastQueue.name}")
    public void alRecibir(PedidoEvento e) {
        NotificacionPedido n = new NotificacionPedido(e.tipo(), e.eventId(), e.ocurridoEn(),
                vistas.buscar(e.pedidoId()).orElse(null), vistas.resumen());
        ws.convertAndSend(DESTINO, n);
        log.info("[WEBSOCKET] broadcast de {} a todos los clientes en {}", e.tipo(), DESTINO);
    }
}
