package co.vinni.tienda.traza;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** El log del stack guarda las líneas en orden y solo conserva las últimas 300. */
class TrazaServicioTest {

    @Test
    void guardaLasLineasEnOrdenConSecuenciaCreciente() {
        TrazaServicio traza = TrazaServicio.vacio();
        traza.registrar(TrazaServicio.SPRING, "Comando recibido", "POST", "p1");
        traza.registrar(TrazaServicio.KAFKA, "Evento publicado", "topico", "p1");

        List<TrazaEvento> lineas = traza.recientes();
        assertEquals(2, lineas.size());
        assertEquals(TrazaServicio.SPRING, lineas.get(0).herramienta());
        assertEquals(TrazaServicio.KAFKA, lineas.get(1).herramienta());
        assertTrue(lineas.get(0).seq() < lineas.get(1).seq());
    }

    @Test
    void soloConservaLasUltimas300() {
        TrazaServicio traza = TrazaServicio.vacio();
        for (int i = 0; i < 350; i++) {
            traza.registrar(TrazaServicio.MONGODB, "accion " + i, "detalle", null);
        }
        List<TrazaEvento> lineas = traza.recientes();
        assertEquals(300, lineas.size());
        assertEquals("accion 50", lineas.get(0).accion());
        assertEquals("accion 349", lineas.get(299).accion());
    }
}
