package co.vinni.tienda.consulta;

import co.vinni.tienda.evento.ItemEvento;
import co.vinni.tienda.evento.PedidoEvento;
import co.vinni.tienda.traza.TrazaServicio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Prueba el proyector con un almacén en memoria (no necesita MongoDB, Kafka ni Spring). */
class ProyeccionServicioTest {

    private AlmacenEnMemoria vistas;
    private ProyeccionServicio proyeccion;

    @BeforeEach
    void preparar() {
        vistas = new AlmacenEnMemoria();
        proyeccion = new ProyeccionServicio(vistas, TrazaServicio.vacio());
    }

    private PedidoEvento evento(String eventId, String tipo, String pedidoId, String estado) {
        return new PedidoEvento(eventId, tipo, pedidoId, "Ana", new BigDecimal("100.00"), estado,
                List.of(new ItemEvento("Mouse", 2, new BigDecimal("50.00"))), Instant.now());
    }

    @Test
    void creaLaVistaYLaActualizaConCadaEvento() {
        assertTrue(proyeccion.aplicar(evento("e1", PedidoEvento.CREADO, "p1", "CREADO")));
        assertEquals("CREADO", vistas.buscar("p1").orElseThrow().estado());
        assertEquals("Mouse", vistas.buscar("p1").orElseThrow().items().get(0).producto());

        assertTrue(proyeccion.aplicar(evento("e2", PedidoEvento.PAGADO, "p1", "PAGADO")));
        assertEquals("PAGADO", vistas.buscar("p1").orElseThrow().estado());
    }

    @Test
    void unEventoRepetidoSeIgnora() {
        assertTrue(proyeccion.aplicar(evento("e10", PedidoEvento.CREADO, "p2", "CREADO")));
        assertFalse(proyeccion.aplicar(evento("e10", PedidoEvento.CREADO, "p2", "CREADO")));
        assertEquals(1, vistas.listar(null, "Ana").size());
    }

    @Test
    void reprocesarElCreadoNoPisaUnEstadoMasNuevo() {
        proyeccion.aplicar(evento("e30", PedidoEvento.CREADO, "p5", "CREADO"));
        proyeccion.aplicar(evento("e31", PedidoEvento.PAGADO, "p5", "PAGADO"));
        // llega otra vez un PEDIDO_CREADO con id de evento distinto (reproceso): no debe volver a CREADO
        proyeccion.aplicar(evento("e32", PedidoEvento.CREADO, "p5", "CREADO"));
        assertEquals("PAGADO", vistas.buscar("p5").orElseThrow().estado());
    }

    @Test
    void resumenYFiltros() {
        proyeccion.aplicar(evento("e20", PedidoEvento.CREADO, "p3", "CREADO"));
        proyeccion.aplicar(evento("e21", PedidoEvento.CREADO, "p4", "CREADO"));
        proyeccion.aplicar(evento("e22", PedidoEvento.PAGADO, "p4", "PAGADO"));

        assertEquals(1L, vistas.resumen().get("PAGADO"));
        assertEquals(1L, vistas.resumen().get("CREADO"));
        assertEquals(0L, vistas.resumen().get("ENVIADO"));
        assertEquals(1, vistas.listar("PAGADO", null).size());
        assertEquals(0, vistas.listar(null, "otra persona").size());
    }
}
