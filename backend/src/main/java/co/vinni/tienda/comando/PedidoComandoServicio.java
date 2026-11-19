package co.vinni.tienda.comando;

import co.vinni.tienda.evento.ItemEvento;
import co.vinni.tienda.evento.PedidoEvento;
import co.vinni.tienda.traza.TrazaServicio;
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
    private final TrazaServicio traza;

    public PedidoComandoServicio(PedidoRepositorio pedidos, OutboxRepositorio outbox, ObjectMapper mapper,
                                 TrazaServicio traza) {
        this.pedidos = pedidos;
        this.outbox = outbox;
        this.mapper = mapper;
        this.traza = traza;
    }

    @Transactional
    public String crear(CrearPedidoCmd cmd) {
        traza.registrar(TrazaServicio.SPRING, "Comando recibido: crear pedido",
                "POST /api/comandos/pedidos (lado de COMANDOS)", null);
        List<PedidoItem> items = cmd.items().stream()
                .map(i -> new PedidoItem(i.producto(), i.cantidad(), i.precioUnitario()))
                .toList();
        Pedido pedido = pedidos.save(Pedido.crear(cmd.cliente().trim(), items));
        traza.registrarTrasCommit(TrazaServicio.POSTGRES, "Pedido guardado",
                "INSERT comando.pedido + comando.pedido_item (transacción confirmada)", pedido.getId());
        registrarEvento(pedido, PedidoEvento.CREADO);
        return pedido.getId();
    }

    @Transactional
    public EstadoPedido pagar(String id) {
        traza.registrar(TrazaServicio.SPRING, "Comando recibido: pagar pedido",
                "POST /api/comandos/pedidos/{id}/pagar", id);
        Pedido p = buscar(id);
        p.pagar();
        trazaCambioDeEstado(p);
        registrarEvento(p, PedidoEvento.PAGADO);
        return p.getEstado();
    }

    @Transactional
    public EstadoPedido enviar(String id) {
        traza.registrar(TrazaServicio.SPRING, "Comando recibido: enviar pedido",
                "POST /api/comandos/pedidos/{id}/enviar", id);
        Pedido p = buscar(id);
        p.enviar();
        trazaCambioDeEstado(p);
        registrarEvento(p, PedidoEvento.ENVIADO);
        return p.getEstado();
    }

    @Transactional
    public EstadoPedido cancelar(String id) {
        traza.registrar(TrazaServicio.SPRING, "Comando recibido: cancelar pedido",
                "POST /api/comandos/pedidos/{id}/cancelar", id);
        Pedido p = buscar(id);
        p.cancelar();
        trazaCambioDeEstado(p);
        registrarEvento(p, PedidoEvento.CANCELADO);
        return p.getEstado();
    }

    private void trazaCambioDeEstado(Pedido p) {
        traza.registrarTrasCommit(TrazaServicio.POSTGRES, "Pedido actualizado",
                "UPDATE comando.pedido SET estado = '" + p.getEstado().name() + "' (transacción confirmada)", p.getId());
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
            traza.registrarTrasCommit(TrazaServicio.POSTGRES, "Evento guardado en el outbox",
                    tipo + " → comando.outbox_evento (misma transacción que el pedido)", p.getId());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el evento " + tipo, e);
        }
    }
}
