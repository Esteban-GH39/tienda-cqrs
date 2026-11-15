package co.vinni.tienda.comando;

import co.vinni.tienda.evento.PedidoEvento;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PedidoComandoServicio.class, JacksonAutoConfiguration.class})
class PedidoComandoServicioTest {

    @Autowired PedidoComandoServicio servicio;
    @Autowired PedidoRepositorio pedidos;
    @Autowired OutboxRepositorio outbox;
    @Autowired ObjectMapper mapper;

    private CrearPedidoCmd comando() {
        return new CrearPedidoCmd("Ana", List.of(
                new CrearPedidoCmd.ItemCmd("Laptop", 1, new BigDecimal("2500.00")),
                new CrearPedidoCmd.ItemCmd("Mouse", 2, new BigDecimal("30.50"))));
    }

    @Test
    void crearGuardaElPedidoYSuEventoEnElOutbox() throws Exception {
        String id = servicio.crear(comando());

        Pedido p = pedidos.findById(id).orElseThrow();
        assertEquals(EstadoPedido.CREADO, p.getEstado());
        assertEquals(0, new BigDecimal("2561.00").compareTo(p.getTotal()), "total = 2500 + 2*30.50");

        List<OutboxEvento> pendientes = outbox.findTop100ByPublicadoFalseOrderByIdAsc();
        assertEquals(1, pendientes.size());
        PedidoEvento ev = mapper.readValue(pendientes.get(0).getPayload(), PedidoEvento.class);
        assertEquals(PedidoEvento.CREADO, ev.tipo());
        assertEquals(id, ev.pedidoId());
        assertEquals("pedido.creado", ev.routingKey());
        assertEquals(2, ev.items().size());
    }

    @Test
    void elFlujoCompletoGeneraUnEventoPorCadaCambio() {
        String id = servicio.crear(comando());
        servicio.pagar(id);
        servicio.enviar(id);

        List<String> tipos = outbox.findTop100ByPublicadoFalseOrderByIdAsc().stream().map(OutboxEvento::getTipo).toList();
        assertEquals(List.of("PEDIDO_CREADO", "PEDIDO_PAGADO", "PEDIDO_ENVIADO"), tipos);
        assertEquals(EstadoPedido.ENVIADO, pedidos.findById(id).orElseThrow().getEstado());
    }

    @Test
    void noSePuedeEnviarSinPagar() {
        String id = servicio.crear(comando());
        assertThrows(ReglaNegocioException.class, () -> servicio.enviar(id));
    }

    @Test
    void noSePuedeCancelarUnPedidoEnviado() {
        String id = servicio.crear(comando());
        servicio.pagar(id);
        servicio.enviar(id);
        assertThrows(ReglaNegocioException.class, () -> servicio.cancelar(id));
    }

    @Test
    void pedidoInexistente() {
        assertThrows(PedidoNoEncontradoException.class, () -> servicio.pagar("no-existe"));
    }
}
