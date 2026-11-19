package co.vinni.tienda.consulta;

import co.vinni.tienda.evento.PedidoEvento;
import co.vinni.tienda.traza.TrazaServicio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * PROYECTOR: convierte cada evento en un documento del modelo de lectura (MongoDB).
 * Es IDEMPOTENTE: si Kafka entrega dos veces el mismo evento (reintento, reinicio), la segunda se ignora
 * gracias a la colección "evento_aplicado". Cada paso se puede repetir sin dañar nada, porque MongoDB
 * (en una sola instancia) no ofrece transacciones entre documentos y no se depende de ellas.
 */
@Service
public class ProyeccionServicio {

    private static final Logger log = LoggerFactory.getLogger(ProyeccionServicio.class);

    private final PedidoVistaRepositorio vistas;
    private final TrazaServicio traza;

    public ProyeccionServicio(PedidoVistaRepositorio vistas, TrazaServicio traza) {
        this.vistas = vistas;
        this.traza = traza;
    }

    /** @return true si el evento era nuevo y se aplicó; false si ya estaba aplicado. */
    public boolean aplicar(PedidoEvento e) {
        if (vistas.eventoYaAplicado(e.eventId())) {
            log.info("[PROYECTOR] evento {} ya aplicado, se ignora", e.eventId());
            traza.registrar(TrazaServicio.MONGODB, "Evento repetido, se ignora",
                    e.tipo() + " ya estaba en evento_aplicado (idempotencia)", e.pedidoId());
            return false;
        }

        if (PedidoEvento.CREADO.equals(e.tipo())) {
            vistas.insertarSiNoExiste(new PedidoVista(e.pedidoId(), e.cliente(), e.total(), e.estado(),
                    e.items(), e.ocurridoEn(), e.ocurridoEn()));
        } else if (!vistas.actualizarEstado(e.pedidoId(), e.estado(), e.ocurridoEn())) {
            log.warn("[PROYECTOR] llegó {} del pedido {} pero no existe en la vista", e.tipo(), e.pedidoId());
        }

        vistas.marcarEventoAplicado(e.eventId());
        log.info("[PROYECTOR] aplicado {} -> pedido {} ahora {} (MongoDB)", e.tipo(), e.pedidoId(), e.estado());
        traza.registrar(TrazaServicio.MONGODB, "Vista actualizada",
                (PedidoEvento.CREADO.equals(e.tipo()) ? "insert en pedido_vista" : "update de pedido_vista")
                        + " (estado " + e.estado() + ") + registro en evento_aplicado", e.pedidoId());
        return true;
    }
}
