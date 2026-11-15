package co.vinni.tienda.consulta;

import co.vinni.tienda.comando.EstadoPedido;

import java.time.Instant;
import java.util.*;

/** Versión en memoria del almacén de lectura, solo para pruebas (así no hace falta MongoDB). */
class AlmacenEnMemoria implements PedidoVistaRepositorio {

    private final Map<String, PedidoVista> vistas = new LinkedHashMap<>();
    private final Set<String> aplicados = new HashSet<>();

    @Override
    public List<PedidoVista> listar(String estado, String cliente) {
        return vistas.values().stream()
                .filter(v -> estado == null || estado.isBlank() || v.estado().equalsIgnoreCase(estado))
                .filter(v -> cliente == null || cliente.isBlank() || v.cliente().equalsIgnoreCase(cliente.trim()))
                .sorted(Comparator.comparing(PedidoVista::creadoEn).reversed())
                .toList();
    }

    @Override
    public Optional<PedidoVista> buscar(String id) {
        return Optional.ofNullable(vistas.get(id));
    }

    @Override
    public Map<String, Long> resumen() {
        Map<String, Long> r = new LinkedHashMap<>();
        for (EstadoPedido e : EstadoPedido.values()) {
            r.put(e.name(), vistas.values().stream().filter(v -> v.estado().equals(e.name())).count());
        }
        return r;
    }

    @Override
    public boolean eventoYaAplicado(String eventId) {
        return aplicados.contains(eventId);
    }

    @Override
    public void marcarEventoAplicado(String eventId) {
        aplicados.add(eventId);
    }

    @Override
    public void insertarSiNoExiste(PedidoVista vista) {
        vistas.putIfAbsent(vista.id(), vista);
    }

    @Override
    public boolean actualizarEstado(String id, String estado, Instant cuando) {
        PedidoVista v = vistas.get(id);
        if (v == null) return false;
        vistas.put(id, new PedidoVista(v.id(), v.cliente(), v.total(), estado, v.items(), v.creadoEn(), cuando));
        return true;
    }
}
