package co.vinni.tienda.comando;

import co.vinni.tienda.evento.ItemEvento;
import co.vinni.tienda.evento.PedidoEvento;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Casos de uso de ESCRITURA. Cada comando, en UNA sola transacción:
 *   1) cambia el pedido en comando.pedido
 *   2) guarda el evento en comando.outbox_evento
 * Así nunca queda un pedido sin evento ni un evento sin pedido. Kafka se publica después (OutboxPublicador).
 */
@Service
public class PedidoComandoServicio {

    private final PedidoRepositorio pedidos;
    private final OutboxRepositorio outbox;
    private final ObjectMapper mapper;

    public PedidoComandoServicio(PedidoRepositorio pedidos, OutboxRepositorio outbox, ObjectMapper mapper) {
        this.pedidos = pedidos;
        this.outbox = outbox;
        this.mapper = mapper;
    }

    @Transactional
    public String crear(CrearPedidoCmd cmd) {
        List<PedidoItem> items = cmd.items().stream()
                .map(i -> new PedidoItem(i.producto(), i.cantidad(), i.precioUnitario()))
                .toList();
        Pedido pedido = pedidos.save(Pedido.crear(cmd.cliente().trim(), items));
        registrarEvento(pedido, PedidoEvento.CREADO);
        return pedido.getId();
    }

    @Transactional
    public EstadoPedido pagar(String id) {
        Pedido p = buscar(id);
        p.pagar();
        registrarEvento(p, PedidoEvento.PAGADO);
        return p.getEstado();
    }

    @Transactional
    public EstadoPedido enviar(String id) {
        Pedido p = buscar(id);
        p.enviar();
        registrarEvento(p, PedidoEvento.ENVIADO);
        return p.getEstado();
    }

    @Transactional
    public EstadoPedido cancelar(String id) {
        Pedido p = buscar(id);
        p.cancelar();
        registrarEvento(p, PedidoEvento.CANCELADO);
        return p.getEstado();
    }

    private Pedido buscar(String id) {
        return pedidos.findById(id).orElseThrow(() -> new PedidoNoEncontradoException(id));
    }

    private void registrarEvento(Pedido p, String tipo) {
        PedidoEvento evento = new PedidoEvento(
                UUID.randomUUID().toString(), tipo, p.getId(), p.getCliente(), p.getTotal(),
                p.getEstado().name(),
                p.getItems().stream().map(i -> new ItemEvento(i.getProducto(), i.getCantidad(), i.getPrecioUnitario())).toList(),
                Instant.now());
        try {
            outbox.save(new OutboxEvento(evento.eventId(), p.getId(), tipo, mapper.writeValueAsString(evento)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el evento " + tipo, e);
        }
    }
}
