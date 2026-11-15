package co.vinni.tienda.consulta;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Almacén del modelo de LECTURA. La implementación real usa MongoDB (PedidoVistaMongo);
 * en las pruebas se usa una versión en memoria.
 */
public interface PedidoVistaRepositorio {

    // ---------- consultas ----------
    List<PedidoVista> listar(String estado, String cliente);

    Optional<PedidoVista> buscar(String id);

    /** Cantidad de pedidos por estado (siempre trae los 4 estados, aunque estén en cero). */
    Map<String, Long> resumen();

    // ---------- operaciones del proyector ----------
    boolean eventoYaAplicado(String eventId);

    void marcarEventoAplicado(String eventId);

    /** Guarda el pedido solo si no existía (así reprocesar PEDIDO_CREADO no pisa un estado más nuevo). */
    void insertarSiNoExiste(PedidoVista vista);

    /** @return true si el pedido existía y se actualizó. */
    boolean actualizarEstado(String id, String estado, Instant cuando);
}
